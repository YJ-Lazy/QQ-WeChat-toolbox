package com.ace.toolbox.xposed;

import android.content.Context;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Reflection adapter for the inspected QQ 9.3.50/15730 APK. No credentials are extracted. */
final class QqMusicBridge {
    static final String N = "com.tencent.qqnt.kernel.nativeinterface.";
    final ClassLoader loader;
    final Object runtime, service;
    final String account;
    private Object listener;

    QqMusicBridge(Context context) throws Exception {
        loader = context.getClassLoader();
        android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(HostPackages.QQ, 0);
        if (!"9.3.50".equals(info.versionName) || info.versionCode != 15730)
            throw new IllegalStateException("点歌目前适配 QQ 9.3.50 (15730)");
        Class<?> application = type("com.tencent.common.app.BaseApplicationImpl");
        Object app = application.getMethod("getApplication").invoke(null);
        runtime = call(app, "getRuntime");
        account = String.valueOf(call(runtime, "getCurrentAccountUin"));
        if (!account.matches("[1-9][0-9]{4,}")) throw new IllegalStateException("QQ 尚未登录");
        Object kernel = runtime.getClass().getMethod("getRuntimeService", Class.class, String.class)
                .invoke(runtime, type("com.tencent.qqnt.kernel.api.IKernelService"), "");
        service = call(kernel, "getMsgService");
        if (service == null) throw new IllegalStateException("QQ 消息服务尚未就绪");
    }

    void listen(QqMusicFeature owner) throws Exception {
        Class<?> iface = type(N + "IKernelMsgListener");
        listener = Proxy.newProxyInstance(loader, new Class<?>[]{iface}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
            if (args != null && args.length > 0) {
                if ("onRecvMsg".equals(method.getName())) owner.accept(this, args[0], false);
                if ("onAddSendMsg".equals(method.getName())) owner.accept(this, args[0], true);
            }
            return null;
        });
        service.getClass().getMethod("addMsgListener", iface).invoke(service, listener);
    }

    void close() {
        try {
            if (listener != null) service.getClass().getMethod("removeMsgListener", type(N + "IKernelMsgListener"))
                    .invoke(service, listener);
        } catch (Exception ignored) { }
        listener = null;
    }

    void send(String group, String body, boolean ark) throws Exception {
        if (!account.equals(String.valueOf(call(runtime, "getCurrentAccountUin"))))
            throw new IllegalStateException("账号已切换，已取消发送");
        Class<?> contactType = type("com.tencent.qqnt.kernelpublic.nativeinterface.Contact");
        Object contact = contactType.getConstructor(int.class, String.class, String.class).newInstance(2, group, "");
        Class<?> elemType = type(N + "MsgElement");
        Object elem = elemType.getConstructor().newInstance();
        elemType.getMethod("setElementType", int.class).invoke(elem, ark ? 10 : 1);
        if (ark) {
            Class<?> a = type(N + "ArkElement");
            Object value = a.getConstructor(String.class, type(N + "LinkInfo"), Integer.class)
                    .newInstance(body, null, null);
            elemType.getMethod("setArkElement", a).invoke(elem, value);
        } else {
            Class<?> t = type(N + "TextElement");
            Object value = t.getConstructor().newInstance();
            t.getMethod("setContent", String.class).invoke(value, body);
            elemType.getMethod("setTextElement", t).invoke(elem, value);
        }
        long id = ((Number) service.getClass().getMethod("generateMsgUniqueId", int.class).invoke(service, 2)).longValue();
        ArrayList<Object> elements = new ArrayList<>(); elements.add(elem);
        CountDownLatch done = new CountDownLatch(1);
        int[] status = {-1};
        Class<?> callbackType = type(N + "IOperateCallback");
        Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{callbackType}, (p, m, args) -> {
            if (m.getDeclaringClass() == Object.class) return objectMethod(p, m, args);
            if (args != null && args.length > 0 && args[0] instanceof Number) {
                status[0] = ((Number) args[0]).intValue(); done.countDown();
            }
            return null;
        });
        Class<?> attrType = type(N + "MsgAttributeInfo");
        Object attr = attrType.getConstructor().newInstance();
        HashMap<Integer, Object> attrs = new HashMap<>();
        attrs.put(0, attr);
        service.getClass().getMethod("sendMsg", long.class, contactType, ArrayList.class, HashMap.class, callbackType)
                .invoke(service, id, contact, elements, attrs, callback);
        if (!done.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("发送回执超时，请检查群内消息；不会自动重发");
        if (status[0] != 0) throw new IllegalStateException("QQ 发送失败，错误码 " + status[0]);
    }

    Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, false, loader); }
    static Object call(Object obj, String name) throws Exception { return obj.getClass().getMethod(name).invoke(obj); }
    static Object objectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "hashCode": return System.identityHashCode(proxy);
            case "equals": return args != null && args.length == 1 && proxy == args[0];
            default: return "ACE music callback";
        }
    }
}
