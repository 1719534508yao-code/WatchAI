package com.yao.watchai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_MIC = 1;

    private static final int BG        = 0xFF0B0B0F;
    private static final int BUBBLE_AI = 0xE01C1C24;   // 半透明，让背景图透出来一点
    private static final int BUBBLE_ME = 0xF24E6AB1;   // 取自立绘的深靛蓝
    private static final int TEXT_AI   = 0xFFFFFFFF;
    private static final int TEXT_ME   = 0xFFFFFFFF;
    private static final int DIM       = 0xFFB8BED0;
    private static final int ACCENT    = 0xFF4E6AB1;
    /** 背景图上的黑色蒙层，压暗以保证文字对比度 */
    private static final int SCRIM      = 0xB8000000;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout list;
    private ScrollView scroller;
    private TextView status;
    private TextView talkBtn;
    private TextView lastAiBubble;   // 流式输出时追加到这里

    private final List<String[]> history = new ArrayList<>();  // [role, text]，仅本次会话
    private Config cfg;
    private SessionStore sessions;

    private MediaRecorder recorder;
    private File audioFile;
    private boolean recording;
    private android.os.Vibrator vibrator;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        cfg = new Config(this);
        vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        applyIntentConfig(getIntent());

        // 每次启动 = 新会话：上一轮存档顶替为「上一次」，更早的删除
        // history 本身是内存中的，启动即为空，不会把旧对话带进上下文
        sessions = new SessionStore(this);
        sessions.beginNewSession();

        setContentView(buildUi());
        handleExtras(getIntent());
    }

    /**
     * adb 下发的所有指令都在这里处理。onCreate 和 onNewIntent 都要走，
     * 否则应用已经在前台时下发的参数会被忽略。
     *
     *   -e deepseek_key sk-...  设置对话 Key
     *   -e asr_key sk-...       设置语音 Key
     *   -e volc_key ...         设置豆包 Key
     *   -e reset 1              重置额度断路器
     *   -e dump 1               在状态行显示当前配置摘要
     *   -e debug_audio /path    用指定音频文件跑一遍「识别 → 对话」
     */
    private void handleExtras(Intent it) {
        if (it == null) return;
        applyIntentConfig(it);

        if (it.hasExtra("reset")) {
            cfg.clearExhausted();
            status.setText("断路器已重置");
        }

        String debugAudio = it.getStringExtra("debug_audio");
        if (debugAudio != null) runDebugAudio(new File(debugAudio));

        String debugText = it.getStringExtra("debug_text");
        if (debugText != null) onUserText(debugText);

        if (it.hasExtra("dump")) {
            status.setText("ds=" + cfg.deepseekKey().length()
                    + " asr=" + cfg.asrKey().length()
                    + " volc=" + cfg.volcKey().length()
                    + " exhausted=" + cfg.primaryExhausted()
                    + " model=" + cfg.asrModel());
        }
    }

    @Override
    protected void onNewIntent(Intent it) {
        super.onNewIntent(it);
        setIntent(it);
        handleExtras(it);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cfg.ready()) {
            if (status.getText().toString().startsWith("请先")) status.setText("按住说话");
        } else {
            status.setText("请先点右上角 ⚙ 填写 API Key");
        }
    }

    /** 调试：跳过录音，直接把指定音频文件走一遍「识别 → 对话」 */
    private void runDebugAudio(final File f) {
        if (!cfg.ready()) { status.setText("未配置 Key"); return; }
        status.setText("调试：识别中…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    String text = Api.transcribe(cfg, f);
                    if (text == null || text.isEmpty()) { setStatus("识别为空"); return; }
                    onUserText(text);
                } catch (Exception e) {
                    setStatus("调试识别失败：" + shortMsg(e.getMessage()));
                }
            }
        }).start();
    }

    /**
     * 允许通过 adb 直接下发 Key，省去在表盘上敲长串：
     *   adb shell am start -n com.yao.watchai/.MainActivity \
     *       -e deepseek_key sk-xxx -e asr_key sk-yyy
     */
    private void applyIntentConfig(Intent it) {
        if (it == null) return;
        putIf(it, "deepseek_key", Config.KEY_DS_KEY);
        putIf(it, "asr_key",      Config.KEY_ASR_KEY);
        putIf(it, "volc_key",     Config.KEY_VOLC_KEY);
        putIf(it, "volc_appid",   Config.KEY_VOLC_APP_KEY);
        putIf(it, "volc_access",  Config.KEY_VOLC_ACCESS);
        putIf(it, "model",        Config.KEY_DS_MODEL);
    }

    private void putIf(Intent it, String extra, String key) {
        String v = it.getStringExtra(extra);
        if (v != null && !v.isEmpty()) cfg.put(key, v);
    }

    // ---------------- UI ----------------

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        applyBackground(root);
        root.setPadding(dp(6), dp(20), dp(6), dp(6));

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        // 左上角：查看上一次会话
        TextView historyBtn = new TextView(this);
        historyBtn.setText("☰");
        historyBtn.setTextColor(DIM);
        historyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        historyBtn.setPadding(dp(3), dp(2), dp(10), dp(2));
        historyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });
        bar.addView(historyBtn);

        TextView title = new TextView(this);
        title.setText("AI");
        title.setTextColor(DIM);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextColor(DIM);
        gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        gear.setPadding(dp(8), dp(2), dp(4), dp(2));
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        bar.addView(gear);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 对话区
        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 状态行
        status = new TextView(this);
        status.setText("按住说话");
        status.setTextColor(DIM);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(2), 0, dp(4));
        // 状态行压在背景图上，加投影保证可读
        status.setShadowLayer(dp(3), 0, dp(1), 0xFF000000);
        root.addView(status);

        // 说话键
        talkBtn = new TextView(this);
        talkBtn.setText("按住\n说话");
        talkBtn.setTextColor(Color.WHITE);
        talkBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        talkBtn.setTypeface(Typeface.DEFAULT_BOLD);
        talkBtn.setGravity(Gravity.CENTER);
        talkBtn.setBackground(circle(ACCENT));
        talkBtn.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        // 按下立刻给反馈：震一下 + 按钮缩一下，不等录音器初始化
                        buzz(30);
                        v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(70).start();
                        startRecording();
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                        stopRecordingAndSend();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                        cancelRecording();
                        return true;
                }
                return false;
            }
        });

        LinearLayout btnWrap = new LinearLayout(this);
        btnWrap.setGravity(Gravity.CENTER);
        int size = dp(120);
        btnWrap.addView(talkBtn, new LinearLayout.LayoutParams(size, size));
        root.addView(btnWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return root;
    }

    /** 背景图 + 黑色蒙层。图片按屏幕比例预裁好，不会有黑边 */
    private static void applyBackground(android.view.View root) {
        int id = root.getResources().getIdentifier("chatbg", "drawable", root.getContext().getPackageName());
        if (id == 0) return;   // 资源缺失就退回纯色
        Drawable img = root.getResources().getDrawable(id);
        root.setBackground(new LayerDrawable(new Drawable[]{img, new ColorDrawable(SCRIM)}));
    }

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private void addBubble(String who, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        // 自己说的缩小、AI 的回答放大：屏幕小，重点在回答
        boolean me = "me".equals(who);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, me ? 14 : 22);
        tv.setTextColor(me ? TEXT_ME : TEXT_AI);
        tv.setLineSpacing(dp(me ? 2 : 5), 1f);
        tv.setPadding(dp(10), dp(7), dp(10), dp(7));

        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(12));
        d.setColor("me".equals(who) ? BUBBLE_ME : BUBBLE_AI);
        tv.setBackground(d);

        LinearLayout row = new LinearLayout(this);
        row.setGravity("me".equals(who) ? Gravity.END : Gravity.START);
        row.setPadding(0, dp(3), 0, dp(3));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = 0.86f;
        row.addView(tv, lp);

        list.addView(row);
        scrollDown();
        if ("ai".equals(who)) lastAiBubble = tv;
    }

    private void scrollDown() {
        scroller.post(new Runnable() {
            public void run() { scroller.fullScroll(View.FOCUS_DOWN); }
        });
    }

    // ---------------- 录音 ----------------

    private void startRecording() {
        if (!cfg.ready()) {
            status.setText("请先点右上角 ⚙ 填写 API Key");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        try {
            audioFile = new File(getCacheDir(), "q.m4a");
            if (audioFile.exists()) audioFile.delete();

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioChannels(1);
            recorder.setAudioEncodingBitRate(32000);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;

            talkBtn.setText("松开发送");
            talkBtn.setBackground(circle(0xFFD32F2F));
            status.setText("正在听…");
        } catch (Exception e) {
            recording = false;
            releaseRecorder();
            buzz(150);
            status.setText("录音失败：" + e.getMessage());
        }
    }

    private void cancelRecording() {
        if (!recording) return;
        recording = false;
        releaseRecorder();
        resetTalkBtn();
        status.setText("已取消");
    }

    private void stopRecordingAndSend() {
        if (!recording) return;
        recording = false;
        releaseRecorder();
        resetTalkBtn();

        if (audioFile == null || !audioFile.exists() || audioFile.length() < 1200) {
            status.setText("太短了，再按一次");
            return;
        }
        status.setText("识别中…");

        new Thread(new Runnable() {
            public void run() {
                try {
                    String text = Api.transcribe(cfg, audioFile);
                    if (text.isEmpty()) {
                        setStatus("没听清，再说一次");
                        return;
                    }
                    onUserText(text);
                } catch (Exception e) {
                    setStatus("识别失败：" + shortMsg(e.getMessage()));
                }
            }
        }).start();
    }

    private void releaseRecorder() {
        try { if (recorder != null) { recorder.stop(); } } catch (Exception ignore) {}
        try { if (recorder != null) { recorder.release(); } } catch (Exception ignore) {}
        recorder = null;
    }

    private void resetTalkBtn() {
        talkBtn.setText("按住\n说话");
        talkBtn.setBackground(circle(ACCENT));
    }

    /** 轻微震动反馈 */
    private void buzz(int ms) {
        try {
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                        ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Exception ignore) { /* 没马达就算了 */ }
    }

    // ---------------- 对话 ----------------

    private void onUserText(String text) {
        ui.post(new Runnable() {
            public void run() {
                addBubble("me", text);
                history.add(new String[]{"user", text});
                sessions.append("user", text);
                status.setText("思考中…");
                // 预建一个空 AI 气泡，流式往里追加
                addBubble("ai", "");
                final TextView target = lastAiBubble;
                final StringBuilder acc = new StringBuilder();

                new Thread(new Runnable() {
                    public void run() {
                        List<String[]> send = trimmedHistory();
                        Api.chat(cfg, send, new Api.TokenCallback() {
                            public void onToken(String piece) {
                                acc.append(piece);
                                ui.post(new Runnable() {
                                    public void run() {
                                        target.setText(acc.toString());
                                        scrollDown();
                                    }
                                });
                            }
                            public void onDone() {
                                ui.post(new Runnable() {
                                    public void run() {
                                        history.add(new String[]{"assistant", acc.toString()});
                                        sessions.append("assistant", acc.toString());
                                        status.setText("按住说话");
                                    }
                                });
                            }
                            public void onError(String msg) {
                                ui.post(new Runnable() {
                                    public void run() {
                                        target.setText("出错了：" + shortMsg(msg));
                                        status.setText("按住说话");
                                    }
                                });
                            }
                            public void onStatus(String s) {
                                setStatus(s);
                            }
                        });
                    }
                }).start();
            }
        });
    }

    /** 只带最近若干轮，控制 token 成本 */
    private List<String[]> trimmedHistory() {
        List<String[]> out = new ArrayList<>();
        int start = Math.max(0, history.size() - 8);
        for (int i = start; i < history.size(); i++) out.add(history.get(i));
        return out;
    }

    private void setStatus(final String s) {
        ui.post(new Runnable() {
            public void run() { status.setText(s); }
        });
    }

    private static String shortMsg(String s) {
        if (s == null) return "未知错误";
        String low = s.toLowerCase();
        if (low.contains("unable to resolve host") || low.contains("unknownhost"))
            return "手表没有网络";
        if (low.contains("timeout") || low.contains("timed out"))
            return "网络超时";
        if (low.contains("network is unreachable"))
            return "手表没有网络";
        s = s.replace("\n", " ");
        return s.length() > 42 ? s.substring(0, 42) + "…" : s;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_MIC && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            status.setText("按住说话");
        }
    }
}
