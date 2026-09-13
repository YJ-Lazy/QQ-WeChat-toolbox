package com.ace.toolbox.xposed;
import java.util.Arrays;
public final class MusicStateTest {
    static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    public static void main(String[] args) throws Exception {
        QqMusicState<String> s = new QqMusicState<>();
        String a = QqMusicState.key("10001", "20001", "alice");
        String b = QqMusicState.key("10001", "20001", "bob");
        String otherGroup = QqMusicState.key("10001", "20002", "alice");
        String otherAccount = QqMusicState.key("10002", "20001", "alice");
        s.remember(a, Arrays.asList("song A", "song B"), 1000, 0);
        s.remember(b, Arrays.asList("song C"), 1000, 0);
        check(s.get(a).songs.get(0).equals("song A"), "requester A order");
        check(s.get(b).songs.get(0).equals("song C"), "requester B isolation");
        check(s.get(otherGroup) == null && s.get(otherAccount) == null, "group and account isolation");
        check(s.get(a).valid(180999, 0) && !s.get(a).valid(181000, 0), "three-minute boundary");
        check(!s.get(a).valid(1001, 1), "toggle revision invalidates pending");
        check(s.claim("10001", "20001", 123), "new event");
        check(!s.claim("10001", "20001", 123), "duplicate send/receive callback");
        check(s.claim("10001", "20002", 123), "message ID isolation");
        check(!QqMusicState.allowed(0, true) && !QqMusicState.allowed(0, false), "disabled");
        check(QqMusicState.allowed(1, true) && !QqMusicState.allowed(1, false), "self scope");
        check(QqMusicState.allowed(2, true) && QqMusicState.allowed(2, false), "all scope");
        check(s.searchAllowed(a, 1000) && !s.searchAllowed(a, 5999) && s.searchAllowed(a, 6000), "cooldown");
        s.remove(a); check(s.get(a) == null && s.get(b) != null, "one-shot choice isolation");
        s.remember(b, Arrays.asList("new song"), 5000, 1);
        check(s.get(b).songs.get(0).equals("new song"), "latest query replaces older list");
        for (int i = 0; i < 130; i++) s.remember("k" + i, Arrays.asList("x"), 0, 0);
        check(s.get("k0") == null && s.get("k129") != null, "bounded pending memory");
        System.out.println("PASS: scope, isolation, ordering, expiry, revisions, deduplication, cooldown, bounded memory");
    }
}
