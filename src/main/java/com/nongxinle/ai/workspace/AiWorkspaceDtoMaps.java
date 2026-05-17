package com.nongxinle.ai.workspace;

import com.nongxinle.ai.workspace.dto.WorkNoteResponse;
import com.nongxinle.ai.workspace.dto.WorkPinResponse;
import com.nongxinle.entity.GbAiWorkNoteEntity;
import com.nongxinle.entity.GbAiWorkPinEntity;

public final class AiWorkspaceDtoMaps {

    private AiWorkspaceDtoMaps() {}

    public static WorkPinResponse toPinResponse(GbAiWorkPinEntity e, boolean includeSnapshot) {
        if (e == null) {
            return null;
        }
        WorkPinResponse.WorkPinResponseBuilder b = WorkPinResponse.builder()
                .id(e.getGbAiWpId())
                .userId(e.getGbAiWpUserId())
                .conversationId(e.getGbAiWpConversationId())
                .runId(e.getGbAiWpRunId())
                .messageId(e.getGbAiWpMessageId())
                .title(e.getGbAiWpTitle())
                .sourceType(e.getGbAiWpSourceType())
                .sourceAnswerPreview(e.getGbAiWpSourceAnswerPreview())
                .sourceRole(e.getGbAiWpSourceRole())
                .sourceAgentName(e.getGbAiWpSourceAgentName())
                .sourceCreatedAt(e.getGbAiWpSourceCreatedAt())
                .createdAt(e.getGbAiWpCreatedAt())
                .updatedAt(e.getGbAiWpUpdatedAt());
        if (includeSnapshot) {
            b.sourceTextSnapshot(e.getGbAiWpSourceTextSnapshot());
        }
        return b.build();
    }

    public static WorkNoteResponse toNoteResponse(GbAiWorkNoteEntity e, boolean includeSnapshot) {
        if (e == null) {
            return null;
        }
        WorkNoteResponse.WorkNoteResponseBuilder b = WorkNoteResponse.builder()
                .id(e.getGbAiWnId())
                .userId(e.getGbAiWnUserId())
                .conversationId(e.getGbAiWnConversationId())
                .title(e.getGbAiWnTitle())
                .content(e.getGbAiWnContentMd())
                .noteType(e.getGbAiWnNoteType())
                .primarySourceType(e.getGbAiWnPrimarySourceType())
                .primaryConversationId(e.getGbAiWnPrimaryConversationId())
                .primaryRunId(e.getGbAiWnPrimaryRunId())
                .primaryMessageId(e.getGbAiWnPrimaryMessageId())
                .sourceAnswerPreview(e.getGbAiWnSourceAnswerPreview())
                .createdAt(e.getGbAiWnCreatedAt())
                .updatedAt(e.getGbAiWnUpdatedAt());
        if (includeSnapshot) {
            b.sourceTextSnapshot(e.getGbAiWnSourceTextSnapshot());
        }
        return b.build();
    }
}
