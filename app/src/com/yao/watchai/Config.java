package com.yao.watchai;

import android.content.Context;
import android.content.SharedPreferences;

/** 配置读写（SharedPreferences） */
public class Config {

    private static final String PREFS = "watchai";

    public static final String KEY_DS_KEY   = "deepseek_key";
    public static final String KEY_DS_URL   = "deepseek_url";
    public static final String KEY_DS_MODEL = "deepseek_model";
    public static final String KEY_ASR_KEY  = "asr_key";
    public static final String KEY_ASR_URL  = "asr_url";
    public static final String KEY_ASR_MODEL= "asr_model";

    public static final String KEY_VOLC_KEY      = "volc_key";        // 新版控制台
    public static final String KEY_VOLC_APP_KEY  = "volc_app_key";    // 旧版控制台 APP ID
    public static final String KEY_VOLC_ACCESS   = "volc_access_key"; // 旧版控制台 Access Token
    public static final String KEY_VOLC_RESID    = "volc_resource_id";
    public static final String KEY_VOLC_URL      = "volc_url";

    /** 主通道被判定额度用尽的时间戳；0 表示正常 */
    public static final String KEY_EXHAUSTED_AT = "asr_exhausted_at";

    public static final String DEF_DS_URL    = "https://api.deepseek.com";
    public static final String DEF_DS_MODEL  = "deepseek-chat";
    public static final String DEF_ASR_URL   = "https://api.siliconflow.cn/v1";
    /**
     * SenseVoiceSmall 在 2026-11 实测返回 HTTP 500（模型列表里还有，但调用失败）。
     * 换成同样免费的 XingChenASR-V3.2，实测 8 秒音频往返约 1.4 秒，标点完整。
     */
    public static final String DEF_ASR_MODEL = "XingChenAGI/XingChenASR-V3.2";
    public static final String DEF_VOLC_URL  = "https://openspeech.bytedance.com/api/v3/auc/bigmodel";
    public static final String DEF_VOLC_RESID = "volc.bigasr.auc_turbo";

    /** 断路器恢复时间：6 小时 */
    private static final long EXHAUSTED_TTL_MS = 6 * 60 * 60 * 1000L;

    private final SharedPreferences p;

    public Config(Context c) {
        p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String deepseekKey() { return p.getString(KEY_DS_KEY, "").trim(); }
    public String asrKey()      { return p.getString(KEY_ASR_KEY, "").trim(); }
    public String volcKey()     { return p.getString(KEY_VOLC_KEY, "").trim(); }
    public String volcAppKey()  { return p.getString(KEY_VOLC_APP_KEY, "").trim(); }
    public String volcAccess()  { return p.getString(KEY_VOLC_ACCESS, "").trim(); }

    /** 豆包是否可用：新版单 Key，或旧版 APPID + AccessToken */
    public boolean hasVolc() {
        if (!volcKey().isEmpty()) return true;
        return !volcAppKey().isEmpty() && !volcAccess().isEmpty();
    }

    public String dsUrl()   { return nz(p.getString(KEY_DS_URL, DEF_DS_URL), DEF_DS_URL); }
    public String dsModel() { return nz(p.getString(KEY_DS_MODEL, DEF_DS_MODEL), DEF_DS_MODEL); }
    public String asrUrl()  { return nz(p.getString(KEY_ASR_URL, DEF_ASR_URL), DEF_ASR_URL); }
    public String asrModel(){ return nz(p.getString(KEY_ASR_MODEL, DEF_ASR_MODEL), DEF_ASR_MODEL); }
    public String volcUrl() { return nz(p.getString(KEY_VOLC_URL, DEF_VOLC_URL), DEF_VOLC_URL); }
    public String volcResourceId() {
        return nz(p.getString(KEY_VOLC_RESID, DEF_VOLC_RESID), DEF_VOLC_RESID);
    }

    /** 语音识别 Key：没单独配就用 DeepSeek 的（虽然多半不通，但至少不会崩） */
    public String effAsrKey() {
        String k = asrKey();
        return k.isEmpty() ? deepseekKey() : k;
    }

    public boolean ready() { return !deepseekKey().isEmpty(); }

    // ---------------- 断路器 ----------------

    /** 主通道（硅基流动）当前是否被判定额度用尽 */
    public boolean primaryExhausted() {
        long at = p.getLong(KEY_EXHAUSTED_AT, 0L);
        if (at == 0L) return false;
        if (System.currentTimeMillis() - at > EXHAUSTED_TTL_MS) {
            clearExhausted();
            return false;
        }
        return true;
    }

    public void markPrimaryExhausted() {
        p.edit().putLong(KEY_EXHAUSTED_AT, System.currentTimeMillis()).apply();
    }

    public void clearExhausted() {
        p.edit().remove(KEY_EXHAUSTED_AT).apply();
    }

    public void put(String k, String v) { p.edit().putString(k, v).apply(); }

    private static String nz(String v, String def) {
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }
}
