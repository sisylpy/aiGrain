package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析阶段的多门店 Harness 观测：谁在公共范围层识别了「并排对比」且是否成功收成 {@code GROUP} 子集、店名对齐结果。
 */
public final class AiMultiStoreHarnessTrace {

    private boolean detected;
    /** 某一路径调用过 {@code copyOrgNarrowedToStoreSubsetKeepingGroup} 且保留了 ≥2 门店。 */
    private boolean subsetApplied;
    /** 并排子集对齐后的店名序列（可读名，非 id）。仅 {@link #subsetApplied} 时为非空。 */
    private final List<String> matchedStoreNames = new ArrayList<>();

    public void ingestDetectionCandidate(
            String normalizedUserQuestion,
            AiQuerySemanticParseResult semanticLlm,
            AiResolvedOrgScope orgBeforeExplicitStoreNarrows) {
        if (!groupWithAtLeastTwoStores(orgBeforeExplicitStoreNarrows)) {
            return;
        }
        if (semanticLlm == null) {
            return;
        }
        List<String> mentions = semanticLlm.effectiveMentionedStoreNames();
        if (mentions.size() >= 2) {
            detected = true;
        }
    }

    public void noteSubsetKeepingGroupApplied(List<AiStoreScopeDTO> picks) {
        if (picks == null || picks.size() < 2) {
            return;
        }
        subsetApplied = true;
        matchedStoreNames.clear();
        for (AiStoreScopeDTO s : picks) {
            if (s == null || !StringUtils.hasText(s.getStoreName())) {
                continue;
            }
            matchedStoreNames.add(s.getStoreName().trim());
        }
    }

    /**
     * 用户表达多店并排对比语义且最终结果仍为 GROUP 且可见门店根不少于 2，视为「未被误收成单店」。
     */
    public boolean resolveSingleStoreNarrowingBlocked(
            String normalizedUserQuestion,
            AiResolvedOrgScope mergedOrgFinal,
            AiQuerySemanticParseResult semanticLlm) {
        boolean groupWide =
                mergedOrgFinal != null
                        && AiResolvedOrgScope.SCOPE_GROUP.equals(mergedOrgFinal.getScopeType())
                        && mergedOrgFinal.getVisibleStores() != null
                        && mergedOrgFinal.getVisibleStores().size() >= 2;

        boolean utteranceMultiPair = effectiveMentionsAtLeastTwo(semanticLlm);

        return utteranceMultiPair && groupWide;
    }

    public boolean isDetected() {
        return detected;
    }

    public boolean isSubsetApplied() {
        return subsetApplied;
    }

    public List<String> copyMatchedStores() {
        return matchedStoreNames.isEmpty() ? List.of() : new ArrayList<>(matchedStoreNames);
    }

    private static boolean groupWithAtLeastTwoStores(AiResolvedOrgScope org) {
        return org != null
                && AiResolvedOrgScope.SCOPE_GROUP.equals(org.getScopeType())
                && org.getVisibleStores() != null
                && org.getVisibleStores().size() >= 2;
    }

    private static boolean effectiveMentionsAtLeastTwo(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        List<String> m = sem.effectiveMentionedStoreNames();
        return m != null && m.size() >= 2;
    }

    private AiMultiStoreHarnessTrace() {
    }

    /** 每条 Run 一个新的 trace 实例（可变，解析阶段单行有效）。 */
    public static AiMultiStoreHarnessTrace create() {
        return new AiMultiStoreHarnessTrace();
    }
}
