package com.nongxinle.ai.followup;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 追问侧窄规则：显式话题切换 hint 与路径冲突占位（供 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 使用）。
 * 域切换主语义由 {@link com.nongxinle.ai.semantic.AiQuerySemanticLlmParser} 收口。
 */
public final class AiFollowUpHintSupport {

    private static final Pattern SWITCH_TOPIC_HINT =
            Pattern.compile("换成(经营|成本|采购|营收|库存|菜品|毛利|利润)");

    private AiFollowUpHintSupport() {
    }

    /**
     * 当前句是否自带业务域（显式「换成经营|成本|…」）；为 true 时禁止当作「仅时间短句」继承上轮 path。
     * 「上个月呢」等应返回 false。
     */
    public static boolean currentMessageDeclaresDomainPath(String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return false;
        }
        return StringUtils.hasText(rawMessage.trim()) && SWITCH_TOPIC_HINT.matcher(rawMessage.replace(" ", "")).find();
    }

    /**
     * 供 Resolver 等无法注入 Bean 的场景复用。
     * 语义切换由 QuerySemanticParser 负责；此处恒不阻断（与迁出前一致）。
     */
    public static boolean pathTopicConflict(String cur, FollowUpPathKind last) {
        return topicConflict(cur, last);
    }

    private static boolean topicConflict(String cur, FollowUpPathKind last) {
        return false;
    }
}
