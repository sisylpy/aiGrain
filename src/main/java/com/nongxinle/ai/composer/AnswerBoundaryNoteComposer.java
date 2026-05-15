package com.nongxinle.ai.composer;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.resolver.AiMultiTurnOrgScopePolicy;
import com.nongxinle.ai.resolver.AiMultiTurnTimeWindowPolicy;
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

    private AnswerBoundaryNoteComposer() {
    }

    /**
     * 修正 Resolver 侧 {@code buildCombinedBoundaryNote} 在「仅时间继承、本句已点名门店」等场景下误用的统一 suffix。
     */
    public static String refineUserFacingBoundaryNote(AiResolvedQueryContext ctx, String rawNote) {
        if (ctx == null || !StringUtils.hasText(rawNote) || !rawNote.contains(RESOLVER_COMBINED_BAD_SUFFIX)) {
            return rawNote;
        }
        boolean timeInherited = "INHERITED_PREVIOUS".equals(ctx.getEffectiveTimeWindowSource());
        boolean scopeInherited = "INHERITED_PREVIOUS".equals(ctx.getEffectiveScopeSource());
        AiResolvedTimeWindow tw = ctx.getTimeWindow();
        String timeHuman = tw != null ? AiMultiTurnTimeWindowPolicy.humanReadableTimeCarryover(tw) : "上文";
        if (timeInherited && scopeInherited) {
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
        return AiMultiTurnTimeWindowPolicy.humanReadableTimeCarryover(tw);
    }
}
