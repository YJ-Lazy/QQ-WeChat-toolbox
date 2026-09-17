package com.ace.toolbox.xposed;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

final class QqMusicFeature {
    static final QqMusicFeature INSTANCE = new QqMusicFeature();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), r -> new Thread(r, "ACE-music"), new ThreadPoolExecutor.AbortPolicy());
    private final Map<String, AtomicLong> revisions = new ConcurrentHashMap<>();
    private final QqMusicState<QqMusicSearch.Song> state = new QqMusicState<>();
    private volatile QqMusicBridge bridge;
    private volatile Context app;
    private volatile WeakReference<Activity> activity = new WeakReference<>(null);

    void resume(Activity a) {
        activity = new WeakReference<>(a); app = a.getApplicationContext();
        // Do not inspect host account/service until at least one group has been enabled.
        boolean any = false;
        for (Object value : prefs().getAll().values()) if (value instanceof Integer && (Integer) value > 0) any = true;
        if (any) try { connect(a); } catch (Exception e) { Log.w("ACE-Music", "Service not ready", e); }
    }

    synchronized String connect(Activity a) throws Exception {
        app = a.getApplicationContext(); activity = new WeakReference<>(a);
        QqMusicBridge next = new QqMusicBridge(a);
        if (bridge != null && bridge.runtime == next.runtime && bridge.service == next.service
                && bridge.account.equals(next.account)) return bridge.account;
        QqMusicBridge old = bridge;
        bridge = null;
        if (old != null) old.close();
        next.listen(this); bridge = next;
        return next.account;
    }

    int mode(String account, String group) { return prefs().getInt(key(account, group), 0); }
    void mode(String account, String group, int mode) {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("mode");
        if (!prefs().edit().putInt(key(account, group), mode).commit())
            throw new IllegalStateException("开关保存失败");
        revision(account, group).incrementAndGet();
    }
    int delivery(String account, String group) { return prefs().getInt("delivery:"+key(account,group),0); }
    void delivery(String account, String group, int value) {
        if(value<0 || value>1) throw new IllegalArgumentException("delivery");
        if(!prefs().edit().putInt("delivery:"+key(account,group),value).commit()) throw new IllegalStateException("发送方式保存失败");
        revision(account,group).incrementAndGet();
    }
    private SharedPreferences prefs() { return app.getSharedPreferences("ace_group_music", Context.MODE_PRIVATE); }
    private static String key(String account, String group) { return account + ":" + group; }
    private AtomicLong revision(String account, String group) {
        return revisions.computeIfAbsent(key(account, group), ignored -> new AtomicLong());
    }

    void accept(QqMusicBridge source, Object records, boolean outgoing) {
        if (source != bridge || records == null) return;
        if (records instanceof Collection<?>) {
            for (Object record : (Collection<?>) records) accept(source, record, outgoing);
            return;
        }
        try {
            AceScriptMessageEvent event = AceScriptMessageEvent.from(outgoing ? "send" : "receive", records);
            if (event.chatType != 2) return;
            String group = event.peerUid.matches("[1-9][0-9]+")
                    ? event.peerUid : event.peerUin;
            if (!group.matches("[1-9][0-9]+")) return;
            boolean own = outgoing || source.account.equals(event.senderUin);
            String sender = own ? source.account : !event.senderUid.isEmpty() ? event.senderUid : event.senderUin;
            if (sender.isEmpty()) return;
            int mode = mode(source.account, group);
            if (!QqMusicState.allowed(mode, own)) return;
            String text = event.text.trim();
            if (!text.startsWith("点歌") && !text.startsWith("QQ点歌")
                    && !text.matches("[0-9]{1,2}") && !"取消点歌".equals(text)) return;
            long seconds = event.time > 100000000000L ? event.time / 1000 : event.time;
            if (seconds <= 0 || Math.abs(System.currentTimeMillis() / 1000 - seconds) > 90) return;
            long rev = revision(source.account, group).get();
            // Some QQNT send callbacks expose msgId=0. Keep duplicate protection with
            // a deterministic fallback key instead of dropping the command entirely.
            String eventId = event.msgId != 0 ? Long.toString(event.msgId)
                    : (event.direction + "|" + group + "|" + sender + "|" + seconds + "|" + text);
            Log.i("ACE-Music", "accepted " + event.direction + " group=" + group
                    + " sender=" + sender + " text=" + text + " msgId=" + event.msgId);
            worker.execute(() -> handle(source, group, sender, own, eventId, text, rev));
        } catch (RejectedExecutionException busy) {
            notice("点歌任务较多，请稍后重试");
        } catch (Throwable error) { Log.e("ACE-Music", "Message parsing failed", error); }
    }

    private boolean current(QqMusicBridge source, String group, boolean own, long rev) {
        int m = mode(source.account, group);
        return source == bridge && QqMusicState.allowed(m, own)
                && revision(source.account, group).get() == rev;
    }

    private void handle(QqMusicBridge source, String group, String sender, boolean own, String eventId, String text, long rev) {
        if (!current(source, group, own, rev)) return;
        String sessionKey = QqMusicState.key(source.account, group, sender);
        if (!state.claim(source.account, group, eventId)) return;
        long now = System.currentTimeMillis();
        try {
            if (text.startsWith("点歌")) {
                Log.i("ACE-Music", "search start group=" + group + " query=" + text.substring(2).trim());
                String query = text.substring(2).trim();
                if (query.isEmpty() || query.length() > 80) {
                    source.send(group, "用法：点歌 歌名（最多 80 字）", false); return;
                }
                if (!state.searchAllowed(sessionKey, now)) return;
                state.remove(sessionKey);
                List<QqMusicSearch.Song> songs = QqMusicSearch.search(query);
                Log.i("ACE-Music", "search result count=" + songs.size() + " group=" + group);
                if (!current(source, group, own, rev)) return;
                if (songs.isEmpty()) { source.send(group, "QQ音乐：没有找到相关歌曲，请换个关键词。", false); return; }
                StringBuilder result = new StringBuilder("QQ音乐 · ").append(QqMusicSearch.clean(query));
                for (int i = 0; i < songs.size(); i++) result.append('\n').append(i + 1).append(". ")
                        .append(songs.get(i).title).append(" — ").append(songs.get(i).singer);
                result.append("\n请点歌者在 3 分钟内发送序号；序号对应你最近一次点歌。");
                source.send(group, result.toString(), false);
                if (current(source, group, own, rev)) {
                    state.remember(sessionKey, songs, System.currentTimeMillis(), rev);
                }
            } else if (text.startsWith("QQ点歌")) {
                String query = text.substring(4).trim();
                if (query.isEmpty() || query.length() > 80) {
                    source.send(group, "用法：QQ点歌 歌名（最多 80 字）", false); return;
                }
                if (!state.searchAllowed(sessionKey, now)) return;
                state.remove(sessionKey);
                List<QqMusicSearch.Song> songs = QqMusicSearch.search(query);
                if (!current(source, group, own, rev)) return;
                if (songs.isEmpty()) { source.send(group, "QQ音乐：没有找到相关歌曲，请换个关键词。", false); return; }
                StringBuilder result = new StringBuilder("QQ音乐 · ").append(QqMusicSearch.clean(query));
                for (int i = 0; i < songs.size(); i++) result.append('\n').append(i + 1).append(". ")
                        .append(songs.get(i).title).append(" — ").append(songs.get(i).singer);
                result.append("\n请点歌者在 3 分钟内发送序号；序号对应你最近一次点歌。");
                source.send(group, result.toString(), false);
                if (current(source, group, own, rev)) state.remember(sessionKey, songs, now, rev);
            } else if ("取消点歌".equals(text)) {
                if (state.get(sessionKey) != null) {
                    state.remove(sessionKey);
                    source.send(group, "已取消本次点歌。", false);
                }
            } else {
                QqMusicState.Pending<QqMusicSearch.Song> choice = state.get(sessionKey);
                if (choice == null) {
                    Log.i("ACE-Music", "selection ignored: no pending search group=" + group + " sender=" + sender);
                    return; // Ordinary group numbers are not commands without an active search.
                }
                if (!choice.valid(now, rev)) {
                    state.remove(sessionKey); source.send(group, "点歌结果已过期，请重新发送点歌指令。", false); return;
                }
                int index = Integer.parseInt(text) - 1;
                if (index < 0 || index >= choice.songs.size()) {
                    source.send(group, "请输入 1–" + choice.songs.size() + " 之间的序号。", false); return;
                }
                // Remove before sending: an uncertain/late acknowledgment must never cause an automatic duplicate.
                state.remove(sessionKey);
                if (!current(source, group, own, rev)) return;
                QqMusicSearch.Song song = choice.songs.get(index);
                Log.i("ACE-Music", "selection=" + (index + 1) + " title=" + song.title
                        + " delivery=" + delivery(source.account, group));
                if (delivery(source.account,group)==1) {
                    notice("正在下载并转换SILK语音…");
                    String url=QqMusicAudio.playable(song);
                    if (!current(source,group,own,rev)) return;
                    QqMusicAudio.Voice voice=QqMusicAudio.prepare(app,source,url);
                    if (!current(source,group,own,rev)) { voice.file.delete(); return; }
                    // Keep cache for QQ's asynchronous upload/retry; prune after 24 hours.
                    source.sendVoice(group,voice);
                } else {
                    if (current(source, group, own, rev)) {
                        try {
                            String audio = QqMusicAudio.playable(song);
                            if (!current(source, group, own, rev)) return;
                            String card = QqMusicCardApi.create(song, audio);
                            if (!current(source, group, own, rev)) return;
                            source.send(group, card, true);
                            Log.i("ACE-Music", "OIAPI card send acknowledged");
                        } catch (Exception cardError) {
                            Log.w("ACE-Music", "OIAPI card failed: " + cardError.getClass().getSimpleName());
                            if (!current(source, group, own, rev)) return;
                            source.send(group, "卡片生成或发送未确认，歌曲："
                                    + song.title + " — " + song.singer + "\n" + song.link(), false);
                            notice("卡片不可用，已发送歌曲链接；可在菜单切换为 SILK 语音");
                        }
                    }
                }
            }
        } catch (Throwable error) {
            Log.e("ACE-Music", "Music request failed", error);
            if (current(source, group, own, rev)) notice("点歌失败：" + error.getMessage());
        }
    }

    private void notice(String message) {
        Activity a = activity.get();
        if (a != null && !a.isDestroyed()) a.runOnUiThread(() -> Toast.makeText(a, message, Toast.LENGTH_LONG).show());
    }
}
