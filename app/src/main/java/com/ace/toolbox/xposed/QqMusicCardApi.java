package com.ace.toolbox.xposed;

import java.io.*;
import java.net.*;
import org.json.*;

/** OIAPI adapter; sends song metadata and playable URL, never QQ account credentials. */
final class QqMusicCardApi {
    static String create(QqMusicSearch.Song song, String audio) throws Exception {
        String cover = song.album.isEmpty() ? "" :
                "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + song.album + ".jpg";
        String url = "https://oiapi.net/API/QQMusicJSONArk?format=qq&url=" + enc(audio)
                + "&song=" + enc(song.title) + "&singer=" + enc(song.singer)
                + "&cover=" + enc(cover) + "&jump=" + enc(song.link());
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IOException("OIAPI HTTP " + c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
            try (InputStream in = c.getInputStream()) {
                byte[] b = new byte[4096]; int n;
                while ((n = in.read(b)) != -1) {
                    if (out.size() + n > 262144 || android.os.SystemClock.elapsedRealtime() > deadline)
                        throw new IOException("OIAPI响应过大或超时");
                    out.write(b, 0, n);
                }
            }
            return parse(out.toString("UTF-8"));
        } finally { c.disconnect(); }
    }
    static String parse(String body) throws Exception {
        JSONObject response = new JSONObject(body);
        if (response.optInt("code", -1) != 1) throw new IOException("OIAPI未生成卡片");
        Object value = response.opt("message");
        JSONObject card = value instanceof JSONObject ? (JSONObject) value
                : new JSONObject(value instanceof String ? (String) value : "");
        if (card.optString("app").isEmpty() || card.optString("view").isEmpty()
                || card.optJSONObject("meta") == null || card.toString().length() > 65536)
            throw new IOException("OIAPI卡片结构无效");
        // Preserve returned fields, including any token; do not reconstruct the card.
        return card.toString();
    }
    private static String enc(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }
    private QqMusicCardApi() { }
}
