package com.nongxinle.ai.workspace;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.nongxinle.ai.workspace.dto.WorkNoteResponse;
import com.nongxinle.ai.workspace.dto.WorkPinResponse;
import com.nongxinle.entity.GbAiWorkNoteEntity;
import com.nongxinle.entity.GbAiWorkPinEntity;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

public final class AiWorkspaceDtoMaps {

    private AiWorkspaceDtoMaps() {}

    public static WorkPinResponse toPinResponse(GbAiWorkPinEntity e, boolean includeSnapshot) {
        if (e == null) {
            return null;
        }
        Long id = e.getGbAiWpId();
        WorkPinResponse.WorkPinResponseBuilder b = WorkPinResponse.builder()
                .id(id)
                .pinId(id)
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
                .updatedAt(e.getGbAiWpUpdatedAt())
                .primaryCardType(e.getGbAiWpPrimaryCardType())
                .cardCount(e.getGbAiWpCardCount() != null ? e.getGbAiWpCardCount() : 0);
        if (includeSnapshot) {
            b.sourceTextSnapshot(e.getGbAiWpSourceTextSnapshot());
            b.cardsSnapshotJson(e.getGbAiWpCardsSnapshotJson());
            b.cards(parseCardsSnapshot(e.getGbAiWpCardsSnapshotJson()));
        }
        return b.build();
    }

    private static List<Map<String, Object>> parseCardsSnapshot(String cardsSnapshotJson) {
        if (!StringUtils.hasText(cardsSnapshotJson)) {
            return null;
        }
        try {
            return JSON.parseObject(cardsSnapshotJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ignore) {
            return null;
        }
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
