package com.nongxinle.ai.workspace;

import org.springframework.util.StringUtils;

/**
 * 预览截取与默认标题（纯字符串，无 IO）。
 */
public final class AiWorkspaceTextSupport {

    private AiWorkspaceTextSupport() {}

    public static String truncatePreview(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String t = text.trim();
        if (t.length() <= AiWorkspaceConstants.PREVIEW_MAX_CHARS) {
            return t;
        }
        return t.substring(0, AiWorkspaceConstants.PREVIEW_MAX_CHARS);
    }

    /**
     * 消息级「保存笔记」：取正文首行并压缩空白，截断为 {@link AiWorkspaceConstants#MESSAGE_NOTE_TITLE_MAX_CHARS}。
     */
    public static String deriveMessageNoteTitle(String content) {
        if (!StringUtils.hasText(content)) {
            return "笔记";
        }
        String line = content.replace('\n', ' ').trim().replaceAll("\\s+", " ");
        int max = AiWorkspaceConstants.MESSAGE_NOTE_TITLE_MAX_CHARS;
        if (line.length() <= max) {
            return line;
        }
        return line.substring(0, max);
    }

    /**
     * Pin：title 为空时由 preview / snapshot 生成短标题。
     */
    public static String derivePinTitle(String requestedTitle, String preview, String snapshot) {
        if (StringUtils.hasText(requestedTitle)) {
            return requestedTitle.trim();
        }
        String base = StringUtils.hasText(preview) ? preview : snapshot;
        if (!StringUtils.hasText(base)) {
            return "图钉";
        }
        String line = base.replace('\n', ' ').trim();
        if (line.length() <= AiWorkspaceConstants.TITLE_MAX_CHARS) {
            return line;
        }
        return line.substring(0, AiWorkspaceConstants.TITLE_MAX_CHARS);
    }

    public static String normalizeWhitespaceSnapshot(String snapshot) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.trim();
    }
}
