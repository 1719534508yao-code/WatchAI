package com.yao.watchai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** 查看上一次会话（只读）。更早的会话已在启动时被覆盖删除。 */
public class HistoryActivity extends Activity {

    private static final int BG        = 0xFF0B0B0F;
    private static final int BUBBLE_AI = 0xE01C1C24;
    private static final int BUBBLE_ME = 0xF24E6AB1;
    private static final int TEXT_AI   = 0xFFFFFFFF;
    private static final int TEXT_ME   = 0xFFFFFFFF;
    private static final int DIM       = 0xFFB8BED0;
    private static final int SCRIM     = 0xA6000000;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(build());
    }

    private static void applyBackground(View root) {
        int id = root.getResources().getIdentifier("chatbg", "drawable", root.getContext().getPackageName());
        if (id == 0) return;
        android.graphics.drawable.Drawable img = root.getResources().getDrawable(id);
        root.setBackground(new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{
                        img, new android.graphics.drawable.ColorDrawable(SCRIM)}));
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        applyBackground(root);
        root.setPadding(dp(6), dp(20), dp(6), dp(6));

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextColor(DIM);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        back.setPadding(dp(2), dp(2), dp(10), dp(2));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText("上一次会话");
        title.setTextColor(DIM);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 内容
        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        List<String[]> msgs = new SessionStore(this).previousSession();
        if (msgs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("没有上一次会话");
            empty.setTextColor(DIM);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, 0);
            list.addView(empty);
        } else {
            for (String[] m : msgs) addBubble(list, m[0], m[1]);
        }

        sc.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private void addBubble(LinearLayout list, String who, String text) {
        boolean me = "me".equals(who) || "user".equals(who);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, me ? 14 : 22);
        tv.setTextColor(me ? TEXT_ME : TEXT_AI);
        tv.setLineSpacing(dp(me ? 2 : 4), 1f);
        tv.setPadding(dp(10), dp(7), dp(10), dp(7));

        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(12));
        d.setColor(me ? BUBBLE_ME : BUBBLE_AI);
        tv.setBackground(d);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(me ? Gravity.END : Gravity.START);
        row.setPadding(0, dp(3), 0, dp(3));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = 0.86f;
        row.addView(tv, lp);
        list.addView(row);
    }
}
