package com.nongxinle.ai.followup.rewrite.llm;

import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.followup.rewrite.FollowUpRewriteRequest;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * LLM rewrite 输出通用质量校验（非业务 if/else 模板）。
 * <p>
 * 拒绝半完整补全：仍带省略语气、或仅在原句前拼接时间/范围。
 */
public final class LlmFollowUpRewriteQualityValidator {

    private static final String[] COMPLETION_MARKERS = {
        "多少",
        "是多少",
        "有几",
        "有没有",
        "是否",
        "谁",
        "为什么",
        "怎么样",
        "如何",
        "哪个",
        "哪些",
        "哪一家",
        "排行",
        "最高",
        "最低",
        "对比",
        "比较",
        "能不能"
    };

    private LlmFollowUpRewriteQualityValidator() {}

    /**
     * @return rejection reason code for debug, or null if acceptable
     */
    public static String rejectReason(FollowUpRewriteRequest request, String completedUserQuery) {
        if (!StringUtils.hasText(completedUserQuery)) {
            return "empty_completed_query";
        }
        String completed = completedUserQuery.trim();
        String raw = request != null && StringUtils.hasText(request.getNormalizedUserMessage())
                ? request.getNormalizedUserMessage().trim()
                : null;

        if (stillElliptical(completed)) {
            return "still_ellipsis";
        }
        if (StringUtils.hasText(raw) && prefixOnlyAppend(raw, completed, request)) {
            return "prefix_only_append";
        }
        if (request != null && request.isHasPreviousTurn() && StringUtils.hasText(raw)) {
            if (compact(completed).equals(compact(raw))) {
                return "unchanged_from_raw";
            }
        }
        if (hasUnresolvedDeictic(completed, raw, request)) {
            return "unresolved_deictic";
        }
        String scopeLeak = scopePivotStoreLeakReason(completed, raw, request);
        if (scopeLeak != null) {
            return scopeLeak;
        }
        return null;
    }

    private static boolean hasUnresolvedDeictic(String completed, String raw, FollowUpRewriteRequest request) {
        String c = normalizeQualifiedDeictic(completed, request);
        if (!StringUtils.hasText(c)) {
            return true;
        }
        if (c.contains("这个商品") || c.contains("那个商品")) {
            return true;
        }
        if (c.contains("这个菜") || c.contains("那个菜")) {
            return true;
        }
        if (StringUtils.hasText(raw)) {
            String rawC = compact(raw);
            if (rawC.contains("这个商品") && c.contains("这个商品")) {
                return true;
            }
            if (rawC.contains("那个商品") && c.contains("那个商品")) {
                return true;
            }
            if (rawC.contains("这个菜") && c.contains("这个菜")) {
                return true;
            }
            if (rawC.contains("那个菜") && c.contains("那个菜")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当 completed 已含 resultAnchors 中的 GOODS/DISH 名称，且指代词紧跟其后（如「海天5度白醋这个商品」），
     * 视为已锚定，去掉冗余指代后再做 deictic 校验。
     */
    private static String normalizeQualifiedDeictic(String completed, FollowUpRewriteRequest request) {
        String c = compact(completed);
        List<String> names = collectQualifyingAnchorNames(request);
        if (names.isEmpty()) {
            return c;
        }
        names.sort(Comparator.comparingInt(String::length).reversed());
        for (String name : names) {
            String nc = compact(name);
            if (!StringUtils.hasText(nc) || !c.contains(nc)) {
                continue;
            }
            c = c.replace(nc + "这个商品", nc);
            c = c.replace(nc + "那个商品", nc);
            c = c.replace(nc + "这个菜", nc);
            c = c.replace(nc + "那个菜", nc);
        }
        return c;
    }

    private static List<String> collectQualifyingAnchorNames(FollowUpRewriteRequest request) {
        if (request == null || request.getResultAnchors() == null || request.getResultAnchors().isEmpty()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (AiResultAnchor anchor : request.getResultAnchors()) {
            if (anchor == null || !StringUtils.hasText(anchor.getEntityName())) {
                continue;
            }
            String type = anchor.getEntityType();
            if (AiResultAnchor.ENTITY_TYPE_GOODS.equals(type)
                    || AiResultAnchor.ENTITY_TYPE_DISH.equals(type)) {
                names.add(anchor.getEntityName().trim());
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * 单店 scope pivot 补全后不得仍含其它 visibleStores 店名。
     */
    private static String scopePivotStoreLeakReason(
            String completed, String raw, FollowUpRewriteRequest request) {
        if (request == null || !StringUtils.hasText(raw) || !StringUtils.hasText(completed)) {
            return null;
        }
        List<String> visible = request.getVisibleStoreNames();
        if (visible == null || visible.size() < 2) {
            return null;
        }
        List<String> mentionedInRaw = visibleStoresMentionedInMessage(raw, visible);
        if (mentionedInRaw.size() != 1) {
            return null;
        }
        String target = mentionedInRaw.get(0);
        for (String store : visible) {
            if (!StringUtils.hasText(store) || store.trim().equals(target)) {
                continue;
            }
            if (messageContainsStoreName(completed, store.trim())) {
                return "scope_pivot_leaked_stores";
            }
        }
        return null;
    }

    private static List<String> visibleStoresMentionedInMessage(String message, List<String> visible) {
        if (!StringUtils.hasText(message) || visible == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String store : visible) {
            if (!StringUtils.hasText(store)) {
                continue;
            }
            if (messageContainsStoreName(message, store.trim())) {
                out.add(store.trim());
            }
        }
        return out;
    }

    private static boolean messageContainsStoreName(String message, String storeName) {
        if (!StringUtils.hasText(message) || !StringUtils.hasText(storeName)) {
            return false;
        }
        String compactMsg = compact(message);
        String compactName = compact(storeName);
        return StringUtils.hasText(compactName) && compactMsg.contains(compactName);
    }

    private static boolean stillElliptical(String text) {
        String c = compact(text);
        if (!StringUtils.hasText(c)) {
            return true;
        }
        if (c.endsWith("呢") || c.endsWith("呢?") || c.endsWith("呢？")) {
            if (!hasCompletionMarker(c)) {
                return true;
            }
        }
        if (c.matches(".*那[^？?]{0,24}呢[？?]?$") && !hasCompletionMarker(c)) {
            return true;
        }
        if (c.matches(".*(这个|那个)[^？?]{0,12}呢[？?]?$") && !hasCompletionMarker(c)) {
            return true;
        }
        if ((c.equals("这个") || c.equals("那个") || c.endsWith("这个呢") || c.endsWith("那个呢"))
                && !hasCompletionMarker(c)) {
            return true;
        }
        return false;
    }

    private static boolean hasCompletionMarker(String compactText) {
        for (String marker : COMPLETION_MARKERS) {
            if (compactText.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean prefixOnlyAppend(
            String raw, String completed, FollowUpRewriteRequest request) {
        String rawC = compact(raw);
        String completedC = compact(completed);
        if (!StringUtils.hasText(rawC) || !StringUtils.hasText(completedC)) {
            return false;
        }
        if (completedC.equals(rawC)) {
            return stillElliptical(rawC);
        }
        if (completedC.endsWith(rawC) && stillElliptical(rawC)) {
            return true;
        }
        String core = stripInheritedContext(completed, request);
        String coreC = compact(core);
        if (!StringUtils.hasText(coreC)) {
            return false;
        }
        String rawDeicticStripped = stripLeadingDeictic(rawC);
        if (coreC.equals(rawC) || coreC.equals(rawDeicticStripped)) {
            return stillElliptical(coreC);
        }
        return coreC.endsWith(rawDeicticStripped) && stillElliptical(rawDeicticStripped);
    }

    private static String stripInheritedContext(String completed, FollowUpRewriteRequest request) {
        String s = completed;
        if (request == null) {
            return s;
        }
        Set<String> prefixes = new LinkedHashSet<>();
        if (StringUtils.hasText(request.getPreviousTimeLabel())) {
            prefixes.add(request.getPreviousTimeLabel().trim());
        }
        if (StringUtils.hasText(request.getPreviousStartDate())
                && StringUtils.hasText(request.getPreviousEndDate())) {
            prefixes.add(request.getPreviousStartDate().trim() + "至" + request.getPreviousEndDate().trim());
            prefixes.add(request.getPreviousStartDate().trim() + "到" + request.getPreviousEndDate().trim());
        }
        if (StringUtils.hasText(request.getPreviousMentionedStoreName())) {
            prefixes.add(request.getPreviousMentionedStoreName().trim());
        }
        List<String> visible = request.getVisibleStoreNames();
        if (visible != null) {
            for (String name : visible) {
                if (StringUtils.hasText(name)) {
                    prefixes.add(name.trim());
                }
            }
        }
        String compact = compact(s);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String p : prefixes) {
                if (!StringUtils.hasText(p)) {
                    continue;
                }
                String pc = compact(p);
                if (compact.startsWith(pc)) {
                    compact = compact.substring(pc.length());
                    changed = true;
                }
                if (compact.startsWith(pc + "、")) {
                    compact = compact.substring(pc.length() + 1);
                    changed = true;
                }
                if (compact.startsWith(pc + ",")) {
                    compact = compact.substring(pc.length() + 1);
                    changed = true;
                }
            }
        }
        return compact;
    }

    private static String stripLeadingDeictic(String compact) {
        if (!StringUtils.hasText(compact)) {
            return compact;
        }
        if (compact.startsWith("那")) {
            return compact.substring(1);
        }
        if (compact.startsWith("这个")) {
            return compact.substring(2);
        }
        if (compact.startsWith("那个")) {
            return compact.substring(2);
        }
        return compact;
    }

    private static String compact(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replace(" ", "").replace("\u3000", "").trim();
    }
}
