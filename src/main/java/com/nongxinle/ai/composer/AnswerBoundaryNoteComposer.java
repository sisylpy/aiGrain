package com.nongxinle.ai.composer;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaselineSupport;
import com.nongxinle.ai.inventory.CoverDaysSalesBaselinePresentationSupport;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.resolver.AiMultiTurnOrgScopePolicy;
import com.nongxinle.ai.context.AiResolvedTimeWindowDisplaySupport;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将 {@link com.nongxinle.ai.context.AiResolvedQueryContext#getAnswerBoundaryNote()} 中面向用户的开头说明
 * 与「时间 / 门店是否本句新指定」对齐；仅消费 Resolver 已写入的结构化字段与语义点名列表，不解析用户原文。
 */
public final class AnswerBoundaryNoteComposer {

    private static final String RESOLVER_COMBINED_BAD_SUFFIX =
            "本句未指定新的时间和门店。若需调整请直接说明。";

    private static final String GROUP_SCOPE_ALL_STORES_SUFFIX =
            "本句指定全部店铺，已切换为集团范围。";

    private AnswerBoundaryNoteComposer() {
    }

    /**
     * 修正 {@link AiResolvedTimeWindowDisplaySupport#buildCombinedBoundaryNote} 在「仅时间继承、本句已点名门店」等场景下误用的统一 suffix。
     */
    public static String refineUserFacingBoundaryNote(AiResolvedQueryContext ctx, String rawNote) {
        if (ToolRequestContractExecutionParamSupport.isInventoryCoverDaysCapability(ctx)) {
            return refineInventoryCoverDaysBoundaryNote(ctx, rawNote);
        }
        if (ToolRequestContractExecutionParamSupport.isWarehouseNearExpiryContract(ctx)) {
            return refineWarehouseNearExpiryBoundaryNote(ctx, rawNote);
        }
        if (ToolRequestContractExecutionParamSupport.isWarehouseInventorySupervisionContract(ctx)) {
            return refineWarehouseInventorySupervisionBoundaryNote(ctx, rawNote);
        }
        if (ctx == null || !StringUtils.hasText(rawNote) || !rawNote.contains(RESOLVER_COMBINED_BAD_SUFFIX)) {
            return rawNote;
        }
        boolean timeInherited = "INHERITED_PREVIOUS".equals(ctx.getEffectiveTimeWindowSource());
        boolean scopeInherited = "INHERITED_PREVIOUS".equals(ctx.getEffectiveScopeSource());
        AiResolvedTimeWindow tw = ctx.getTimeWindow();
        String timeHuman = tw != null ? AiResolvedTimeWindowDisplaySupport.humanReadableTimeCarryover(tw) : "上文";
        if (timeInherited && scopeInherited) {
            // 【优化】检测 LLM 返回 scopeAction=OVERRIDE/NEW 但无具体门店名 → "全部店铺"场景
            if (semanticDeclaresGroupScopeOverride(ctx)) {
                String suffix = AiResolvedOrgScope.SCOPE_GROUP.equals(ctx.getOrgScope().getScopeType())
                        ? GROUP_SCOPE_ALL_STORES_SUFFIX
                        : "本句指定范围覆盖上一轮门店。";
                return "时间沿用上文「" + timeHuman + "」。" + suffix + "若需调整请直接说明。";
            }
            return rawNote;
        }
        if (timeInherited && !scopeInherited) {
            String storePhrase = structuredStoreEnumerationPhrase(ctx);
            if (!StringUtils.hasText(storePhrase)) {
                return rawNote;
            }
            return "本句指定了" + storePhrase + "；时间沿用上文「" + timeHuman + "」。若需调整请直接说明。";
        }
        if (!timeInherited && scopeInherited) {
            String timePhrase = explicitTimePhraseForBoundary(ctx, tw);
            String storeHint =
                    AiMultiTurnOrgScopePolicy.singleVisibleStoreName(ctx.getOrgScope()).orElse("上文门店");
            if (!StringUtils.hasText(timePhrase)) {
                return rawNote;
            }
            return "门店沿用上文「" + storeHint + "」；本句指定了新的统计时间为「" + timePhrase + "」。若需调整请直接说明。";
        }
        return rawNote;
    }

    /**
     * 检测 LLM 语义层是否声明了「全部店铺/集团范围覆盖」。
     * 当 scopeAction=OVERRIDE 或 NEW，但 mentionedStoreNames 为空时，代表用户在说"全部店铺"。
     */
    private static boolean semanticDeclaresGroupScopeOverride(AiResolvedQueryContext ctx) {
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        String action = sem.getScopeAction();
        if (!StringUtils.hasText(action)) {
            return false;
        }
        String norm = action.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        if (!("OVERRIDE".equals(norm) || "NEW".equals(norm))) {
            return false;
        }
        // 有 OVERRIDE/NEW 但无具体门店名 → "全部店铺"场景
        return sem.effectiveMentionedStoreNames().isEmpty();
    }

    private static String structuredStoreEnumerationPhrase(AiResolvedQueryContext ctx) {
        List<String> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> harness = ctx.getHarnessMultiStoreMatchedStores();
        if (harness != null) {
            for (String n : harness) {
                addNameIfNew(ordered, seen, n);
            }
        }
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        if (sem != null && !sem.isParseMissing()) {
            for (String n : sem.effectiveMentionedStoreNames()) {
                addNameIfNew(ordered, seen, n);
            }
        }
        String one = ctx.getResolvedMatchedSemanticStoreMention();
        if (StringUtils.hasText(one)) {
            addNameIfNew(ordered, seen, one);
        }
        if (ordered.isEmpty()) {
            return AiMultiTurnOrgScopePolicy.singleVisibleStoreName(ctx.getOrgScope()).map(n -> "「" + n + "」").orElse(null);
        }
        return formatStoreBracketList(ordered);
    }

    private static void addNameIfNew(List<String> ordered, Set<String> seen, String raw) {
        String t = AiQuerySemanticParseResult.sanitizeMentionedStoreNameToken(raw);
        if (!StringUtils.hasText(t)) {
            return;
        }
        String key = t.toLowerCase(java.util.Locale.ROOT);
        if (seen.add(key)) {
            ordered.add(t);
        }
    }

    /** 「A」；「A」与「B」；「A」、「B」、「C」（与口吻文档一致）。 */
    private static String formatStoreBracketList(List<String> names) {
        if (names.size() == 1) {
            return "「" + names.get(0) + "」";
        }
        if (names.size() == 2) {
            return "「" + names.get(0) + "」与「" + names.get(1) + "」";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append("「").append(names.get(i)).append("」");
        }
        return sb.toString();
    }

    private static String refineInventoryCoverDaysBoundaryNote(
            AiResolvedQueryContext ctx, String rawNote) {
        boolean scopeInherited = "INHERITED_PREVIOUS".equals(ctx.getEffectiveScopeSource());
        boolean explicitBaseline = DishIngredientCoverSalesBaselineSupport.isExplicitSalesBaseline(ctx);
        String baselinePhrase =
                explicitBaseline
                        ? "销量基线按"
                                + CoverDaysSalesBaselinePresentationSupport.formatPeriodPhraseFromContext(ctx)
                        : "销量基线按最近7天";
        String core = "当前库存 + " + baselinePhrase;
        if (scopeInherited) {
            String storeHint =
                    AiMultiTurnOrgScopePolicy.singleVisibleStoreName(ctx.getOrgScope())
                            .orElse("上文门店");
            return "门店沿用上文「" + storeHint + "」；" + core + "。若需调整请直接说明。";
        }
        if (explicitBaseline) {
            return core + "。若需调整请直接说明。";
        }
        if (!StringUtils.hasText(rawNote)) {
            return null;
        }
        if ("INHERITED_PREVIOUS".equals(ctx.getEffectiveTimeWindowSource())
                || "DEFAULT_MONTH_TO_DATE".equals(ctx.getEffectiveTimeWindowSource())) {
            return null;
        }
        return rawNote;
    }

    /** warehouse.near_expiry：CURRENT_SNAPSHOT，boundary 不展示期间/继承时间。 */
    private static String refineWarehouseNearExpiryBoundaryNote(
            AiResolvedQueryContext ctx, String rawNote) {
        return refineWarehouseCurrentSnapshotBoundaryNote(ctx);
    }

    /** warehouse.inventory_supervision：CURRENT_SNAPSHOT，boundary 不展示期间/继承时间。 */
    private static String refineWarehouseInventorySupervisionBoundaryNote(
            AiResolvedQueryContext ctx, String rawNote) {
        return refineWarehouseCurrentSnapshotBoundaryNote(ctx);
    }

    private static String refineWarehouseCurrentSnapshotBoundaryNote(AiResolvedQueryContext ctx) {
        boolean scopeInherited = "INHERITED_PREVIOUS".equals(ctx.getEffectiveScopeSource());
        if (scopeInherited) {
            String storeHint =
                    AiMultiTurnOrgScopePolicy.singleVisibleStoreName(ctx.getOrgScope())
                            .orElse("上文门店");
            return "门店沿用上文「" + storeHint + "」；当前库存（截至当前）。若需调整请直接说明。";
        }
        return null;
    }

    private static String explicitTimePhraseForBoundary(AiResolvedQueryContext ctx, AiResolvedTimeWindow tw) {
        if (ctx != null && StringUtils.hasText(ctx.getTimeWindowLabel())) {
            return ctx.getTimeWindowLabel().trim();
        }
        if (tw == null) {
            return null;
        }
        if (StringUtils.hasText(tw.getDisplayText())) {
            String d = tw.getDisplayText().trim();
            if (!d.equals("继承上一轮时间窗")) {
                return d;
            }
        }
        return AiResolvedTimeWindowDisplaySupport.humanReadableTimeCarryover(tw);
    }
}
