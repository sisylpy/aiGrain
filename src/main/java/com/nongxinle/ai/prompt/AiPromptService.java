package com.nongxinle.ai.prompt;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按 promptId 加载 classpath markdown；带进程内缓存。正文默认取 「# Prompt 正文」小节之后，以保持文件头元数据不进 LLM。
 */
@Service
@RequiredArgsConstructor
public class AiPromptService {

    /**
     * 与资源文件约定的正文锚点（行首）。
     */
    private static final Pattern PROMPT_BODY_ANCHOR = Pattern.compile("^#\\s+Prompt\\s+正文\\s*$", Pattern.MULTILINE);

    private final AiPromptRegistry registry;

    private final ConcurrentHashMap<String, String> bodyCache = new ConcurrentHashMap<>();

    /**
     * 返回送入模型的 system prompt 正文（已去头、trim）。
     */
    public String require(String promptId) {
        String id = promptId == null ? "" : promptId.trim();
        if (id.isEmpty()) {
            throw new AiPromptNotFoundException("promptId blank");
        }
        return bodyCache.computeIfAbsent(id, this::loadAndExtractUnchecked);
    }

    private String loadAndExtractUnchecked(String promptId) {
        String rel = registry.resolveClasspathRelativePath(promptId);
        if (rel == null) {
            throw new AiPromptNotFoundException("unknown promptId (not registered): " + promptId);
        }
        ClassPathResource res = new ClassPathResource(rel);
        if (!res.exists()) {
            throw new AiPromptNotFoundException("prompt resource missing: classpath:" + rel + " for promptId=" + promptId);
        }
        String raw;
        try {
            raw = readUtf8(res);
        } catch (IOException e) {
            throw new AiPromptNotFoundException("failed reading promptId=" + promptId + " path=classpath:" + rel + ": " + e.getMessage());
        }
        String body = extractPromptBody(raw);
        if (body.isEmpty()) {
            throw new AiPromptNotFoundException("prompt body empty after extraction for promptId=" + promptId + " path=classpath:" + rel);
        }
        return body;
    }

    static String extractPromptBody(String fullMarkdown) {
        if (fullMarkdown == null) {
            return "";
        }
        Matcher m = PROMPT_BODY_ANCHOR.matcher(fullMarkdown);
        if (m.find()) {
            String tail = fullMarkdown.substring(m.end());
            int chop = tail.startsWith("\n") ? 1 : 0;
            return tail.substring(chop).trim();
        }
        return fullMarkdown.trim();
    }

    private static String readUtf8(Resource res) throws IOException {
        try (var in = res.getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }
}
