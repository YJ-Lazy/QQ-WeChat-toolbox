package com.ace.toolbox.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class HostCleanerDialog {
    private static final int BLUE = Color.rgb(24, 119, 242);
    private static final int GREEN = Color.rgb(52, 199, 89);
    private static final int ORANGE = Color.rgb(255, 149, 0);
    private static final int TEXT_PRIMARY = Color.rgb(28, 28, 30);
    private static final int TEXT_SECONDARY = Color.rgb(108, 108, 112);
    private static final int CARD_BG = Color.rgb(247, 247, 249);
    private static final int DIVIDER = Color.rgb(229, 229, 234);

    static void show(Activity activity, String pkg) {
        Set<String> selectedIds = new HashSet<>();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 12));
        root.setBackground(roundRect(Color.WHITE, dp(activity, 26)));

        root.addView(buildHeader(activity, pkg));
        addSpace(activity, root, 16);

        LinearLayout cleanCard = card(activity);
        TextView cleanLabel = text(activity, "安全清理", 13, TEXT_SECONDARY, false);
        TextView cleanAmount = text(activity, "正在扫描…", 28, TEXT_PRIMARY, true);
        TextView cleanMeta = text(activity, "正在识别 QQ 缓存项目", 13, TEXT_SECONDARY, false);
        cleanMeta.setPadding(0, dp(activity, 4), 0, dp(activity, 10));

        ProgressBar scanBar = new ProgressBar(
                activity, null, android.R.attr.progressBarStyleHorizontal);
        scanBar.setIndeterminate(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            scanBar.setProgressTintList(ColorStateList.valueOf(BLUE));
            scanBar.setIndeterminateTintList(ColorStateList.valueOf(BLUE));
        }

        Button chooseButton = compactButton(activity, "选择清理项", TEXT_PRIMARY, Color.rgb(238, 238, 242));
        chooseButton.setEnabled(false);
        chooseButton.setAlpha(.45f);

        cleanCard.addView(cleanLabel);
        cleanCard.addView(cleanAmount);
        cleanCard.addView(cleanMeta);
        cleanCard.addView(scanBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 6)));
        addSpace(activity, cleanCard, 12);
        cleanCard.addView(chooseButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44)));
        root.addView(cleanCard);

        addSpace(activity, root, 12);
        root.addView(buildSsaidCard(activity, pkg));
        if (HostPackages.QQ.equals(pkg)) {
            addSpace(activity, root, 12);
            root.addView(buildJavaScriptCard(activity));
        }
        addSpace(activity, root, 14);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button close = compactButton(activity, "关闭", TEXT_PRIMARY, Color.rgb(240, 240, 243));
        Button clean = compactButton(activity, "扫描中…", Color.WHITE, BLUE);
        clean.setTypeface(Typeface.DEFAULT_BOLD);
        clean.setEnabled(false);

        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(0, dp(activity, 50), 1f);
        actions.addView(close, closeLp);
        LinearLayout.LayoutParams cleanLp = new LinearLayout.LayoutParams(0, dp(activity, 50), 1f);
        cleanLp.setMarginStart(dp(activity, 10));
        actions.addView(clean, cleanLp);
        root.addView(actions);

        ScrollView outer = new ScrollView(activity);
        outer.setFillViewport(true);
        outer.setBackgroundColor(Color.TRANSPARENT);
        outer.setClipToOutline(false);
        outer.addView(root);

        Dialog dialog = createAceDialog(activity, outer, true);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        new Thread(() -> {
            List<CleanModels.TargetScan> scans = CleanerEngine.scanTargets(activity, pkg);

            for (CleanModels.TargetScan ts : scans) {
                if (ts.target.defaultSelected && ts.files > 0) {
                    selectedIds.add(ts.target.id);
                }
            }

            activity.runOnUiThread(() -> {
                scanBar.setIndeterminate(false);
                scanBar.setProgress(100);

                chooseButton.setEnabled(true);
                chooseButton.setAlpha(1f);

                updateSelectedSummary(scans, selectedIds, cleanAmount, cleanMeta, clean);
                chooseButton.setOnClickListener(v ->
                        showTargetPicker(
                                activity,
                                scans,
                                selectedIds,
                                () -> updateSelectedSummary(
                                        scans,
                                        selectedIds,
                                        cleanAmount,
                                        cleanMeta,
                                        clean
                                )
                        )
                );

                clean.setOnClickListener(v -> {
                    List<CleanModels.TargetScan> selected = selectedScans(scans, selectedIds);
                    if (selected.isEmpty()) return;
                    dialog.dismiss();
                    confirmClean(activity, pkg, selected);
                });
            });
        }, "ACE-target-scan").start();
    }

    private static LinearLayout buildHeader(Activity activity, String pkg) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = new TextView(activity);
        logo.setText("A");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(18);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(roundRect(BLUE, dp(activity, 13)));
        header.addView(logo, new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));

        LinearLayout titleBox = new LinearLayout(activity);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(activity, 12), 0, 0, 0);
        titleBox.addView(text(activity, "ACE 工具箱", 21, TEXT_PRIMARY, true));
        titleBox.addView(text(
                activity,
                HostPackages.QQ.equals(pkg)
                        ? "QQ 分类清理 · SSAID · Java 脚本"
                        : "微信安全清理",
                12,
                TEXT_SECONDARY,
                false
        ));
        header.addView(titleBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private static LinearLayout buildSsaidCard(Activity activity, String pkg) {
        LinearLayout ssaidCard = card(activity);

        LinearLayout ssaidHeader = new LinearLayout(activity);
        ssaidHeader.setOrientation(LinearLayout.HORIZONTAL);
        ssaidHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView ssaidTitle = text(activity, "SSAID", 15, TEXT_PRIMARY, true);
        TextView badge = text(activity, "", 11, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(activity, 9), dp(activity, 4), dp(activity, 9), dp(activity, 4));

        ssaidHeader.addView(ssaidTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ssaidHeader.addView(badge);
        ssaidCard.addView(ssaidHeader);

        TextView value = text(activity, "", 18, TEXT_PRIMARY, true);
        value.setPadding(0, dp(activity, 10), 0, dp(activity, 4));
        value.setTextIsSelectable(true);
        ssaidCard.addView(value);

        ssaidCard.addView(text(
                activity,
                "显示当前 ACE 配置；修改后需重启 QQ 才会应用",
                12,
                TEXT_SECONDARY,
                false
        ));

        boolean enabled = HostPackages.QQ.equals(pkg) && HostConfig.ssaidEnabled(activity);
        String configured = HostPackages.QQ.equals(pkg) ? HostConfig.ssaidValue(activity) : "";

        if (!HostPackages.QQ.equals(pkg)) {
            badge.setText("不适用");
            badge.setBackground(roundRect(Color.rgb(142, 142, 147), dp(activity, 12)));
            value.setText("微信不使用此功能");
        } else if (enabled) {
            badge.setText("已启用");
            badge.setBackground(roundRect(GREEN, dp(activity, 12)));
            value.setText(configured.matches("[0-9a-f]{16}")
                    ? configured
                    : "未配置有效 SSAID");
        } else {
            badge.setText("未启用");
            badge.setBackground(roundRect(Color.rgb(142, 142, 147), dp(activity, 12)));
            value.setText(configured.isEmpty() ? "未配置" : configured);
        }
        return ssaidCard;
    }


private static LinearLayout buildJavaScriptCard(Activity activity) {
    LinearLayout scriptCard = card(activity);

    LinearLayout header = new LinearLayout(activity);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);

    TextView title = text(activity, "Java 脚本", 15, TEXT_PRIMARY, true);
    TextView badge = text(activity, "", 11, Color.WHITE, true);
    badge.setGravity(Gravity.CENTER);
    badge.setPadding(dp(activity, 9), dp(activity, 4), dp(activity, 9), dp(activity, 4));

    boolean enabled = HostConfig.javaScriptEnabled(activity);
    String source = HostConfig.javaScriptSource(activity);

    if (enabled) {
        badge.setText("实验");
        badge.setBackground(roundRect(ORANGE, dp(activity, 12)));
    } else {
        badge.setText("未启用");
        badge.setBackground(roundRect(Color.rgb(142, 142, 147), dp(activity, 12)));
    }

    header.addView(title, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    header.addView(badge);
    scriptCard.addView(header);

    boolean autoRun = HostConfig.javaScriptAutoRun(activity);


    TextView summary = text(


            activity,


            (source == null || source.trim().isEmpty()


                    ? "未配置 main.java"


                    : "main.java · " + source.length() + " 字符")


                    + "\n自动执行：" + (autoRun ? "已开启（每个 QQ 进程一次）" : "关闭"),


            12,


            TEXT_SECONDARY,


            false


    );
    summary.setPadding(0, dp(activity, 7), 0, dp(activity, 6));
    scriptCard.addView(summary);

    String callbackState = "回调："
            + (HostConfig.javaScriptReceiveCallback(activity) ? "收✓ " : "收○ ")
            + (HostConfig.javaScriptSendCallback(activity) ? "发✓ " : "发○ ")
            + (HostConfig.javaScriptGroupCallback(activity) ? "群✓" : "群○");
    TextView callbacks = text(activity, callbackState, 12, TEXT_SECONDARY, false);
    callbacks.setPadding(0, 0, 0, dp(activity, 10));
    scriptCard.addView(callbacks);

    Button run = compactButton(
            activity,
            "运行脚本",
            Color.WHITE,
            enabled ? BLUE : Color.rgb(174, 174, 178)
    );
    run.setEnabled(enabled && source != null && !source.trim().isEmpty());
    run.setAlpha(run.isEnabled() ? 1f : .55f);
    run.setOnClickListener(v -> confirmRunJavaScript(activity));
    scriptCard.addView(run, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(activity, 44)
    ));

    TextView warning = text(
            activity,
            "支持可选自动运行和消息/群事件回调。只运行你自己编写或审计过的脚本。",
            11,
            ORANGE,
            false
    );
    warning.setPadding(0, dp(activity, 8), 0, 0);
    scriptCard.addView(warning);

    return scriptCard;
}

private static void confirmRunJavaScript(Activity activity) {
    AlertDialog confirm = new AlertDialog.Builder(activity)
            .setTitle("运行 Java 脚本？")
            .setMessage(
                    "脚本将在 QQ 进程中执行，拥有该进程可访问的权限。"
                            + "\n\nv2.1 支持 Java 脚本与 QQNT 消息回调；回调在独立脚本线程执行，不阻塞 QQ Hook 线程。"
                            + "\n\n请只运行你自己编写或已审计的代码。"
            )
            .setNegativeButton("取消", null)
            .setPositiveButton("运行", null)
            .create();

    confirm.setOnShowListener(d -> {
        Button run = confirm.getButton(AlertDialog.BUTTON_POSITIVE);
        if (run != null) {
            run.setTextColor(BLUE);
            run.setOnClickListener(v -> {
                run.setEnabled(false);
                run.setText("运行中…");

                new Thread(() -> {
                    HostJavaScriptEngine.RunResult result =
                            HostJavaScriptEngine.run(activity);

                    activity.runOnUiThread(() -> {
                        confirm.dismiss();

                        AlertDialog done = new AlertDialog.Builder(activity)
                                .setTitle(result.success ? "脚本执行完成" : "脚本执行失败")
                                .setMessage(result.message)
                                .setPositiveButton("知道了", null)
                                .create();
                        done.setOnShowListener(x -> {
                            Button ok = done.getButton(AlertDialog.BUTTON_POSITIVE);
                            if (ok != null) {
                                ok.setTextColor(result.success ? BLUE : ORANGE);
                            }
                        });
                        done.show();
                    });
                }, "ACE-java-script").start();
            });
        }
    });
    confirm.show();
}

    private static void showTargetPicker(
            Activity activity,
            List<CleanModels.TargetScan> scans,
            Set<String> selectedIds,
            Runnable onApplied
    ) {
        Set<String> draft = new HashSet<>(selectedIds);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 18), dp(activity, 4), dp(activity, 18), dp(activity, 8));

        String lastCategory = null;
        for (CleanModels.TargetScan ts : scans) {
            if (ts.files <= 0) continue;

            if (!ts.target.category.equals(lastCategory)) {
                if (lastCategory != null) addSpace(activity, content, 10);
                TextView category = text(
                        activity,
                        ts.target.category,
                        13,
                        BLUE,
                        true
                );
                category.setPadding(dp(activity, 2), dp(activity, 8), 0, dp(activity, 6));
                content.addView(category);
                lastCategory = ts.target.category;
            }

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, dp(activity, 5), 0, dp(activity, 5));

            CheckBox check = new CheckBox(activity);
            check.setChecked(draft.contains(ts.target.id));
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                check.setButtonTintList(new ColorStateList(
                        new int[][]{
                                new int[]{android.R.attr.state_checked},
                                new int[]{}
                        },
                        new int[]{BLUE, Color.rgb(180, 180, 185)}
                ));
            }
            check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    draft.add(ts.target.id);
                } else {
                    draft.remove(ts.target.id);
                }
            });
            row.addView(check, new LinearLayout.LayoutParams(
                    dp(activity, 42), ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout info = new LinearLayout(activity);
            info.setOrientation(LinearLayout.VERTICAL);

            LinearLayout titleLine = new LinearLayout(activity);
            titleLine.setOrientation(LinearLayout.HORIZONTAL);
            titleLine.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = text(activity, ts.target.label, 15, TEXT_PRIMARY, true);
            titleLine.addView(name, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView size = text(activity, formatBytes(ts.bytes), 13, TEXT_SECONDARY, true);
            titleLine.addView(size);

            info.addView(titleLine);

            String descText = ts.target.description;
            if (ts.target.deepClean) descText = "深度清理 · " + descText;
            TextView desc = text(
                    activity,
                    descText,
                    11,
                    ts.target.deepClean ? ORANGE : TEXT_SECONDARY,
                    false
            );
            desc.setPadding(0, dp(activity, 3), 0, 0);
            info.addView(desc);

            row.addView(info, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
            content.addView(row);

            View divider = new View(activity);
            divider.setBackgroundColor(DIVIDER);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
            dlp.setMarginStart(dp(activity, 42));
            content.addView(divider, dlp);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);

        TextView message = text(
                activity,
                "深度清理项默认不勾选。聊天记录、数据库、账号、支付和收藏主体数据不会被删除。",
                12,
                TEXT_SECONDARY,
                false
        );
        message.setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 18), dp(activity, 8));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(message);

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 440)
        );
        container.addView(scroll, slp);

        AlertDialog picker = new AlertDialog.Builder(activity)
                .setTitle("选择清理项")
                .setView(container)
                .setNegativeButton("取消", null)
                .setNeutralButton("全选安全项", null)
                .setPositiveButton("应用", null)
                .create();

        picker.setOnShowListener(d -> {
            Button apply = picker.getButton(AlertDialog.BUTTON_POSITIVE);
            Button safe = picker.getButton(AlertDialog.BUTTON_NEUTRAL);

            if (apply != null) {
                apply.setTextColor(BLUE);
                apply.setOnClickListener(v -> {
                    selectedIds.clear();
                    selectedIds.addAll(draft);
                    onApplied.run();
                    picker.dismiss();
                });
            }

            if (safe != null) {
                safe.setTextColor(BLUE);
                safe.setOnClickListener(v -> {
                    draft.clear();
                    for (CleanModels.TargetScan ts : scans) {
                        if (ts.files > 0 && !ts.target.deepClean) {
                            draft.add(ts.target.id);
                        }
                    }
                    picker.dismiss();
                    selectedIds.clear();
                    selectedIds.addAll(draft);
                    onApplied.run();
                });
            }
        });

        picker.show();
    }

    private static void updateSelectedSummary(
            List<CleanModels.TargetScan> scans,
            Set<String> selectedIds,
            TextView amount,
            TextView meta,
            Button clean
    ) {
        long bytes = 0L;
        int files = 0;
        int items = 0;
        int deep = 0;

        for (CleanModels.TargetScan ts : scans) {
            if (!selectedIds.contains(ts.target.id)) continue;
            bytes += ts.bytes;
            files += ts.files;
            items++;
            if (ts.target.deepClean) deep++;
        }

        amount.setText(formatBytes(bytes));

        if (items == 0) {
            meta.setText("尚未选择清理项");
            clean.setText("开始清理");
            clean.setEnabled(false);
            clean.setAlpha(.45f);
            return;
        }

        String deepText = deep > 0 ? " · 含 " + deep + " 个深度项" : "";
        meta.setText(items + " 个清理项 · " + files + " 个文件" + deepText
                + "\n点击「选择清理项」可逐项调整");

        clean.setText("开始清理");
        clean.setEnabled(files > 0);
        clean.setAlpha(files > 0 ? 1f : .45f);
    }

    private static List<CleanModels.TargetScan> selectedScans(
            List<CleanModels.TargetScan> scans,
            Set<String> selectedIds
    ) {
        List<CleanModels.TargetScan> out = new ArrayList<>();
        for (CleanModels.TargetScan ts : scans) {
            if (selectedIds.contains(ts.target.id) && ts.files > 0) out.add(ts);
        }
        return out;
    }


private static void confirmClean(
        Activity activity,
        String pkg,
        List<CleanModels.TargetScan> selected
) {
    long bytes = 0L;
    int files = 0;
    int deep = 0;
    for (CleanModels.TargetScan item : selected) {
        bytes += item.bytes;
        files += item.files;
        if (item.target.deepClean) deep++;
    }

    final long beforeBytes = bytes;
    final int fileCount = files;

    LinearLayout root = dialogSurface(activity);
    root.setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 18));

    // Header
    LinearLayout header = new LinearLayout(activity);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);

    TextView icon = circleIcon(activity, "✓", BLUE);
    header.addView(icon, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));

    LinearLayout titleBox = new LinearLayout(activity);
    titleBox.setOrientation(LinearLayout.VERTICAL);
    titleBox.setPadding(dp(activity, 12), 0, 0, 0);
    titleBox.addView(text(activity, "确认清理", 21, TEXT_PRIMARY, true));
    titleBox.addView(text(activity, "请确认本次清理范围", 12, TEXT_SECONDARY, false));
    header.addView(titleBox, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    root.addView(header);

    addSpace(activity, root, 16);

    LinearLayout amountCard = card(activity);
    amountCard.addView(text(activity, "预计可释放", 12, TEXT_SECONDARY, false));
    TextView amount = text(activity, formatBytes(bytes), 30, BLUE, true);
    amount.setPadding(0, dp(activity, 2), 0, dp(activity, 6));
    amountCard.addView(amount);

    String selectionSummary = selected.size() + " 个项目 · " + files + " 个文件"
            + (deep > 0 ? " · 含 " + deep + " 个深度清理项" : "");
    amountCard.addView(text(activity, selectionSummary, 13, TEXT_SECONDARY, false));
    root.addView(amountCard);

    addSpace(activity, root, 12);

    LinearLayout protectCard = card(activity);
    LinearLayout protectTitle = new LinearLayout(activity);
    protectTitle.setOrientation(LinearLayout.HORIZONTAL);
    protectTitle.setGravity(Gravity.CENTER_VERTICAL);
    TextView shield = circleIcon(activity, "✓", GREEN);
    protectTitle.addView(shield, new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)));
    TextView safeTitle = text(activity, "安全保护仍然生效", 14, TEXT_PRIMARY, true);
    safeTitle.setPadding(dp(activity, 9), 0, 0, 0);
    protectTitle.addView(safeTitle);
    protectCard.addView(protectTitle);

    TextView protectedText = text(
            activity,
            "数据库、消息记录、联系人、账号、支付、收藏主体数据不会被主动删除。",
            12,
            TEXT_SECONDARY,
            false
    );
    protectedText.setPadding(0, dp(activity, 8), 0, 0);
    protectCard.addView(protectedText);
    root.addView(protectCard);

    addSpace(activity, root, 16);

    LinearLayout actions = new LinearLayout(activity);
    actions.setOrientation(LinearLayout.HORIZONTAL);

    Button cancel = compactButton(activity, "取消", TEXT_PRIMARY, Color.rgb(240, 240, 243));
    Button confirm = compactButton(activity, "开始清理", Color.WHITE, BLUE);
    confirm.setTypeface(Typeface.DEFAULT_BOLD);

    actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(activity, 50), 1f));
    LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(0, dp(activity, 50), 1f);
    confirmLp.setMarginStart(dp(activity, 10));
    actions.addView(confirm, confirmLp);
    root.addView(actions);

    Dialog dialog = createAceDialog(activity, root, false);
    cancel.setOnClickListener(v -> dialog.dismiss());
    confirm.setOnClickListener(v -> {
        dialog.dismiss();
        doClean(activity, pkg, selected, beforeBytes, fileCount);
    });
    dialog.show();
}


private static void doClean(
        Activity activity,
        String pkg,
        List<CleanModels.TargetScan> selected,
        long beforeBytes,
        int totalFiles
) {
    LinearLayout root = dialogSurface(activity);
    root.setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 18));

    LinearLayout header = new LinearLayout(activity);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(circleIcon(activity, "↻", BLUE),
            new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));

    LinearLayout titleBox = new LinearLayout(activity);
    titleBox.setOrientation(LinearLayout.VERTICAL);
    titleBox.setPadding(dp(activity, 12), 0, 0, 0);
    titleBox.addView(text(activity, "正在清理", 20, TEXT_PRIMARY, true));
    TextView status = text(activity, "准备处理所选项目…", 12, TEXT_SECONDARY, false);
    titleBox.addView(status);
    header.addView(titleBox, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    root.addView(header);

    addSpace(activity, root, 16);

    LinearLayout progressCard = card(activity);
    TextView freed = text(activity, "已释放 0 B", 24, BLUE, true);
    progressCard.addView(freed);

    TextView count = text(activity, "0 / " + Math.max(totalFiles, 1) + " 个文件", 12, TEXT_SECONDARY, false);
    count.setPadding(0, dp(activity, 4), 0, dp(activity, 10));
    progressCard.addView(count);

    ProgressBar progress = new ProgressBar(
            activity, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(Math.max(totalFiles, 1));
    if (android.os.Build.VERSION.SDK_INT >= 21) {
        progress.setProgressTintList(ColorStateList.valueOf(BLUE));
    }
    progressCard.addView(progress, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 7)));
    root.addView(progressCard);

    TextView hint = text(
            activity,
            "清理过程中请保持 QQ 在前台，部分正在使用的缓存可能会跳过。",
            11,
            TEXT_SECONDARY,
            false
    );
    hint.setPadding(dp(activity, 2), dp(activity, 12), dp(activity, 2), 0);
    root.addView(hint);

    Dialog progressDialog = createAceDialog(activity, root, false);
    progressDialog.setCancelable(false);
    progressDialog.show();

    new Thread(() -> {
        CleanModels.Result result = CleanerEngine.cleanTargets(
                selected,
                (deleted, total, freedBytes) -> activity.runOnUiThread(() -> {
                    progress.setProgress(Math.min(deleted, progress.getMax()));
                    status.setText("正在安全处理缓存文件");
                    count.setText(deleted + " / " + total + " 个文件");
                    freed.setText("已释放 " + formatBytes(freedBytes));
                })
        );

        long afterBytes = 0L;
        for (CleanModels.TargetScan refreshed : CleanerEngine.scanTargets(activity, pkg)) {
            for (CleanModels.TargetScan old : selected) {
                if (old.target.id.equals(refreshed.target.id)) {
                    afterBytes += refreshed.bytes;
                    break;
                }
            }
        }

        HostReportBridge.submit(
                activity,
                pkg,
                beforeBytes,
                afterBytes,
                result.deletedFiles,
                result.freedBytes
        );

        final long finalAfterBytes = afterBytes;
        activity.runOnUiThread(() -> {
            progressDialog.dismiss();
            showCleanResult(activity, result, finalAfterBytes);
        });
    }, "ACE-selective-clean").start();
}

private static void showCleanResult(
        Activity activity,
        CleanModels.Result result,
        long remainingBytes
) {
    LinearLayout root = dialogSurface(activity);
    root.setPadding(dp(activity, 22), dp(activity, 22), dp(activity, 22), dp(activity, 18));

    LinearLayout successHeader = new LinearLayout(activity);
    successHeader.setOrientation(LinearLayout.VERTICAL);
    successHeader.setGravity(Gravity.CENTER_HORIZONTAL);

    TextView success = circleIcon(activity, "✓", GREEN);
    successHeader.addView(success, new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)));
    addSpace(activity, successHeader, 10);
    successHeader.addView(text(activity, "清理完成", 22, TEXT_PRIMARY, true));
    TextView freedAmount = text(activity, formatBytes(result.freedBytes), 32, BLUE, true);
    freedAmount.setGravity(Gravity.CENTER);
    freedAmount.setPadding(0, dp(activity, 4), 0, 0);
    successHeader.addView(freedAmount);
    TextView releasedLabel = text(activity, "本次释放空间", 12, TEXT_SECONDARY, false);
    releasedLabel.setGravity(Gravity.CENTER);
    successHeader.addView(releasedLabel);
    root.addView(successHeader);

    addSpace(activity, root, 16);

    LinearLayout stats = card(activity);
    stats.addView(statRow(activity, "已删除文件", result.deletedFiles + " 个", TEXT_PRIMARY));
    stats.addView(divider(activity));
    stats.addView(statRow(
            activity,
            "未能删除",
            result.failedFiles > 0 ? result.failedFiles + " 个" : "0 个",
            result.failedFiles > 0 ? ORANGE : TEXT_PRIMARY
    ));
    stats.addView(divider(activity));
    stats.addView(statRow(activity, "所选项目剩余", formatBytes(remainingBytes), TEXT_PRIMARY));
    root.addView(stats);

    TextView hint = text(
            activity,
            "QQ 运行期间可能立即重新生成少量缓存，这是正常现象。",
            11,
            TEXT_SECONDARY,
            false
    );
    hint.setPadding(dp(activity, 2), dp(activity, 12), dp(activity, 2), dp(activity, 14));
    root.addView(hint);

    Button done = compactButton(activity, "完成", Color.WHITE, BLUE);
    done.setTypeface(Typeface.DEFAULT_BOLD);
    root.addView(done, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50)));

    Dialog dialog = createAceDialog(activity, root, false);
    done.setOnClickListener(v -> dialog.dismiss());
    dialog.show();
}

    private static LinearLayout card(Activity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        card.setBackground(roundRect(CARD_BG, dp(activity, 18)));
        return card;
    }

    private static Button compactButton(
            Activity activity,
            String label,
            int textColor,
            int background
    ) {
        Button b = new Button(activity);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(textColor);
        b.setBackground(roundRect(background, dp(activity, 16)));
        return b;
    }

    private static TextView text(
            Activity activity,
            String value,
            float size,
            int color,
            boolean bold
    ) {
        TextView tv = new TextView(activity);
        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setGravity(Gravity.START);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private static GradientDrawable roundRect(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private static void addSpace(Activity activity, LinearLayout parent, int heightDp) {
        View v = new View(activity);
        parent.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, heightDp)
        ));
    }


private static Dialog createAceDialog(
        Activity activity,
        View content,
        boolean tall
) {
    Dialog dialog = new Dialog(activity);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    dialog.setContentView(content);

    Window window = dialog.getWindow();
    if (window != null) {
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(.42f);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width = Math.min(
                activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 28),
                dp(activity, 520)
        );
        lp.height = tall
                ? Math.min(
                        activity.getResources().getDisplayMetrics().heightPixels - dp(activity, 70),
                        dp(activity, 760)
                )
                : WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(lp);
    }
    return dialog;
}

private static LinearLayout dialogSurface(Activity activity) {
    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackground(roundRect(Color.WHITE, dp(activity, 26)));
    return root;
}

private static TextView circleIcon(Activity activity, String glyph, int background) {
    TextView icon = text(activity, glyph, 18, Color.WHITE, true);
    icon.setGravity(Gravity.CENTER);
    icon.setBackground(roundRect(background, dp(activity, 22)));
    return icon;
}

private static View divider(Activity activity) {
    View divider = new View(activity);
    divider.setBackgroundColor(DIVIDER);
    divider.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1)));
    return divider;
}

private static LinearLayout statRow(
        Activity activity,
        String label,
        String value,
        int valueColor
) {
    LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(activity, 8), 0, dp(activity, 8));

    TextView left = text(activity, label, 13, TEXT_SECONDARY, false);
    TextView right = text(activity, value, 14, valueColor, true);
    right.setGravity(Gravity.END);

    row.addView(left, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(right);
    return row;
}

    static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";

        double v = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = -1;
        do {
            v /= 1024.0;
            i++;
        } while (v >= 1024 && i < units.length - 1);

        return String.format(
                Locale.getDefault(),
                "%.1f %s",
                v,
                units[i]
        );
    }

    private static int dp(Activity activity, int value) {
        return (int) (
                value * activity.getResources().getDisplayMetrics().density + .5f
        );
    }

    private HostCleanerDialog() {}
}
