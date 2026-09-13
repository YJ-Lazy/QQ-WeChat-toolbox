package com.ace.toolbox.xposed;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class QqMusicSearch {
    static final class Song {
        final String mid, title, singer, album;
        Song(String mid, String title, String singer, String album) {
            this.mid = mid; this.title = title; this.singer = singer; this.album = album;
        }
        String card() throws JSONException {
            String url = "https://y.qq.com/n/ryqq/songDetail/" + mid;
            JSONObject music = new JSONObject().put("title", title).put("desc", singer)
                    .put("tag", "QQ音乐").put("jumpUrl", url)
                    .put("preview", album.isEmpty() ? "" : "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + album + ".jpg")
                    .put("sourceMsgId", "0").put("source_icon", "").put("source_url", "");
            // Share the official song page. Do not fabricate a playable URL or fetch paid audio.
            return new JSONObject().put("app", "com.tencent.structmsg").put("view", "music")
                    .put("ver", "0.0.0.1").put("prompt", "[分享]" + title)
                    .put("meta", new JSONObject().put("music", music))
                    .put("config", new JSONObject().put("forward", true).put("type", "normal"))
                    .put("desc", "音乐").toString();
        }
    }

    static List<Song> search(String query) throws Exception {
        String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p=1&n=10&w="
                + URLEncoder.encode(query, "UTF-8");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Referer", "https://y.qq.com/");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("QQ 音乐搜索暂不可用");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 2 * 1024 * 1024) throw new IOException("搜索响应过大");
                    out.write(buffer, 0, n);
                }
            }
            return parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally { connection.disconnect(); }
    }

    static List<Song> parse(String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        if (root.optInt("code", -1) != 0) throw new JSONException("QQ 音乐搜索失败");
        JSONArray rows = root.getJSONObject("data").getJSONObject("song").getJSONArray("list");
        List<Song> songs = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.length() && songs.size() < 10; i++) {
            JSONObject row = rows.getJSONObject(i);
            String mid = row.optString("songmid");
            if (!mid.matches("[A-Za-z0-9]+") || !seen.add(mid)) continue;
            JSONArray singers = row.optJSONArray("singer"); StringBuilder artist = new StringBuilder();
            if (singers != null) for (int j = 0; j < singers.length(); j++) {
                if (j > 0) artist.append(" / ");
                artist.append(clean(singers.getJSONObject(j).optString("name")));
            }
            String album = row.optString("albummid");
            songs.add(new Song(mid, clean(row.optString("songname")), artist.toString(),
                    album.matches("[A-Za-z0-9]+") ? album : ""));
        }
        return songs;
    }
    static String clean(String text) { return text.replace('\n', ' ').replace('\r', ' '); }
    private QqMusicSearch() { }
}
