package com.nongxinle.ai.followup;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 【已废弃业务语义】追问扩写与时间片语提取曾用于 Java keyword 链路；追问路由与时间窗已统一交由
 * {@link com.nongxinle.ai.semantic.AiQuerySemanticLlmParser} + {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver}
 *。<b>禁止</b>新业务调用 {@link #applyIfFollowUp}、{@link #isShortTemporalFollowUp(String)}。
 */
@Deprecated(forRemoval = false)
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpIntentResolveService {

    private static final Pattern SWITCH_TIME =
            Pattern.compile("换(?:成|为|到)(\\S+)");
    /** 追问句中出现的显式话题切换词时不继承 */
    private static final Pattern SWITCH_TOPIC_HINT = Pattern.compile("换成(经营|成本|采购|营收|库存|菜品|毛利|利润)");
    private static final int MAX_FOLLOW_LEN = 40;

    private final AiFollowUpConversationMemory memory;

    /**
     * 若命中追问且存在上一轮快照，则改写 {@link AiRunState#setNormalizedUserInput(String)} 并打日志。
     *
     * @return true 表示已做过等价扩写
     */
    @Deprecated(forRemoval = false)
    public boolean applyIfFollowUp(AiRunState state) {
        // normalizedUserInput 扩写已由 LLM 语义解析 + Resolver 收口；禁用 Java 「短时间短句」spliceTemporal。
        return false;
    }

    /**
     * @deprecated Java 短语时间判断已不再参与主链路；仅保留兼容单测/Harness。禁止新业务调用。
     */
    @Deprecated(forRemoval = false)
    public static boolean isShortTemporalFollowUp(String cur) {
        if (!StringUtils.hasText(cur) || cur.length() > MAX_FOLLOW_LEN) {
            return false;
        }
        if (currentMessageDeclaresDomainPath(cur)) {
            return false;
        }
        String s = cur.replace(" ", "");
        if (SWITCH_TOPIC_HINT.matcher(s).find()) {
            return false;
        }
        return extractNewTemporalPhrase(cur).isPresent();
    }

    /**
     * 当前句是否自带业务域（经营/采购/库存/菜品毛利/成本或 Lexicon 结构化采购）；为 true 时禁止当作「仅时间短句」继承上轮 path。
     * 不含后续 {@code normalizePurchaseStructuredRouting} 补全的 path；「上个月呢」等应返回 false。
     */
    public static boolean currentMessageDeclaresDomainPath(String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return false;
        }
        // 语义域切换由 AiQuerySemanticLlmParser 输出收口；此处不再用语义词典探测领域。
        return StringUtils.hasText(rawMessage.trim()) && SWITCH_TOPIC_HINT.matcher(rawMessage.replace(" ", "")).find();
    }

    private static boolean looksLikeTemporalFollowUp(String cur) {
        return isShortTemporalFollowUp(cur);
    }

    public boolean conflictsWithPreviousPath(String cur, FollowUpPathKind last) {
        return pathTopicConflict(cur, last);
    }

    /** 供 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 等无法注入本 Bean 的场景复用 */
    public static boolean pathTopicConflict(String cur, FollowUpPathKind last) {
        return topicConflict(cur, last);
    }

    /** 新业务词与上一轮路径明显不一致时不继承（避免库存/采购插队）。语义切换改由 QuerySemanticParser；此处恒不阻断。 */
    private static boolean topicConflict(String cur, FollowUpPathKind last) {
        return false;
    }

    public static Optional<String> extractNewTemporalPhrase(String cur) {
        if (!StringUtils.hasText(cur)) {
            return Optional.empty();
        }
        String s = cur.trim().replace(" ", "");
        Matcher m = SWITCH_TIME.matcher(s);
        if (m.find()) {
            String cap = sanitizePhrase(m.group(1));
            if (matchesKnownTemporal(cap)) {
                return Optional.of(cap);
            }
        }
        String hit = null;
        int earliestIdx = Integer.MAX_VALUE;
        for (String p : TEMPORALS_LONGEST_FIRST) {
            int idx = s.indexOf(p);
            if (idx < 0) {
                continue;
            }
            if (idx < earliestIdx || (idx == earliestIdx && hit != null && p.length() > hit.length())) {
                earliestIdx = idx;
                hit = p;
            } else if (idx == earliestIdx && hit == null) {
                hit = p;
            }
        }
        return hit == null ? Optional.empty() : Optional.of(hit);
    }

    private static String sanitizePhrase(String fragment) {
        if (fragment == null) {
            return "";
        }
        String x = fragment.replace(" ", "").replaceAll("[\\.。,，]+", "");
        x = x.replaceAll("(看看|看下|说下|查查|行吗|呗|罢了|算了)$", "");
        return x.trim();
    }

    /**
     * 在上一轮问句中用 newPhrase 替换首个出现的时间段词；若没有可替换词，则把 newPhrase 前缀到问句上。
     */
    public static String spliceTemporal(String lastQuestion, String newPhrase) {
        if (!StringUtils.hasText(lastQuestion) || !StringUtils.hasText(newPhrase)) {
            return lastQuestion;
        }
        String s = lastQuestion.replace(" ", "");
        int idx = -1;
        String hit = null;
        for (String p : TEMPORALS_LONGEST_FIRST) {
            int j = s.indexOf(p);
            if (j >= 0 && (idx < 0 || j < idx)) {
                idx = j;
                hit = p;
            }
        }
        if (idx >= 0 && hit != null) {
            return s.substring(0, idx) + newPhrase + s.substring(idx + hit.length());
        }
        return newPhrase + s;
    }

    private static boolean matchesKnownTemporal(String p) {
        if (!StringUtils.hasText(p)) {
            return false;
        }
        for (String t : KNOWN_TEMPORAL_SET) {
            if (p.equals(t)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> buildLongestFirst() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        String[] arr = new String[] {
                "前年", "去年", "今年", "明年", "上月", "下月",
                "上个月", "下个月", "本周", "上周", "下周", "这周",
                "本月", "当月", "这个季度", "上季度", "本季度",
                "这个星期", "上个星期",
                "今天", "昨天", "前天", "明天", "后天",
                "这个月"
        };
        for (String a : arr) {
            set.add(a);
        }
        List<String> list = new ArrayList<>(set);
        list.sort(Comparator.comparingInt(String::length).reversed());
        return List.copyOf(list);
    }

    /**
     * 用于门店范围追问：从句中摘掉已知时间词，便于在「汀兰餐厅上个月呢」中解析店名。
     */
    public static String stripKnownTemporalPhrases(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String s = raw.trim().replace(" ", "");
        for (String t : TEMPORALS_LONGEST_FIRST) {
            s = s.replace(t, "");
        }
        return s.trim();
    }

    private static final List<String> TEMPORALS_LONGEST_FIRST = buildLongestFirst();
    private static final java.util.Set<String> KNOWN_TEMPORAL_SET = new java.util.HashSet<>(TEMPORALS_LONGEST_FIRST);

    static {
        // 增补常见口语（与上一轮替换表一致口径）
        KNOWN_TEMPORAL_SET.add("本年");
        KNOWN_TEMPORAL_SET.add("近期");
        KNOWN_TEMPORAL_SET.add("最近");
        List<String> more = List.of(
                "本月", "当月", "这个月", "上个月", "上月", "下个月", "本周", "这周", "上周", "下周",
                "本年", "今年", "去年", "前年", "今天", "昨天", "前天", "明天");
        KNOWN_TEMPORAL_SET.addAll(more);
    }

    /** 由 {@link AiRunState} 构建可持久化快照；无经营主线或未识别则返回 null。 */
    public static AiFollowUpIntentSnapshot snapshotFromCompletedState(AiRunState state) {
        if (state == null || state.isCancelled()) {
            return null;
        }
        if (state.getWorkspaceMode() != AiWorkspaceMode.BUSINESS_CHAT) {
            return null;
        }
        String q = state.getNormalizedUserInput();
        if (!StringUtils.hasText(q) && state.getResolvedQueryContext() != null) {
            q = state.getResolvedQueryContext().getNormalizedQuestion();
        }
        if (!StringUtils.hasText(q)) {
            return null;
        }
        FollowUpPathKind kind = null;
        if (state.isDishProfitPath()) {
            kind = FollowUpPathKind.DISH_PROFIT;
        } else if (state.isBusinessOverviewPath()) {
            kind = FollowUpPathKind.BUSINESS_OVERVIEW;
        } else if (state.isWarehouseStockOverviewPath()) {
            kind = FollowUpPathKind.WAREHOUSE_STOCK;
        } else if (state.isStockReduceQueryPath()) {
            kind = FollowUpPathKind.STOCK_REDUCE_QUERY;
        } else if (state.isRevenueOverviewPath()) {
            kind = FollowUpPathKind.REVENUE_OVERVIEW;
        } else if (state.isPurchaseOverviewPath()) {
            kind = FollowUpPathKind.PURCHASE_OVERVIEW;
        } else if (state.isPurchaseCostInsightPath()) {
            kind = FollowUpPathKind.PURCHASE_COST;
        } else if (state.isCostInsightPath()) {
            kind = FollowUpPathKind.COST_INSIGHT;
        }
        if (kind == null) {
            return null;
        }
        return AiFollowUpIntentSnapshot.builder()
                .v(AiFollowUpIntentSnapshot.VERSION)
                .effectiveQuestion(q.trim())
                .pathKind(kind)
                .build();
    }
}
