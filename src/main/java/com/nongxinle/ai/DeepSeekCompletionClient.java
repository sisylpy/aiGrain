package com.nongxinle.ai;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 非流式 DeepSeek Chat Completions 调用，供 AI 添加商品等独立编排复用（与应用 {@code ai.deepseek.*} 配置同源）。
 */
@Slf4j
@Component
public class DeepSeekCompletionClient {

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.deepseek.base-url}")
    private String baseUrl;

    @Value("${ai.deepseek.model}")
    private String model;

    @Value("${ai.deepseek.max-tokens:2000}")
    private int maxTokens;

    @Value("${ai.deepseek.temperature:0.7}")
    private double defaultTemperature;

    /**
     * @param phase 日志阶段标识
     * @return 模型正文；失败时返回以「抱歉」开头的短句，便于调用方识别非 JSON
     */
    public String complete(List<Map<String, String>> messages, String phase, Double temperatureOverride) {
        return complete(messages, phase, temperatureOverride, null);
    }

    /**
     * @param maxTokensOverride 非空时覆盖全局 {@code max-tokens}（如添加商品短 JSON）
     */
    public String complete(List<Map<String, String>> messages, String phase, Double temperatureOverride,
                           Integer maxTokensOverride) {
        double temperature = temperatureOverride != null ? temperatureOverride : defaultTemperature;
        int tokens = maxTokensOverride != null ? maxTokensOverride : maxTokens;
        log.info("[DeepSeek] phase={} model={} messageCount={} temperature={} maxTokens={}", phase, model,
                messages == null ? 0 : messages.size(), temperature, tokens);

        try {
            JSONObject body = new JSONObject();
            body.set("model", model);
            body.set("messages", messages);
            body.set("max_tokens", tokens);
            body.set("temperature", temperature);
            body.set("stream", false);

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON_MEDIA))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "";
                    log.error("[DeepSeek] phase={} httpStatus={} bodyPreview={}", phase, response.code(),
                            abbreviate(errBody, 800));
                    return "抱歉，AI 服务暂时不可用。请稍后重试。";
                }
                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject json = JSONUtil.parseObj(responseBody);
                JSONArray choices = json.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                    String content = message.getStr("content");
                    log.info("[DeepSeek] phase={} responseChars={}", phase, content != null ? content.length() : 0);
                    return content != null ? content : "AI 未返回有效内容。";
                }
                log.warn("[DeepSeek] phase={} choices empty", phase);
                return "AI 未返回有效回复。";
            }
        } catch (Exception e) {
            log.error("[DeepSeek] phase={} exception: {}", phase, e.getMessage(), e);
            return "抱歉，AI 服务出现异常。请稍后重试。";
        }
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }
}
