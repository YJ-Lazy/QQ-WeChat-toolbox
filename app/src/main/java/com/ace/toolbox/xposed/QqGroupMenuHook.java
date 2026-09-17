package com.ace.toolbox.xposed;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import android.view.View;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

final class QqGroupMenuHook {
    private static final WeakHashMap<View, Binding> bindings = new WeakHashMap<>();
    private static boolean installed;
    static synchronized void install(XposedModule module, ClassLoader loader) {
        if (installed) return;
        try {
            Class<?> vb = Class.forName("com.tencent.mobileqq.aio.title.right1.Right1VB", false, loader);
            Method icon = vb.getDeclaredMethod("L1"); icon.setAccessible(true);
            Method context = vb.getMethod("getMAIOContext$sdk_debug");
            Class<?> state = Class.forName("com.tencent.mvi.base.mvi.MviUIState", false, loader);
            for (Method m : new Method[]{vb.getDeclaredMethod("bindViewAndData"), vb.getDeclaredMethod("handleUIState", state)}) {
                module.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        Object controller = chain.getThisObject();
                        View view = (View) icon.invoke(controller);
                        if (Looper.myLooper() == Looper.getMainLooper() && view != null) {
                            Binding old = bindings.get(view);
                            if (old == null || old.owner.get() != controller) {
                                if (old != null) view.setLongClickable(old.original);
                                bindings.put(view, new Binding(controller, context, view.isLongClickable()));
                            }
                            Binding binding = bindings.get(view);
                            view.setLongClickable(group(binding) != null || binding.original);
                        }
                    } catch (Throwable e) { module.log(Log.WARN, "ACE-GroupMenu", "Bind failed", e); }
                    return result;
                });
            }
            module.hook(vb.getMethod("onDestroy")).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                if (vb.isInstance(chain.getThisObject())) {
                    try {
                        View view = (View) icon.invoke(chain.getThisObject());
                        Binding b = bindings.get(view);
                        if (b != null && b.owner.get() == chain.getThisObject()) {
                            bindings.remove(view); view.setLongClickable(b.original);
                        }
                    } catch (Throwable e) { module.log(Log.WARN, "ACE-GroupMenu", "Cleanup failed", e); }
                }
                return chain.proceed();
            });
            for (Method m : new Method[]{View.class.getDeclaredMethod("performLongClick"),
                    View.class.getDeclaredMethod("performLongClick", float.class, float.class)}) {
                module.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        View view = (View) chain.getThisObject(); Binding b = bindings.get(view);
                        if (b != null && view.isShown() && view.isEnabled() && view.hasWindowFocus()) {
                            try {
                                String group = group(b); Activity a = activity(view.getContext());
                                if (group != null && a != null && !a.isFinishing() && !a.isDestroyed()) {
                                    android.content.pm.PackageInfo p = a.getPackageManager().getPackageInfo(HostPackages.QQ, 0);
                                    if ("9.3.50".equals(p.versionName) && p.versionCode == 15730) {
                                        QqGroupMenuDialog.show(a, group); return true;
                                    }
                                }
                            } catch (Throwable e) { module.log(Log.ERROR, "ACE-GroupMenu", "Open failed", e); }
                        }
                    }
                    return chain.proceed();
                });
            }
            installed = true;
        } catch (Throwable e) { module.log(Log.ERROR, "ACE-GroupMenu", "Adapter unavailable", e); }
    }
    private static String group(Binding b) throws Exception {
        Object owner = b.owner.get(); if (owner == null) return null;
        Object context = b.context.invoke(owner); if (context == null) return null;
        Object contact = call(call(call(context, "g"), "p"), "b");
        if (!Integer.valueOf(2).equals(call(contact, "d"))) return null;
        String group = String.valueOf(call(contact, "g"));
        return group.matches("[1-9][0-9]+") ? group : null;
    }
    private static Object call(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }
    private static Activity activity(Context c) {
        for (int i = 0; i < 16 && c != null; i++) {
            if (c instanceof Activity) return (Activity)c;
            if (!(c instanceof ContextWrapper)) break;
            Context next = ((ContextWrapper)c).getBaseContext(); if (next == c) break; c = next;
        }
        return null;
    }
    private static final class Binding {
        final WeakReference<Object> owner; final Method context; final boolean original;
        Binding(Object owner, Method context, boolean original) {
            this.owner = new WeakReference<>(owner); this.context = context; this.original = original;
        }
    }
}
