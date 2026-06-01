package com.nongxinle.ai.advisor.capability;

import com.nongxinle.ai.advisor.capability.dto.AdvisorCommonWorkflowDTO;
import com.nongxinle.ai.advisor.capability.dto.AdvisorQuestionTopicDTO;
import com.nongxinle.ai.advisor.capability.dto.AdvisorSuggestedQuestionItemDTO;
import com.nongxinle.ai.advisor.capability.dto.AdvisorSuggestedQuestionRowDTO;
import com.nongxinle.ai.advisor.capability.dto.AiAdvisorCapabilityDTO;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorDetailDTO;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorWorkflowItemDTO;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorWorkflowRunListItemDTO;
import com.nongxinle.mapper.GbAiWorkflowSuggestedQuestionMapper;
import com.nongxinle.service.GbAiAdvisorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 组装顾问能力详情（commonWorkflows + questionTopics + recentRuns）。
 *
 * <p>不参与 Harness 语义路由；workflowCode / questionCode 仅用于展示、归属、埋点、recentRuns。
 */
@Service
@RequiredArgsConstructor
public class AdvisorCapabilityService {

    private static final String SCENE_MINIAPP = "MINIAPP";
    private static final String SCENE_DESKTOP = "DESKTOP";

    private final GbAiAdvisorService gbAiAdvisorService;
    private final GbAiWorkflowSuggestedQuestionMapper suggestedQuestionMapper;

    public AiAdvisorCapabilityDTO loadCapability(Long advisorId, String scene, Long userId) {
        String sceneFilter = normalizeSceneFilter(scene);
        AiAdvisorDetailDTO advisor = gbAiAdvisorService.getAdvisor(advisorId);

        List<AdvisorCommonWorkflowDTO> commonWorkflows = mapCommonWorkflows(gbAiAdvisorService.listAdvisorWorkflows(advisorId));
        String questionCodePrefix =
                AdvisorSuggestedQuestionScopeSupport.questionCodePrefixForAdvisor(advisor.getCode())
                        .orElse(null);
        List<AdvisorQuestionTopicDTO> questionTopics =
                groupQuestionTopics(
                        suggestedQuestionMapper.selectVisibleByAdvisorId(
                                advisorId, sceneFilter, questionCodePrefix));
        List<AiAdvisorWorkflowRunListItemDTO> recentRuns = loadRecentRuns(advisorId, userId);

        return AiAdvisorCapabilityDTO.builder()
                .advisorId(advisor.getAdvisorId())
                .advisorCode(advisor.getCode())
                .advisorName(advisor.getName())
                .subtitle(advisor.getSubtitle())
                .description(advisor.getDescription())
                .capabilityDescription(advisor.getDescription())
                .avatarUrl(advisor.getAvatarUrl())
                .sortOrder(advisor.getSortOrder())
                .scene(StringUtils.hasText(scene) ? sceneFilter : null)
                .commonWorkflows(commonWorkflows)
                .questionTopics(questionTopics)
                .recentRuns(recentRuns)
                .build();
    }

    static String normalizeSceneFilter(String scene) {
        if (!StringUtils.hasText(scene)) {
            return null;
        }
        String norm = scene.trim().toUpperCase(Locale.ROOT);
        if (SCENE_MINIAPP.equals(norm) || SCENE_DESKTOP.equals(norm)) {
            return norm;
        }
        throw new IllegalArgumentException("scene must be MINIAPP or DESKTOP when provided");
    }

    private List<AiAdvisorWorkflowRunListItemDTO> loadRecentRuns(Long advisorId, Long userId) {
        if (userId == null) {
            return List.of();
        }
        return gbAiAdvisorService.listRecentWorkflowRuns(advisorId, userId, 10);
    }

    private static List<AdvisorCommonWorkflowDTO> mapCommonWorkflows(List<AiAdvisorWorkflowItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<AdvisorCommonWorkflowDTO> out = new ArrayList<>(items.size());
        for (AiAdvisorWorkflowItemDTO item : items) {
            AdvisorCommonWorkflowDTO dto = new AdvisorCommonWorkflowDTO();
            dto.setWorkflowId(item.getWorkflowId());
            dto.setWorkflowCode(item.getCode());
            dto.setTitle(item.getName());
            dto.setDescription(item.getDescription());
            dto.setCategory(item.getCategory());
            dto.setEnabled(true);
            dto.setStatus(AdvisorCommonWorkflowDTO.STATUS_ACTIVE);
            dto.setSort(item.getBindSortOrder() != null ? item.getBindSortOrder() : 0);
            dto.setPinned(item.getBindPinned() != null && item.getBindPinned() == 1);
            dto.setDefault(item.getBindIsDefault() != null && item.getBindIsDefault() == 1);
            out.add(dto);
        }
        return out;
    }

    private static List<AdvisorQuestionTopicDTO> groupQuestionTopics(List<AdvisorSuggestedQuestionRowDTO> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, AdvisorQuestionTopicDTO> byTopic = new LinkedHashMap<>();
        for (AdvisorSuggestedQuestionRowDTO row : rows) {
            AdvisorQuestionTopicDTO topic =
                    byTopic.computeIfAbsent(
                            row.getTopicId(),
                            id -> AdvisorQuestionTopicDTO.builder()
                                    .topicId(row.getTopicId())
                                    .title(row.getTopicTitle())
                                    .description(row.getTopicDescription())
                                    .sort(row.getTopicSort() != null ? row.getTopicSort() : 0)
                                    .questions(new ArrayList<>())
                                    .build());
            topic.getQuestions().add(toQuestionItem(row));
        }
        return new ArrayList<>(byTopic.values());
    }

    private static AdvisorSuggestedQuestionItemDTO toQuestionItem(AdvisorSuggestedQuestionRowDTO row) {
        boolean enabled = row.getEnabled() != null && row.getEnabled() == 1;
        String status = row.getStatus();
        boolean clickable = enabled && AdvisorSuggestedQuestionItemDTO.STATUS_ACTIVE.equals(status);
        return AdvisorSuggestedQuestionItemDTO.builder()
                .questionId(row.getQuestionCode())
                .questionCode(row.getQuestionCode())
                .text(row.getQuestionText())
                .workflowId(row.getWorkflowId())
                .workflowCode(row.getWorkflowCode())
                .enabled(enabled)
                .status(status)
                .clickable(clickable)
                .sort(row.getSort() != null ? row.getSort() : 0)
                .scene(row.getScene())
                .intentHint(row.getIntentHint())
                .contractHint(row.getContractHint())
                .build();
    }
}
