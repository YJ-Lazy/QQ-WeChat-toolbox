package com.ace.toolbox.xposed;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.provider.MediaStore;
import android.widget.Toast;
import io.github.libxposed.api.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.*;

/** Opt-in, five-minute trace. Never changes host arguments, results or exceptions. */
final class QqShareDiagnostics {
    private static final ExecutorService IO = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(256),r->new Thread(r,"ACE-share-trace"),new ThreadPoolExecutor.AbortPolicy());
    private static volatile long until;
    private static volatile File log;
    private static final AtomicLong ids=new AtomicLong();
    private static final ThreadLocal<Boolean> reading=new ThreadLocal<>();
    private static final List<String> coverage=new ArrayList<>();
    private static boolean installed;
    static synchronized void install(XposedModule module,ClassLoader loader) {
        if(installed)return;installed=true;
        String[] names={"com.tencent.mobileqq.forward.ForwardArkMsgOption",
            "com.tencent.mobileqq.forward.ForwardArkH5StructOption",
            "com.tencent.mobileqq.forward.ForwardPluginShareStructMsgOption",
            "com.tencent.mobileqq.webview.share.ForwardArkMsgForWebShareOption",
            "com.tencent.mobileqq.data.ArkAppMessage",
            "com.tencent.mobileqq.activity.ChatActivityFacade",
            "com.tencent.qqnt.kernel.api.impl.MsgService"};
        for(String name:names) try {
            Class<?> c=Class.forName(name,false,loader);
            for(Method m:c.getDeclaredMethods()) {
                if(Modifier.isAbstract(m.getModifiers()) || Modifier.isNative(m.getModifiers()))continue;
                String n=m.getName();
                boolean selected=name.endsWith("ArkAppMessage") ? Arrays.asList("fromAppXml","toAppXml","toShareMsgJSONObject","toPbData").contains(n)
                    : name.endsWith("MsgService") ? n.equals("sendMsg")
                    : name.endsWith("ChatActivityFacade") ? Arrays.stream(m.getParameterTypes()).anyMatch(t->t.getName().endsWith("ArkAppMessage"))
                    : Arrays.asList("r","s","v","forwardOnConfirm","realForwardTo","preloadData","sendMessage2TargetOnConfirm").contains(n);
                if(!selected)continue;
                try {
                    m.setAccessible(true);
                    module.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                        boolean active=SystemClock.elapsedRealtime()<until && !Boolean.TRUE.equals(reading.get());
                        long id=active?ids.incrementAndGet():0;
                        if(active)try {
                            reading.set(true);
                            StringBuilder b=new StringBuilder("ENTER #"+id+" "+m.toGenericString());
                            for(int i=0;i<m.getParameterTypes().length;i++)b.append("\narg").append(i).append('=').append(summary(chain.getArg(i),0));
                            Object self=chain.getThisObject();
                            if(self!=null && name.contains("forward")) {
                                for(Class<?> k=self.getClass();k!=null;k=k.getSuperclass())try {
                                    Field f=k.getDeclaredField("mExtraData");f.setAccessible(true);b.append("\nextras=").append(summary(f.get(self),0));break;
                                }catch(NoSuchFieldException ignored){}
                            }
                            StackTraceElement[] stack=Thread.currentThread().getStackTrace();
                            for(int i=3;i<Math.min(stack.length,17);i++)b.append("\n at ").append(stack[i]);
                            record(b.toString());
                        }catch(Throwable ignored){}finally{reading.remove();}
                        Object result;
                        try {result=chain.proceed();}catch(Throwable e){if(active)record("THROW #"+id+" "+e.getClass().getName());throw e;}
                        if(active)try{reading.set(true);record("EXIT #"+id+" "+summary(result,0));}catch(Throwable ignored){}finally{reading.remove();}
                        return result;
                    });
                    coverage.add("OK "+m.toGenericString());
                } catch(Throwable e){coverage.add("FAIL "+m.toGenericString()+" "+e.getClass().getSimpleName());}
            }
        }catch(Throwable e){coverage.add("MISSING "+name+" "+e.getClass().getSimpleName());}
    }
    static void menu(Activity a,String group) {
        new android.app.AlertDialog.Builder(a,android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("卡片分享诊断")
            .setMessage("临时记录5分钟分享调用（当前进程内其他分享也可能被记录）。\n开始后先从QQ音乐正常分享一次，再用点歌发一次卡片，最后停止并导出。\n日志含歌曲信息和调用参数摘要；token和账号会脱敏。")
            .setPositiveButton("开始记录",(d,w)->{
                synchronized(QqShareDiagnostics.class){
                    if(SystemClock.elapsedRealtime()<until){toast(a,"诊断正在记录，请先停止导出");return;}
                    File dir=new File(a.getCacheDir(),"ace_share_trace");dir.mkdirs();
                    File[] old=dir.listFiles();if(old!=null)for(File f:old)if(System.currentTimeMillis()-f.lastModified()>86400000L)f.delete();
                    log=new File(dir,"share-"+System.currentTimeMillis()+".log");
                    until=SystemClock.elapsedRealtime()+300000;
                    record("START QQ 9.3.50; group="+fingerprint(group)+"\n"+String.join("\n",coverage));
                }
                toast(a,"已开始记录，5分钟后自动停止");
            }).setNeutralButton("停止并导出",(d,w)->{
                until=0;File file=log;
                if(file==null){toast(a,"请先开始记录");return;}
                // A dedicated thread avoids dropping an export when the trace queue is full.
                new Thread(()->{
                    try {
                        IO.submit(()->{}).get(10,TimeUnit.SECONDS);
                        File snapshot;
                        synchronized(QqShareDiagnostics.class){snapshot=File.createTempFile("trace-export-",".log",a.getCacheDir());
                            try(OutputStream out=new FileOutputStream(snapshot)){if(file.exists())try(InputStream in=new FileInputStream(file)){copy(in,out);}}
                        }
                        try {export(a,snapshot,file.getName());}finally{snapshot.delete();}
                    }catch(Exception e){toast(a,"导出失败："+e.getClass().getSimpleName());}
                },"ACE-share-export").start();
            }).setNegativeButton("返回",null).show();
    }
    private static String summary(Object o,int depth) throws Exception {
        if(o==null)return "null";
        if(depth>3)return o.getClass().getName();
        if(o instanceof Boolean)return o.toString();
        if(o instanceof Number)return Math.abs(((Number)o).longValue())<10000?o.toString():"number:"+fingerprint(o.toString());
        if(o instanceof byte[])return "bytes length="+((byte[])o).length;
        if(o instanceof CharSequence || o instanceof JSONObject) {
            String s=o.toString();
            if(s.length()>32768)return "string length="+s.length();
            if(s.trim().startsWith("{"))try{return clean(new JSONObject(s)).toString();}catch(JSONException ignored){}
            return "string length="+s.length()+" "+fingerprint(s);
        }
        if(o instanceof Bundle){Bundle b=(Bundle)o;JSONObject j=new JSONObject();int n=0;
            for(String k:b.keySet()){if(n++>=40)break;j.put(k,k.toLowerCase(Locale.ROOT).matches(".*(token|key|cookie|uin|ticket|auth).*")?"redacted":summary(b.get(k),depth+1));}return j.toString();}
        if(o instanceof Map){StringBuilder b=new StringBuilder("map{");int n=0;for(Object raw:((Map<?,?>)o).entrySet()){if(n++>=12)break;Map.Entry<?,?> e=(Map.Entry<?,?>)raw;b.append(summary(e.getKey(),depth+1)).append('=').append(summary(e.getValue(),depth+1)).append(',');}return b+"}";}
        if(o instanceof Collection){StringBuilder b=new StringBuilder("[");int n=0;for(Object x:(Collection<?>)o){if(n++>=12)break;b.append(summary(x,depth+1)).append(',');}return b+"]";}
        String name=o.getClass().getName();StringBuilder b=new StringBuilder(name);
        String[] getters=name.endsWith("MsgElement")?new String[]{"getElementType","getArkElement"}
            :name.endsWith("ArkElement")?new String[]{"getBytesData","getLinkInfo"}
            :name.endsWith("ArkAppMessage")?new String[]{"toAppXml"}:new String[0];
        for(String g:getters)try{b.append(' ').append(g).append('=').append(summary(o.getClass().getMethod(g).invoke(o),depth+1));}catch(Exception ignored){}
        return b.toString();
    }
    private static JSONObject clean(JSONObject source) throws Exception {
        JSONObject out=new JSONObject();Iterator<String> keys=source.keys();
        while(keys.hasNext()){String k=keys.next();Object v=source.get(k);String lower=k.toLowerCase(Locale.ROOT);
            if(lower.matches(".*(token|uin|cookie|ticket|auth|msg_seq|hosteuin).*"))out.put(k,"len="+String.valueOf(v).length()+" "+fingerprint(String.valueOf(v)));
            else if(v instanceof JSONObject)out.put(k,clean((JSONObject)v));
            else if(v instanceof JSONArray)out.put(k,"array length="+((JSONArray)v).length());
            else if(v instanceof String && ((String)v).startsWith("http"))out.put(k,((String)v).split("\\?",2)[0]);
            else out.put(k,v);
        }return out;
    }
    private static String fingerprint(String s) {
        try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder("sha256:");for(int i=0;i<6;i++)x.append(String.format("%02x",b[i]&255));return x.toString();}catch(Exception e){return "redacted";}
    }
    private static void record(String s){final File f=log;if(f==null)return;String line=System.currentTimeMillis()+" tid="+Thread.currentThread().getId()+" "+s+"\n";
        try { IO.execute(()->{synchronized(QqShareDiagnostics.class){try{if(f.length()>2*1024*1024)return;try(OutputStream out=new FileOutputStream(f,true)){out.write(line.getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}}}); } catch(RejectedExecutionException ignored){} }
    private static void export(Activity a,File f,String name)throws Exception {
        if(Build.VERSION.SDK_INT<29)throw new IOException("导出需要Android 10以上");
        ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,"ACE-"+name);v.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/ACE-Diagnostics");v.put(MediaStore.MediaColumns.IS_PENDING,1);
        ContentResolver r=a.getContentResolver();android.net.Uri uri=r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
        if(uri==null)throw new IOException("无法创建下载文件");
        try{try(InputStream in=new FileInputStream(f);OutputStream out=r.openOutputStream(uri)){if(out==null)throw new IOException("无法写入");copy(in,out);}
            v.clear();v.put(MediaStore.MediaColumns.IS_PENDING,0);r.update(uri,v,null,null);
        }catch(Exception e){r.delete(uri,null,null);throw e;}
        toast(a,"已导出：Download/ACE-Diagnostics/ACE-"+name);
    }
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    private static void toast(Activity a,String s){a.runOnUiThread(()->{if(!a.isDestroyed())Toast.makeText(a,s,Toast.LENGTH_LONG).show();});}
}
