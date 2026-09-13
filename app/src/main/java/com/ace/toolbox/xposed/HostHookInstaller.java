package com.ace.toolbox.xposed;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

final class HostHookInstaller {
    private static final String TAG = "ACE-Hook";
    private static final Set<String> INSTALLED = Collections.synchronizedSet(new HashSet<>());

    static void installEarly(XposedModule module, String pkg) {
        HostSettingInjector.setLogger(module);
        installActivityFallback(module, pkg);
        if (HostPackages.WECHAT.equals(pkg)) {
            installWechatInstrumentationWatcher(module);
        }
    }

    static void install(XposedModule module, XposedModule.PackageReadyParam param) {
        String pkg = param.getPackageName();
        ClassLoader loader = param.getClassLoader();
        HostSettingInjector.setLogger(module);

        if (HostPackages.QQ.equals(pkg)) {
            QqGroupMenuHook.install(module, loader);
            QqShareDiagnostics.install(module, loader);
            QqCompatibilityProbe.run(module, loader);
            QqSettingsProviderInjector.install(module, loader);
            QqScriptEventHookInstaller.install(module, loader);
        }
        try {
            String appClassName = param.getApplicationInfo().className;
            Class<?> appClass = appClassName == null || appClassName.isEmpty()
                    ? Application.class : Class.forName(appClassName, false, loader);
            Method onCreate = ReflectionUtils.findMethod(appClass, "onCreate");
            if (onCreate == null) onCreate = Application.class.getDeclaredMethod("onCreate");
            onCreate.setAccessible(true);
            module.hook(onCreate)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object self = chain.getThisObject();
                        if (self instanceof Application) {
                            Application app = (Application) self;
                            installForApplication(module, app, pkg, loader);
                        }
                        return result;
                    });
        } catch (Throwable t) {
            Log.e(TAG, "Unable to install Application bootstrap for " + pkg, t);
            HookRule rule = RuleRepository.loadBundled(pkg);
            if (rule != null) installSettingHooks(module, pkg, loader, rule);
            installActivityFallback(module, pkg);
        }
    }

    private static void installForApplication(XposedModule module, Application app, String pkg, ClassLoader loader) {
        String key = pkg + "@" + System.identityHashCode(loader);
        if (!INSTALLED.add(key)) return;

        HookRule rule = RuleRepository.resolveCachedOrBundled(app, pkg);
        if (rule != null) installSettingHooks(module, pkg, loader, rule);

        installActivityFallback(module, pkg);

        if (HostPackages.QQ.equals(pkg)) {
            SsaidFeature.install(module, app, loader);
        }

        new Thread(() -> {
            HookRule remote = RuleRepository.fetchRemote(app, pkg);
            if (remote != null) installSettingHooks(module, pkg, loader, remote);
        }, "ACE-rule-refresh").start();
    }

    private static void installSettingHooks(XposedModule module, String pkg, ClassLoader loader, HookRule rule) {
        for (String className : rule.settingClasses) {
            String hookKey = pkg + ":settings:" + className + ":" + rule.methodName;
            if (!INSTALLED.add(hookKey)) continue;
            try {
                Class<?> cls = Class.forName(className, false, loader);
                Method method = ReflectionUtils.findMethod(cls, rule.methodName, Bundle.class);
                if (method == null) {
                    Log.w(TAG, "No " + rule.methodName + "(Bundle): " + className);
                    continue;
                }
                method.setAccessible(true);
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object self = chain.getThisObject();
                            if (self instanceof Activity) {
                                Activity activity = (Activity) self;
                                HostSettingInjector.scheduleMaybeInject(activity, pkg);
                            }
                            return result;
                        });
                Log.i(TAG, "Installed settings hook: " + className);
            } catch (Throwable t) {
                Log.w(TAG, "Setting candidate unavailable: " + className + " / " + t.getMessage());
            }
        }
    }

    private static void installWechatInstrumentationWatcher(XposedModule module) {
        String key = HostPackages.WECHAT + ":instrumentation-resume";
        if (!INSTALLED.add(key)) return;

        try {
            Method resume = Instrumentation.class.getDeclaredMethod(
                    "callActivityOnResume", Activity.class);
            resume.setAccessible(true);

            module.hook(resume)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object arg = chain.getArg(0);
                        if (arg instanceof Activity) {
                            Activity activity = (Activity) arg;
                            if (HostPackages.WECHAT.equals(activity.getPackageName())) {
                                module.log(Log.INFO, TAG,
                                        "WeChat Instrumentation resume: "
                                                + activity.getClass().getName());
                                HostSettingInjector.scheduleMaybeInject(
                                        activity, HostPackages.WECHAT);
                            }
                        }
                        return result;
                    });

            module.log(Log.INFO, TAG,
                    "Instrumentation.callActivityOnResume watcher installed for WeChat");
        } catch (Throwable t) {
            module.log(Log.ERROR, TAG,
                    "Instrumentation.callActivityOnResume watcher failed for WeChat", t);
        }
    }

    private static void installActivityFallback(XposedModule module, String pkg) {
        String key = pkg + ":activity-resume";
        if (!INSTALLED.add(key)) return;
        try {
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            onResume.setAccessible(true);
            module.hook(onResume)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object self = chain.getThisObject();
                        if (self instanceof Activity) {
                            Activity activity = (Activity) self;
                            if (HostPackages.QQ.equals(pkg)) {
                                module.log(Log.INFO, TAG,
                                        "Activity resumed: " + activity.getClass().getName()
                                                + "; nativeEntry="
                                                + QqSettingsProviderInjector.nativeEntryInjected());
                            } else if (HostPackages.WECHAT.equals(pkg)) {
                                module.log(Log.INFO, TAG,
                                        "WeChat Activity resumed: " + activity.getClass().getName());
                            }
                            if (HostPackages.QQ.equals(pkg)) {
                                QqMusicFeature.INSTANCE.resume(activity);
                                HostJavaScriptEngine.attachActivity(activity);
                                HostJavaScriptEngine.maybeAutoRun(activity);
                            }
                            if (!HostPackages.QQ.equals(pkg)
                                    || !QqSettingsProviderInjector.nativeEntryInjected()) {
                                HostSettingInjector.scheduleMaybeInject(activity, pkg);
                            }
                        }
                        return result;
                    });
            Log.i(TAG, "Installed compatibility Activity lifecycle watcher for " + pkg);
            module.log(Log.INFO, TAG, "Activity.onResume watcher installed for " + pkg);
        } catch (Throwable t) {
            Log.w(TAG, "Unable to install lifecycle fallback for " + pkg + ": " + t.getMessage());
            module.log(Log.ERROR, TAG, "Activity.onResume watcher failed for " + pkg, t);
        }
    }

    private HostHookInstaller() {}
}
