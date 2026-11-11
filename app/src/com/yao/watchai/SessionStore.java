package com.yao.watchai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话存档。
 *
 * 规则：
 *   - 每次启动应用调用 beginNewSession()，把上一轮存档顶替成 current，更早的全部丢弃
 *   - 所以磁盘上永远最多只有一个更早的会话（session_last.json）+ 当前会话（session_current.json）
 *   - 存档只做记录，**不会**被读回来当作 AI 上下文
 */
public class SessionStore {

    private static final String CUR = "session_current.json";
    private static final String LAST = "session_last.json";

    private final File cur;
    private final File last;

    public SessionStore(Context c) {
        cur = new File(c.getFilesDir(), CUR);
        last = new File(c.getFilesDir(), LAST);
    }

    /** 启动时调用：把上一次的当前会话变成"上一次会话"，更早的直接删除 */
    public void beginNewSession() {
        if (cur.exists()) {
            //noinspection ResultOfMethodCallIgnored
            cur.renameTo(last);   // 覆盖旧的 last，即"之前的全部删掉"
        }
    }

    /** 追加一条消息到当前会话存档 */
    public void append(String role, String text) {
        try {
            List<String[]> all = read(cur);
            all.add(new String[]{role, text});
            write(cur, all);
        } catch (Exception ignore) { /* 存档失败不影响对话 */ }
    }

    /** 读上一次会话（目前只用于以后做历史浏览，不参与上下文） */
    public List<String[]> previousSession() {
        try { return read(last); } catch (Exception e) { return new ArrayList<>(); }
    }

    private List<String[]> read(File f) throws Exception {
        List<String[]> out = new ArrayList<>();
        if (!f.exists()) return out;
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        JSONArray arr = new JSONArray(sb.toString());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(new String[]{o.optString("role"), o.optString("text")});
        }
        return out;
    }

    private void write(File f, List<String[]> all) throws Exception {
        JSONArray arr = new JSONArray();
        for (String[] m : all) {
            JSONObject o = new JSONObject();
            o.put("role", m[0]);
            o.put("text", m[1]);
            arr.put(o);
        }
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(arr.toString().getBytes("UTF-8"));
        fos.close();
    }
}
