package com.ace.toolbox.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Button;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
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
            new Item("点歌开关 / 触发范围", QqGroupMenuDialog::musicDialog),
            new Item("音乐发送方式", QqGroupMenuDialog::deliveryDialog),
            new Item("临时分享诊断", QqShareDiagnostics::menu)
    );
    static void show(Activity a, String group) {
        if (!usable(a)) return;
        Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a,24), dp(a,24), dp(a,24), dp(a,16));
        panel.setBackground(round(a, 0xfff5f9ff, 24));
        TextView title = label(a, "群聊工具", 23, 0xff192a40);
        title.setTypeface(null, 1); panel.addView(title);
        TextView subtitle = label(a, "群 " + group + " · 让聊天更有趣", 13, 0xff718096);
        subtitle.setPadding(0, dp(a,8), 0, dp(a,18)); panel.addView(subtitle);
        for (Item item : ITEMS) {
            TextView row = label(a, item.title + "   ›", 17, 0xff253c57);
            row.setPadding(dp(a,18), dp(a,18), dp(a,18), dp(a,18));
            row.setBackground(round(a, Color.WHITE, 16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin = dp(a,10); panel.addView(row, lp);
            row.setOnClickListener(v -> { dialog.dismiss(); if (usable(a)) item.action.open(a,group); });
        }
        TextView close = label(a, "完成", 16, 0xff247dcc);
        close.setGravity(17); close.setPadding(0,dp(a,14),0,dp(a,14));
        close.setOnClickListener(v -> dialog.dismiss()); panel.addView(close);
        dialog.setContentView(panel); dialog.show();
        if (dialog.getWindow()!=null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(Math.min(dp(a,420), a.getResources().getDisplayMetrics().widthPixels-dp(a,40)), -2);
        }
    }
    private static int dp(Activity a, int n) { return Math.round(n*a.getResources().getDisplayMetrics().density); }
    private static TextView label(Activity a,String text,int size,int color) {
        TextView v=new TextView(a); v.setText(text); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private static GradientDrawable round(Activity a,int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(a,radius)); return d;
    }
    private static void deliveryDialog(Activity a,String group) {
        try {
            QqMusicFeature feature=QqMusicFeature.INSTANCE; String account=feature.connect(a);
            String[] items={"音乐卡片 · OIAPI（实验）", "语音 · 本地转换 SILK"};
            new AlertDialog.Builder(a, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("音乐发送方式")
                .setMessage("OIAPI模式会将歌曲信息、封面和音频链接发给第三方生成卡片。接收端显示仍需实测。")
                .setSingleChoiceItems(items,feature.delivery(account,group),(d,i)->{
                    try {feature.delivery(account,group,i);d.dismiss();
                        Toast.makeText(a,"已设为："+items[i],Toast.LENGTH_SHORT).show();
                    } catch(Exception e){error(a,e);}
                }).setNegativeButton("返回",(d,i)->show(a,group)).show();
        } catch(Exception e){error(a,e);}
    }
    private static void musicDialog(Activity a, String group) {
        try {
            QqMusicFeature feature = QqMusicFeature.INSTANCE;
            String account = feature.connect(a);
            String[] modes = {"关闭", "开启 · 仅自己", "开启 · 所有人"};
            new AlertDialog.Builder(a, android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("点歌 · 群 " + group)
                    .setSingleChoiceItems(modes, feature.mode(account, group), (d, which) -> {
                        try {
                            feature.mode(account, group, which);
                            d.dismiss();
                            Toast.makeText(a, which == 0 ? "点歌已关闭" : "已开启：发送 点歌歌名，再发送序号", Toast.LENGTH_LONG).show();
                        } catch (Exception e) { error(a, e); }
                    }).setNegativeButton("返回", (d, i) -> show(a, group))
                    .setNeutralButton("用法", (d, i) -> new AlertDialog.Builder(a, android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("点歌用法")
                            .setMessage("发送：点歌 晴天\n收到列表后发送：1\n\n结果按搜索顺序编号，每人独立保留 3 分钟；开关仅作用于当前账号的本群。")
                            .setPositiveButton("返回", (x, j) -> musicDialog(a, group)).show()).show();
        } catch (Exception e) { error(a, e); }
    }
    private static void javaDialog(Activity a, String group) {
        String source = HostConfig.javaScriptSource(a);
        boolean ready = HostConfig.javaScriptEnabled(a) && source != null && !source.trim().isEmpty();
        AlertDialog dialog = new AlertDialog.Builder(a, android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Java")
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
                            dialog.dismiss(); new AlertDialog.Builder(a, android.R.style.Theme_Material_Light_Dialog_Alert).setTitle(result.success ? "执行完成" : "执行失败")
                                    .setMessage(result.message).setPositiveButton("关闭", null).show();
                        }});
                    } catch (Throwable e) { a.runOnUiThread(() -> { if (usable(a)) { dialog.dismiss(); error(a, e); } }); }
                }, "ACE-group-java").start();
            });
        }); dialog.show();
    }
    private static void error(Activity a, Throwable error) {
        if (usable(a)) new AlertDialog.Builder(a, android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("操作未完成").setMessage(String.valueOf(error.getMessage()))
                .setPositiveButton("关闭", null).show();
    }
    private static boolean usable(Activity a) { return a != null && !a.isFinishing() && !a.isDestroyed(); }
}
