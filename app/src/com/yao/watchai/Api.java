package com.yao.watchai;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;

/** 网络层：语音转写（主备切换） + DeepSeek 流式对话 */
public class Api {

    /**
     * 手表屏幕只有 402×476，回答必须极短。
     * 这段是静态的，DeepSeek 会命中 prompt cache，成本可忽略。
     */
    private static final String SYSTEM_PROMPT =
            "你在手表上回答。极简：能一句话说清就不说第二句，准确优先于客套。"
          + "不用 Markdown、不用列表、不解释过程、不复述问题。中文。"
          + "需要实时信息（今天/最新/新闻/价格/天气/赛事）时先调用 web_search，再据此一句话回答。"
          + "搜索结果互相矛盾时，不要回答“无法确定”：选出最可能的一个（来源更权威、多方一致的优先）"
          + "直接给出，末尾加「（推测）」两个字。";

    /** 额度类错误：应该触发切换到备用通道 */
    public static class QuotaException extends Exception {
        public QuotaException(String m) { super(m); }
    }

    public interface TokenCallback {
        void onToken(String piece);
        void onDone();
        void onError(String msg);
        /** 过程提示，例如「正在联网搜索…」 */
        void onStatus(String s);
    }

    // ==================== 语音识别：主备路由 ====================

    /**
     * 主：硅基流动（免费）
     * 备：火山豆包录音文件识别·极速版（0.8 元/小时）
     *
     * 主通道被判定额度用尽后，本次启动（或 6 小时内）不再尝试。
     */
    public static String transcribe(Config cfg, File audio) throws Exception {
        StringBuilder err = new StringBuilder();

        boolean skipPrimary = cfg.primaryExhausted() || cfg.asrKey().isEmpty();

        if (!skipPrimary) {
            try {
                return siliconflow(cfg, audio);
            } catch (QuotaException q) {
                cfg.markPrimaryExhausted();
                err.append("主通道额度用尽，已切备用");
            } catch (Exception e) {
                String m = String.valueOf(e.getMessage());
                if (m.contains("Unable to resolve host") || m.contains("Network is unreachable")) {
                    throw e;   // 没网，换供应商也没用
                }
                err.append("主通道失败：").append(m);
            }
        }

        if (!cfg.hasVolc()) {
            throw new Exception(err.length() > 0 ? err.toString() : "未配置语音识别 Key");
        }

        try {
            return volc(cfg, audio);
        } catch (Exception e) {
            throw new Exception(err + "；备用失败：" + e.getMessage());
        }
    }

    // ---------------- 硅基流动 ----------------

    private static String siliconflow(Config cfg, File audio) throws Exception {
        String boundary = "----watchai" + System.currentTimeMillis();
        URL url = new URL(cfg.asrUrl() + "/audio/transcriptions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + cfg.asrKey());
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
        writeField(out, boundary, "model", cfg.asrModel());
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"a.m4a\"\r\n");
        out.writeBytes("Content-Type: audio/mp4\r\n\r\n");
        FileInputStream fis = new FileInputStream(audio);
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) > 0) out.write(buf, 0, n);
        fis.close();
        out.writeBytes("\r\n--" + boundary + "--\r\n");
        out.flush();
        out.close();

        int code = conn.getResponseCode();
        String body = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (isQuota(code, body)) throw new QuotaException("HTTP " + code + " " + body);
        if (isAuthError(code, body)) throw new Exception("语音 Key 无效或未实名（" + code + "）");
        if (code != 200) throw new Exception("ASR " + code + ": " + body);

        JSONObject j = new JSONObject(body);
        return j.optString("text", "").trim();
    }

    private static void writeField(DataOutputStream out, String boundary, String name, String val) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(val.getBytes("UTF-8"));
        out.writeBytes("\r\n");
    }

    // ---------------- 火山豆包：录音文件识别·极速版 ----------------

    /**
     * 同步单次调用，音频用 base64 直传，不需要公网 URL。
     * 结果通过响应头 X-Api-Status-Code 判断，20000000 表示成功。
     */
    private static String volc(Config cfg, File audio) throws Exception {
        byte[] bytes = readFile(audio);

        JSONObject audioNode = new JSONObject();
        audioNode.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
        audioNode.put("format", "mp4");     // MediaRecorder 输出 MPEG_4 容器
        audioNode.put("rate", 16000);
        audioNode.put("bits", 16);
        audioNode.put("channel", 1);

        JSONObject reqNode = new JSONObject();
        reqNode.put("model_name", "bigmodel");
        reqNode.put("enable_itn", true);
        reqNode.put("enable_punc", true);

        JSONObject user = new JSONObject();
        user.put("uid", "watchai");

        JSONObject body = new JSONObject();
        body.put("user", user);
        body.put("audio", audioNode);
        body.put("request", reqNode);

        URL url = new URL(cfg.volcUrl() + "/recognize/flash");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        // 新版控制台用单个 X-Api-Key；旧版用 APP ID + Access Token
        if (!cfg.volcKey().isEmpty()) {
            conn.setRequestProperty("X-Api-Key", cfg.volcKey());
        } else {
            conn.setRequestProperty("X-Api-App-Key", cfg.volcAppKey());
            conn.setRequestProperty("X-Api-Access-Key", cfg.volcAccess());
        }
        conn.setRequestProperty("X-Api-Resource-Id", cfg.volcResourceId());
        conn.setRequestProperty("X-Api-Request-Id", UUID.randomUUID().toString());
        conn.setRequestProperty("X-Api-Sequence", "-1");

        byte[] payload = body.toString().getBytes("UTF-8");
        conn.setFixedLengthStreamingMode(payload.length);
        conn.getOutputStream().write(payload);

        int code = conn.getResponseCode();
        String statusCode = conn.getHeaderField("X-Api-Status-Code");
        String body2 = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (isQuota(code, body2)) throw new QuotaException("HTTP " + code + " " + body2);
        if (isAuthError(code, body2)) throw new Exception("豆包 Key 无效（" + code + "）");
        if (code != 200) throw new Exception("豆包 " + code + ": " + body2);
        if (!"20000000".equals(statusCode))
            throw new Exception("豆包状态 " + statusCode + ": " + body2);

        JSONObject j = new JSONObject(body2);
        JSONObject result = j.optJSONObject("result");
        return result == null ? "" : result.optString("text", "").trim();
    }

    private static byte[] readFile(File f) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(f);
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
        fis.close();
        return bos.toByteArray();
    }

    // ---------------- 额度判定 ----------------

    /**
     * 命中即认为额度用尽 / 限流，应切换到备用通道。
     *
     * 注意 403 不能无脑当成额度问题：key 填错、未实名、没开权限也返回 403。
     * 把它们混为一谈会让断路器被误触发，之后所有请求都跳过主通道。
     * 所以 403 必须在响应体里看到额度类字样才算。
     */
    private static boolean isQuota(int code, String body) {
        String b = body == null ? "" : body.toLowerCase();
        if (code == 402 || code == 429) return true;
        return b.contains("quota") || b.contains("insufficient") || b.contains("exceed")
                || b.contains("rate limit") || b.contains("ratelimit")
                || b.contains("额度") || b.contains("余额") || b.contains("限流");
    }

    /** 鉴权类错误：key 无效 / 未实名 / 无权限 —— 不该触发断路器 */
    private static boolean isAuthError(int code, String body) {
        if (code == 401) return true;
        String b = body == null ? "" : body.toLowerCase();
        return b.contains("token is invalid") || b.contains("unauthorized")
            || b.contains("invalid api key") || b.contains("authentication");
    }

    // ==================== DeepSeek 流式对话 ====================

    /** 联网搜索工具定义（OpenAI 兼容格式） */
    private static JSONArray searchTools() throws Exception {
        JSONObject params = new JSONObject();
        params.put("type", "object");
        JSONObject props = new JSONObject();
        JSONObject q = new JSONObject();
        q.put("type", "string");
        q.put("description", "搜索关键词");
        props.put("query", q);
        params.put("properties", props);
        params.put("required", new JSONArray().put("query"));

        JSONObject fn = new JSONObject();
        fn.put("name", "web_search");
        fn.put("description", "搜索实时网络信息。涉及今天、现在、最新、新闻、股价、天气、赛事等需要实时数据的问题时调用。");
        fn.put("parameters", params);

        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        tool.put("function", fn);
        return new JSONArray().put(tool);
    }

    public static void chat(Config cfg, List<String[]> history, TokenCallback cb) {
        try {
            JSONArray msgs = new JSONArray();

            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", SYSTEM_PROMPT);
            msgs.put(sys);

            for (String[] m : history) {
                JSONObject o = new JSONObject();
                o.put("role", m[0]);
                o.put("content", m[1]);
                msgs.put(o);
            }

            round(cfg, msgs, true, cb);
        } catch (Exception e) {
            cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 一轮请求。开工具时模型可能返回 tool_calls，
     * 此时执行搜索、把结果塞回对话，再来一轮（这轮不开工具，强制出最终答案）。
     */
    private static void round(Config cfg, JSONArray msgs, boolean allowTools, TokenCallback cb) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(cfg.dsUrl() + "/chat/completions");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + cfg.deepseekKey());

            JSONObject req = new JSONObject();
            req.put("model", cfg.dsModel());
            req.put("stream", true);
            req.put("messages", msgs);
            req.put("max_tokens", 512);
            if (allowTools) {
                req.put("tools", searchTools());
                req.put("tool_choice", "auto");
            }

            byte[] payload = req.toString().getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(payload.length);
            conn.getOutputStream().write(payload);

            int code = conn.getResponseCode();
            if (code != 200) {
                cb.onError("HTTP " + code + ": " + readAll(conn.getErrorStream()));
                return;
            }

            // 流式读取；同时累积 tool_calls 分片
            final java.util.LinkedHashMap<Integer, JSONObject> calls = new java.util.LinkedHashMap<>();
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.equals("[DONE]")) break;
                try {
                    JSONObject j = new JSONObject(data);
                    JSONArray ch = j.optJSONArray("choices");
                    if (ch == null || ch.length() == 0) continue;
                    JSONObject delta = ch.getJSONObject(0).optJSONObject("delta");
                    if (delta == null) continue;

                    String piece = delta.optString("content", "");
                    if (!piece.isEmpty()) cb.onToken(piece);

                    JSONArray tcs = delta.optJSONArray("tool_calls");
                    if (tcs != null) {
                        for (int i = 0; i < tcs.length(); i++) {
                            JSONObject t = tcs.getJSONObject(i);
                            int idx = t.optInt("index", 0);
                            JSONObject acc = calls.get(idx);
                            if (acc == null) {
                                acc = new JSONObject();
                                acc.put("id", "");
                                acc.put("name", "");
                                acc.put("args", "");
                                calls.put(idx, acc);
                            }
                            if (t.has("id")) acc.put("id", t.optString("id", ""));
                            JSONObject fn = t.optJSONObject("function");
                            if (fn != null) {
                                if (fn.has("name")) acc.put("name", fn.optString("name", ""));
                                if (fn.has("arguments"))
                                    acc.put("args", acc.optString("args", "") + fn.optString("arguments", ""));
                            }
                        }
                    }
                } catch (Exception ignore) { /* 单行解析失败不中断 */ }
            }
            r.close();

            if (!allowTools || calls.isEmpty()) {
                cb.onDone();
                return;
            }

            // ---- 有工具调用：执行搜索，把结果回灌 ----
            JSONArray toolCalls = new JSONArray();
            JSONArray toolMsgs = new JSONArray();

            for (JSONObject acc : calls.values()) {
                if (!"web_search".equals(acc.optString("name"))) continue;

                String query = "";
                try {
                    query = new JSONObject(acc.optString("args", "{}")).optString("query", "");
                } catch (Exception ignore) {}

                if (query.isEmpty()) continue;

                cb.onStatus("正在联网搜索…");
                String result;
                try {
                    result = webSearch(cfg, query);
                } catch (Exception e) {
                    result = "{\"error\":\"" + e.getMessage() + "\"}";
                }

                JSONObject call = new JSONObject();
                call.put("id", acc.optString("id"));
                call.put("type", "function");
                JSONObject fn = new JSONObject();
                fn.put("name", "web_search");
                fn.put("arguments", acc.optString("args", "{}"));
                call.put("function", fn);
                toolCalls.put(call);

                JSONObject tm = new JSONObject();
                tm.put("role", "tool");
                tm.put("tool_call_id", acc.optString("id"));
                tm.put("content", result);
                toolMsgs.put(tm);
            }

            if (toolCalls.length() == 0 || toolMsgs.length() == 0) {
                cb.onDone();
                return;
            }

            JSONObject assistant = new JSONObject();
            assistant.put("role", "assistant");
            assistant.put("content", "");
            assistant.put("tool_calls", toolCalls);
            msgs.put(assistant);
            for (int i = 0; i < toolMsgs.length(); i++) msgs.put(toolMsgs.get(i));

            round(cfg, msgs, false, cb);   // 第二轮：不开工具，出最终答案
        } catch (Exception e) {
            cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** DeepSeek 官方搜索：仅取摘要，不取全文，省 token */
    private static String webSearch(Config cfg, String query) throws Exception {
        URL url = new URL(cfg.dsUrl() + "/v1/tools/search");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + cfg.deepseekKey());

        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("count", 5);
        body.put("need_content", false);

        byte[] payload = body.toString().getBytes("UTF-8");
        conn.setFixedLengthStreamingMode(payload.length);
        conn.getOutputStream().write(payload);

        int code = conn.getResponseCode();
        String resp = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code != 200) throw new Exception("搜索 " + code + ": " + resp);

        JSONObject j = new JSONObject(resp);
        JSONArray results = j.optJSONArray("results");
        JSONArray out = new JSONArray();
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.optJSONObject(i);
                if (r == null) continue;
                JSONObject o = new JSONObject();
                o.put("title", r.optString("title", ""));
                o.put("summary", r.optString("summary", ""));
                o.put("url", r.optString("url", ""));
                out.put(o);
            }
        }
        return out.toString();
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) bos.write(b, 0, n);
        in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }
}
