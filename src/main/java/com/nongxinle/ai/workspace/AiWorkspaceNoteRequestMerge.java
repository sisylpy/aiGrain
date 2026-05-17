package com.nongxinle.ai.workspace;

import com.nongxinle.ai.workspace.dto.WorkNoteCreateRequest;
import com.nongxinle.ai.workspace.dto.WorkNotePrimarySourcePayload;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 将前端嵌套的 {@link WorkNotePrimarySourcePayload} 合并进顶层请求（仅填补空缺）。
 * 若仍缺 {@code noteType}，则根据锚点 {@code sourceType}（如 RUN）推断为 {@code FROM_RUN} 等。
 */
public final class AiWorkspaceNoteRequestMerge {

    private AiWorkspaceNoteRequestMerge() {}

    public static void applyPrimarySourceIfNeeded(WorkNoteCreateRequest request) {
        if (request == null) {
            return;
        }
        WorkNotePrimarySourcePayload snap = request.getPrimarySource();
        if (snap == null) {
            inferNoteTypeIfMissing(request, null);
            return;
        }

        if (request.getConversationId() == null) {
            request.setConversationId(
                    AiWorkspaceParse.parseLongLenient(snap.getConversationId(), "primarySource.conversationId"));
        }
        if (request.getPrimaryConversationId() == null) {
            request.setPrimaryConversationId(
                    AiWorkspaceParse.parseLongLenient(snap.getConversationId(), "primarySource.conversationId"));
        }
        if (request.getPrimaryRunId() == null) {
            request.setPrimaryRunId(AiWorkspaceParse.parseLongLenient(snap.getRunId(), "primarySource.runId"));
        }
        if (request.getPrimaryMessageId() == null) {
            request.setPrimaryMessageId(AiWorkspaceParse.parseLongLenient(snap.getMessageId(), "primarySource.messageId"));
        }
        if (!StringUtils.hasText(request.getPrimarySourceType()) && StringUtils.hasText(snap.getSourceType())) {
            request.setPrimarySourceType(snap.getSourceType().trim());
        }
        if (!StringUtils.hasText(request.getSourceTextSnapshot())
                && StringUtils.hasText(snap.getSourceTextSnapshot())) {
            request.setSourceTextSnapshot(snap.getSourceTextSnapshot());
        }
        if (!StringUtils.hasText(request.getSourceAnswerPreview())
                && StringUtils.hasText(snap.getSourceAnswerPreview())) {
            request.setSourceAnswerPreview(snap.getSourceAnswerPreview());
        }
        if (!StringUtils.hasText(request.getTitle()) && StringUtils.hasText(snap.getSourceTitle())) {
            request.setTitle(snap.getSourceTitle().trim());
        }

        inferNoteTypeIfMissing(request, snap.getSourceType());
    }

    private static void inferNoteTypeIfMissing(WorkNoteCreateRequest request, String snapSourceTypeFallback) {
        if (StringUtils.hasText(request.getNoteType())) {
            return;
        }
        String anchor = request.getPrimarySourceType();
        if (!StringUtils.hasText(anchor)) {
            anchor = snapSourceTypeFallback;
        }
        String inferred = inferNoteTypeFromAnchor(anchor);
        if (inferred != null) {
            request.setNoteType(inferred);
        }
    }

    /**
     * 前端锚点常用 RUN/MESSAGE/SELECTION；库表 note_type 使用 MANUAL/FROM_*。
     */
    static String inferNoteTypeFromAnchor(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (AiWorkspaceConstants.NOTE_MANUAL.equals(u)
                || AiWorkspaceConstants.NOTE_FROM_RUN.equals(u)
                || AiWorkspaceConstants.NOTE_FROM_PIN.equals(u)
                || AiWorkspaceConstants.NOTE_FROM_SELECTION.equals(u)) {
            return u;
        }
        if (AiWorkspaceConstants.PIN_SOURCE_RUN.equals(u) || AiWorkspaceConstants.PIN_SOURCE_MESSAGE.equals(u)) {
            return AiWorkspaceConstants.NOTE_FROM_RUN;
        }
        if (AiWorkspaceConstants.PIN_SOURCE_SELECTION.equals(u)) {
            return AiWorkspaceConstants.NOTE_FROM_SELECTION;
        }
        return null;
    }
}
