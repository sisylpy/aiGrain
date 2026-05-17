package com.nongxinle.ai.workspace;

import com.nongxinle.ai.workspace.dto.WorkPinCreateRequest;
import com.nongxinle.ai.workspace.dto.WorkPinSourceSnapshotPayload;
import org.springframework.util.StringUtils;

/**
 * 将前端嵌套的 {@link WorkPinSourceSnapshotPayload} 合并进顶层请求（仅填补空缺，不覆盖已有顶层字段）。
 */
public final class AiWorkspacePinRequestMerge {

    private AiWorkspacePinRequestMerge() {}

    public static void applyNestedSnapshotIfNeeded(WorkPinCreateRequest request) {
        if (request == null) {
            return;
        }
        WorkPinSourceSnapshotPayload snap = request.getSourceSnapshot();
        if (snap == null) {
            return;
        }
        if (request.getConversationId() == null) {
            request.setConversationId(AiWorkspaceParse.parseLongLenient(snap.getConversationId(), "sourceSnapshot.conversationId"));
        }
        if (request.getRunId() == null) {
            request.setRunId(AiWorkspaceParse.parseLongLenient(snap.getRunId(), "sourceSnapshot.runId"));
        }
        if (request.getMessageId() == null) {
            request.setMessageId(AiWorkspaceParse.parseLongLenient(snap.getMessageId(), "sourceSnapshot.messageId"));
        }
        if (!StringUtils.hasText(request.getSourceType()) && StringUtils.hasText(snap.getSourceType())) {
            request.setSourceType(snap.getSourceType().trim());
        }
        if (!StringUtils.hasText(request.getSourceTextSnapshot())
                && StringUtils.hasText(snap.getSourceTextSnapshot())) {
            request.setSourceTextSnapshot(snap.getSourceTextSnapshot());
        }
        if (!StringUtils.hasText(request.getSourceAnswerPreview())
                && StringUtils.hasText(snap.getSourceAnswerPreview())) {
            request.setSourceAnswerPreview(snap.getSourceAnswerPreview());
        }
        if (!StringUtils.hasText(request.getSourceRole()) && StringUtils.hasText(snap.getSourceRole())) {
            request.setSourceRole(snap.getSourceRole());
        }
        if (!StringUtils.hasText(request.getSourceAgentName()) && StringUtils.hasText(snap.getSourceAgentName())) {
            request.setSourceAgentName(snap.getSourceAgentName());
        }
        if (!StringUtils.hasText(request.getSourceCreatedAt()) && StringUtils.hasText(snap.getSourceCreatedAt())) {
            request.setSourceCreatedAt(snap.getSourceCreatedAt());
        }
        if (!StringUtils.hasText(request.getTitle()) && StringUtils.hasText(snap.getSourceTitle())) {
            request.setTitle(snap.getSourceTitle().trim());
        }
    }
}
