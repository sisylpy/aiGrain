package com.nongxinle.ai.workspace;

/**
 * 工作笔记 / 图钉 MVP：noteType、 pin sourceType 字面量（与 DB VARCHAR 一致）。
 */
public final class AiWorkspaceConstants {

    private AiWorkspaceConstants() {}

    public static final String NOTE_MANUAL = "MANUAL";
    public static final String NOTE_FROM_RUN = "FROM_RUN";
    public static final String NOTE_FROM_PIN = "FROM_PIN";
    public static final String NOTE_FROM_SELECTION = "FROM_SELECTION";
    /** 聊天区「本条 assistant 回答保存为笔记」（{@code gb_ai_work_note.gb_ai_wn_primary_message_id}） */
    public static final String NOTE_FROM_MESSAGE = "FROM_MESSAGE";

    /** 从助手正文生成的笔记标题最大字符数（约 20～30 字） */
    public static final int MESSAGE_NOTE_TITLE_MAX_CHARS = 30;

    public static final String PIN_SOURCE_RUN = "RUN";
    public static final String PIN_SOURCE_MESSAGE = "MESSAGE";
    public static final String PIN_SOURCE_SELECTION = "SELECTION";

    public static final int PREVIEW_MAX_CHARS = 500;
    public static final int TITLE_MAX_CHARS = 80;
}
