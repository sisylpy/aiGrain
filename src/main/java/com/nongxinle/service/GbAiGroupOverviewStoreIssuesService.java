package com.nongxinle.service;

import com.nongxinle.ai.dto.business.AiOverviewCoveredStoreItem;
import com.nongxinle.ai.dto.business.AiOverviewStoreIssueItem;
import com.nongxinle.ai.dto.business.AiOverviewVisibleStoreItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 集团经营概览：在「记账部门 ∪ 可视子树」口径下拆分「数据缺失门店」与「经营异常门店」。
 * <p>
 * v1：仅 {@link com.nongxinle.ai.mapping.AiRoleMapper#isGroupWideOrgScope(String)}（集团管理端）。
 */
public interface GbAiGroupOverviewStoreIssuesService {

    /**
     * @param aiRoleCode 非集团广角返回空列表
     */
    BuiltGroupOverviewStoreIssues buildGroupStoreIssuesSnapshot(
            String aiRoleCode,
            List<Integer> resolvedDepartmentIds,
            String startDate,
            String stopDate,
            Long anchorDepartmentIdForDisLookup);

    class BuiltGroupOverviewStoreIssues {

        public static BuiltGroupOverviewStoreIssues empty() {
            return new BuiltGroupOverviewStoreIssues(List.of(), List.of(), null, List.of(), List.of());
        }

        private final List<AiOverviewStoreIssueItem> dataMissingStores;
        private final List<AiOverviewStoreIssueItem> attentionStores;
        private final String priorityStoresBrief;
        private final List<AiOverviewVisibleStoreItem> visibleStores;
        private final List<AiOverviewCoveredStoreItem> coveredStores;

        public BuiltGroupOverviewStoreIssues(
                List<AiOverviewStoreIssueItem> dataMissingStores,
                List<AiOverviewStoreIssueItem> attentionStores,
                String priorityStoresBrief,
                List<AiOverviewVisibleStoreItem> visibleStores,
                List<AiOverviewCoveredStoreItem> coveredStores) {
            this.dataMissingStores = dataMissingStores == null ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(dataMissingStores));
            this.attentionStores = attentionStores == null ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(attentionStores));
            this.priorityStoresBrief = priorityStoresBrief;
            this.visibleStores = visibleStores == null ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(visibleStores));
            this.coveredStores = coveredStores == null ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(coveredStores));
        }

        public List<AiOverviewStoreIssueItem> getDataMissingStores() {
            return dataMissingStores;
        }

        public List<AiOverviewStoreIssueItem> getAttentionStores() {
            return attentionStores;
        }

        public String getPriorityStoresBrief() {
            return priorityStoresBrief;
        }

        public List<AiOverviewVisibleStoreItem> getVisibleStores() {
            return visibleStores;
        }

        public List<AiOverviewCoveredStoreItem> getCoveredStores() {
            return coveredStores;
        }
    }
}
