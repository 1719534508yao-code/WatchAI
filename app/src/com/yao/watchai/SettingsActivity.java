package com.yao.watchai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private static final int BG = 0xFF0B0B0F;
    private static final int FIELD_BG = 0xFF1C1C22;
    private static final int DIM = 0xFF808090;

    private Config cfg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new Config(this);
        setContentView(build());
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private android.view.View build() {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView h = new TextView(this);
        h.setText("设置");
        h.setTextColor(Color.WHITE);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(h);

        final EditText ds = field("DeepSeek API Key", cfg.deepseekKey(), false);
        final EditText asr = field("硅基流动 Key（主通道，免费）", cfg.asrKey(), false);
        final EditText volc = field("豆包 Key（备用，额度用尽时自动切）", cfg.volcKey(), false);
        final EditText model = field("模型", cfg.dsModel(), false);
        for (EditText e : new EditText[]{ds, asr, volc, model}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            root.addView(e, lp);
        }

        Button save = new Button(this);
        save.setText("保存");
        save.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        save.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                cfg.put(Config.KEY_DS_KEY, ds.getText().toString());
                cfg.put(Config.KEY_ASR_KEY, asr.getText().toString());
                cfg.put(Config.KEY_VOLC_KEY, volc.getText().toString());
                cfg.put(Config.KEY_DS_MODEL, model.getText().toString());
                cfg.clearExhausted();   // 改了 Key 就把断路器重置
                finish();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        root.addView(save, lp);

        TextView hint = new TextView(this);
        hint.setText("语音：硅基流动（免费）优先，额度用尽自动切豆包录音文件识别。\n"
                   + "硅基流动 key: cloud.siliconflow.cn\n"
                   + "豆包 key: console.volcengine.com/speech\n"
                   + "DeepSeek key: platform.deepseek.com");
        hint.setTextColor(DIM);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setPadding(0, dp(8), 0, 0);
        root.addView(hint);

        sc.addView(root);
        return sc;
    }

    private EditText field(String label, String value, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(label);
        e.setText(value);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(DIM);
        e.setBackgroundColor(FIELD_BG);
        e.setPadding(dp(8), dp(6), dp(8), dp(6));
        e.setSingleLine(!multiline);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        return e;
    }
}
