package com.ace.toolbox.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.Toast;
import java.util.Arrays;
import java.util.List;

final class QqGroupMenuDialog {
    private interface Action { void open(Activity a, String group); }
    private static final class Item {
        final String title; final Action action;
        Item(String title, Action action) { this.title = title; this.action = action; }
    }
    // Future features: append an Item; the QQ settings panel is independent.
    private static final List<Item> ITEMS = Arrays.asList(
            new Item("Java", QqGroupMenuDialog::javaDialog),
            new Item("点歌开关 / 触发范围", QqGroupMenuDialog::musicDialog)
    );
    static void show(Activity a, String group) {
        if (!usable(a)) return;
        String[] labels = new String[ITEMS.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = ITEMS.get(i).title;
        new AlertDialog.Builder(a).setTitle("群聊菜单").setItems(labels, (d, i) -> {
            if (usable(a)) ITEMS.get(i).action.open(a, group);
        }).setNegativeButton("关闭", null).show();
    }
    private static void musicDialog(Activity a, String group) {
        try {
            QqMusicFeature feature = QqMusicFeature.INSTANCE;
            String account = feature.connect(a);
            String[] modes = {"关闭", "开启 · 仅自己", "开启 · 所有人"};
            new AlertDialog.Builder(a).setTitle("点歌 · 群 " + group)
                    .setSingleChoiceItems(modes, feature.mode(account, group), (d, which) -> {
                        try {
                            feature.mode(account, group, which);
                            d.dismiss();
                            Toast.makeText(a, which == 0 ? "点歌已关闭" : "已开启：发送 点歌歌名，再发送序号", Toast.LENGTH_LONG).show();
                        } catch (Exception e) { error(a, e); }
                    }).setNegativeButton("返回", (d, i) -> show(a, group))
                    .setNeutralButton("用法", (d, i) -> new AlertDialog.Builder(a).setTitle("点歌用法")
                            .setMessage("发送：点歌 晴天\n收到列表后发送：1\n\n结果按搜索顺序编号，每人独立保留 3 分钟；开关仅作用于当前账号的本群。")
                            .setPositiveButton("返回", (x, j) -> musicDialog(a, group)).show()).show();
        } catch (Exception e) { error(a, e); }
    }
    private static void javaDialog(Activity a, String group) {
        String source = HostConfig.javaScriptSource(a);
        boolean ready = HostConfig.javaScriptEnabled(a) && source != null && !source.trim().isEmpty();
        AlertDialog dialog = new AlertDialog.Builder(a).setTitle("Java")
                .setMessage(ready ? "运行已保存的 Java 脚本？\n脚本将在 QQ 进程中执行。" : "请先在 ACE App 中启用并保存 Java 脚本。")
                .setNegativeButton("返回", (d, i) -> show(a, group)).setPositiveButton("运行脚本", null).create();
        dialog.setOnShowListener(d -> {
            Button run = dialog.getButton(AlertDialog.BUTTON_POSITIVE); run.setEnabled(ready);
            run.setOnClickListener(v -> {
                run.setEnabled(false); run.setText("运行中…");
                new Thread(() -> {
                    try {
                        HostJavaScriptEngine.RunResult result = HostJavaScriptEngine.run(a);
                        a.runOnUiThread(() -> { if (usable(a)) {
                            dialog.dismiss(); new AlertDialog.Builder(a).setTitle(result.success ? "执行完成" : "执行失败")
                                    .setMessage(result.message).setPositiveButton("关闭", null).show();
                        }});
                    } catch (Throwable e) { a.runOnUiThread(() -> { if (usable(a)) { dialog.dismiss(); error(a, e); } }); }
                }, "ACE-group-java").start();
            });
        }); dialog.show();
    }
    private static void error(Activity a, Throwable error) {
        if (usable(a)) new AlertDialog.Builder(a).setTitle("操作未完成").setMessage(String.valueOf(error.getMessage()))
                .setPositiveButton("关闭", null).show();
    }
    private static boolean usable(Activity a) { return a != null && !a.isFinishing() && !a.isDestroyed(); }
}
