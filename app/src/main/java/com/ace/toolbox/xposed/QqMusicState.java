package com.ace.toolbox.xposed;

import java.util.LinkedHashMap;
import java.util.List;

/** State owned by the single music worker. Keys include account, group and requester. */
final class QqMusicState<T> {
    private final LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
    private final LinkedHashMap<String, Pending<T>> pending = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> searches = new LinkedHashMap<>();
    static boolean allowed(int mode, boolean own) { return mode == 2 || mode == 1 && own; }
    static String key(String account, String group, String sender) { return account + ":" + group + ":" + sender; }
    boolean claim(String account, String group, String id) {
        String k = key(account, group, id);
        if (seen.containsKey(k)) return false;
        seen.put(k, true); trim(seen, 1024); return true;
    }
    boolean searchAllowed(String key, long now) {
        Long last = searches.get(key);
        if (last != null && now - last < 5000) return false;
        searches.put(key, now); trim(searches, 256); return true;
    }
    void remember(String key, List<T> songs, long now, long revision) {
        pending.put(key, new Pending<>(songs, now + 180000, revision)); trim(pending, 128);
    }
    Pending<T> get(String key) { return pending.get(key); }
    void remove(String key) { pending.remove(key); }
    static final class Pending<T> {
        final List<T> songs; final long until, revision;
        Pending(List<T> songs, long until, long revision) { this.songs = songs; this.until = until; this.revision = revision; }
        boolean valid(long now, long currentRevision) { return now < until && revision == currentRevision; }
    }
    private static <V> void trim(LinkedHashMap<String, V> map, int max) {
        while (map.size() > max) map.remove(map.keySet().iterator().next());
    }
}
