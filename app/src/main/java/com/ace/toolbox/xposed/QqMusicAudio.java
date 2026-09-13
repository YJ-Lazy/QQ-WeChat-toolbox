package com.ace.toolbox.xposed;

import android.content.Context;
import android.media.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.lang.reflect.Method;
import org.json.*;

/** Bounded local download, Android decoding, and QQ's own SILK encoder. */
final class QqMusicAudio {
    static String playable(QqMusicSearch.Song song) throws Exception {
        JSONObject param = new JSONObject().put("guid", "1000000000").put("songmid", new JSONArray().put(song.mid))
                .put("songtype", new JSONArray().put(0)).put("uin", "0").put("loginflag", 1).put("platform", "20");
        JSONObject req = new JSONObject().put("comm", new JSONObject().put("uin", 0).put("format", "json"))
                .put("req_0", new JSONObject().put("module", "vkey.GetVkeyServer").put("method", "CgiGetVkey").put("param", param));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        transfer("https://u.y.qq.com/cgi-bin/musicu.fcg?data=" + URLEncoder.encode(req.toString(), "UTF-8"), body, 1024*1024);
        JSONObject result = new JSONObject(body.toString("UTF-8")).getJSONObject("req_0");
        if (result.optInt("code", -1) != 0) throw new IOException("QQ音乐未提供音源");
        JSONObject data = result.getJSONObject("data");
        String path = data.getJSONArray("midurlinfo").getJSONObject(0).optString("purl");
        if (path.isEmpty()) throw new IOException("此歌曲没有可用音源，请换一首歌或使用卡片");
        String base = data.getJSONArray("sip").getString(0);
        String url = new URL(new URL(base), path).toString().replaceFirst("^http:", "https:");
        checkUrl(url); return url;
    }
    private static void checkUrl(String value) throws Exception {
        URL url = new URL(value); String host = url.getHost().toLowerCase(java.util.Locale.ROOT);
        if (!"https".equals(url.getProtocol()) || !(host.endsWith(".qq.com") || host.endsWith(".qqmusic.qq.com")))
            throw new IOException("音源地址不受支持");
    }
    static void transfer(String url, OutputStream out, int limit) throws Exception {
        checkUrl(url);
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","Mozilla/5.0"); c.setRequestProperty("Referer","https://y.qq.com/");
        long deadline=android.os.SystemClock.elapsedRealtime()+90000;
        try {
            if(c.getResponseCode()!=200) throw new IOException("音源下载失败："+c.getResponseCode());
            try(InputStream in=c.getInputStream()) {
                byte[] b=new byte[8192]; int n,total=0;
                while((n=in.read(b))!=-1) {
                    total+=n;
                    if(total>limit || android.os.SystemClock.elapsedRealtime()>deadline) throw new IOException("音源过大或下载超时");
                    out.write(b,0,n);
                }
                if(total==0) throw new IOException("音源为空");
            }
        } finally {c.disconnect();}
    }
    static final class Voice {
        final File file; final int durationMs;
        Voice(File f,int d){file=f;durationMs=d;}
    }
    static Voice prepare(Context context, QqMusicBridge host, String url) throws Exception {
        File dir=new File(context.getCacheDir(),"ace_music");
        if(!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法创建语音缓存");
        File[] old=dir.listFiles();
        if(old!=null) for(File f:old) if(System.currentTimeMillis()-f.lastModified()>86400000L) f.delete();
        File input=File.createTempFile("music-", ".audio",dir), output=File.createTempFile("voice-", ".silk",dir);
        boolean ok=false;
        try {
            try(OutputStream out=new FileOutputStream(input)){transfer(url,out,32*1024*1024);}
            int duration=encode(context,host,input,output);
            if(output.length()<12) throw new IOException("SILK编码结果为空");
            ok=true; return new Voice(output,duration);
        } finally {input.delete(); if(!ok) output.delete();}
    }
    private static int encode(Context context,QqMusicBridge host,File input,File output) throws Exception {
        MediaExtractor extractor=new MediaExtractor(); MediaCodec decoder=null;
        Class<?> codec=host.type("com.tencent.mobileqq.utils.SilkCodecWrapper");
        Object silk=codec.getConstructor(Context.class).newInstance(context);
        long handle=0; boolean started=false;
        try {
            extractor.setDataSource(input.getAbsolutePath()); MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++) {
                MediaFormat f=extractor.getTrackFormat(i);
                if(f.getString(MediaFormat.KEY_MIME).startsWith("audio/")){extractor.selectTrack(i);format=f;break;}
            }
            if(format==null) throw new IOException("音源不包含音频");
            if(format.containsKey(MediaFormat.KEY_DURATION) && format.getLong(MediaFormat.KEY_DURATION)>600000000L)
                throw new IOException("语音最长支持10分钟");
            decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format,null,null,0);decoder.start();started=true;
            handle=((Number)codec.getMethod("SilkEncoderNew",int.class,int.class).invoke(silk,24000,24000)).longValue();
            if(handle==0) throw new IOException("QQ SILK编码器未就绪");
            Method encode=codec.getMethod("encode",long.class,byte[].class,byte[].class,int.class);
            boolean inputEnd=false,outputEnd=false;
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int pcmEncoding=AudioFormat.ENCODING_PCM_16BIT, framePos=0; long count=0, phase=0;
            byte[] frame=new byte[960],packet=new byte[4096];
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            long deadline=android.os.SystemClock.elapsedRealtime()+120000;
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(output))) {
                out.write(2);out.write("#!SILK_V3".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                while(!outputEnd) {
                    if(android.os.SystemClock.elapsedRealtime()>deadline) throw new IOException("音频转换超时");
                    if(!inputEnd){int index=decoder.dequeueInputBuffer(10000);if(index>=0){
                        ByteBuffer b=decoder.getInputBuffer(index);b.clear();int n=extractor.readSampleData(b,0);
                        if(n<0){decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}
                        else{decoder.queueInputBuffer(index,0,n,extractor.getSampleTime(),0);extractor.advance();}
                    }}
                    int index=decoder.dequeueOutputBuffer(info,10000);
                    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=decoder.getOutputFormat();
                        rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        pcmEncoding=f.containsKey(MediaFormat.KEY_PCM_ENCODING)?f.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                        if(rate<=0 || channels<1 || channels>8 || (pcmEncoding!=AudioFormat.ENCODING_PCM_16BIT && pcmEncoding!=AudioFormat.ENCODING_PCM_FLOAT)) throw new IOException("不支持的PCM格式");
                    } else if(index>=0){
                        ByteBuffer b=decoder.getOutputBuffer(index).duplicate().order(ByteOrder.LITTLE_ENDIAN);
                        b.position(info.offset);b.limit(info.offset+info.size);
                        int bytes=pcmEncoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
                        while(b.remaining()>=channels*bytes){double sum=0;
                            for(int ch=0;ch<channels;ch++) sum+=bytes==4?b.getFloat()*32767:b.getShort();
                            short sample=(short)Math.max(-32768,Math.min(32767,Math.round(sum/channels)));
                            phase+=24000;
                            while(phase>=rate){phase-=rate;frame[framePos++]=(byte)sample;frame[framePos++]=(byte)(sample>>8);count++;
                                if(count>24000L*600) throw new IOException("语音最长支持10分钟");
                                if(framePos==frame.length){writeFrame(out,encode,silk,handle,frame,packet);framePos=0;}
                            }
                        }
                        outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;decoder.releaseOutputBuffer(index,false);
                    }
                }
                if(framePos>0){java.util.Arrays.fill(frame,framePos,frame.length,(byte)0);writeFrame(out,encode,silk,handle,frame,packet);}
                if(count==0) throw new IOException("音频解码为空");
            }
            return (int)((count*1000+23999)/24000);
        } finally {
            try{if(handle!=0)codec.getMethod("deleteCodec",long.class).invoke(silk,handle);}finally{
                try{if(decoder!=null){try{if(started)decoder.stop();}finally{decoder.release();}}}finally{extractor.release();}
            }
        }
    }
    private static void writeFrame(OutputStream out,Method encode,Object silk,long handle,byte[] pcm,byte[] packet) throws Exception {
        int n=((Number)encode.invoke(silk,handle,pcm,packet,pcm.length)).intValue();
        if(n<=0||n>packet.length) throw new IOException("SILK编码失败："+n);
        out.write(n&255);out.write((n>>8)&255);out.write(packet,0,n);
    }
}
