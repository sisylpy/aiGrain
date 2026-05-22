package com.nongxinle.ai.semantic.contract;

import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Strict 模式澄清问题工厂（P2.6 预留；未接入主链 enforce）。
 * <p>仅根据 {@link SemanticContractViolationCode} 与合同层 debug 字段生成用户可读问句；
 * 不做 Java 猜测、不做 alias、不做合同外 wire 归一。
 */
public final class SemanticContractClarificationQuestionFactory {

    private static final Map<String, String> DOMAIN_LABELS =
            Map.ofEntries(
                    Map.entry("PURCHASE", "采购"),
                    Map.entry("REVENUE", "营业额"),
                    Map.entry("STOCK_REDUCE", "出库/核销"),
                    Map.entry("WAREHOUSE", "库存"),
                    Map.entry("DISH_SALES", "菜品销量"),
                    Map.entry("DISH_PROFIT", "菜品毛利"),
                    Map.entry("BUSINESS_DIAGNOSIS", "经营诊断"));

    private SemanticContractClarificationQuestionFactory() {
    }

    public static String buildQuestion(SemanticContractClarificationRequest request) {
        if (request == null || request.getViolationCode() == null) {
            return defaultClarification();
        }
        return switch (request.getViolationCode()) {
            case UNSUPPORTED_WIRE -> unsupportedWireQuestion(request);
            case UNSUPPORTED_SLOT_COMBO -> unsupportedSlotComboQuestion(request);
            case MISSING_REQUIRED_SLOT -> missingRequiredSlotQuestion(request);
            case ANCHOR_CONTRACT_MISMATCH -> anchorMismatchQuestion(request);
            case NO_CAPABILITY_CONTRACT -> noCapabilityContractQuestion(request);
            case ROUTE_UNKNOWN -> routeUnknownQuestion();
            case ROUTE_AMBIGUOUS -> routeAmbiguousQuestion(request);
            case PLANNED_CAPABILITY_SELECTED ->
                    "你问的能力尚在规划中，请换一种已支持的问法，或说明你想查总览、排行还是明细。";
            case MODEL_CONTRACT_VIOLATION -> defaultClarification();
        };
    }

    /** 由 {@link SemanticContractStrictDecision} 直接生成澄清问句。 */
    public static String buildQuestion(SemanticContractStrictDecision decision) {
        if (decision == null) {
            return defaultClarification();
        }
        if (StringUtils.hasText(decision.getClarificationQuestion())) {
            return decision.getClarificationQuestion().trim();
        }
        return buildQuestion(
                SemanticContractClarificationRequest.builder()
                        .violationCode(decision.getViolationCode())
                        .selectedDomain(decision.getSelectedDomain())
                        .unsupportedWire(decision.getUnsupportedWire())
                        .missingSlots(decision.getMissingSlots())
                        .candidateDomains(decision.getCandidateDomains())
                        .build());
    }

    private static String unsupportedWireQuestion(SemanticContractClarificationRequest request) {
        String domain = domainLabel(request.getSelectedDomain());
        if ("采购".equals(domain)) {
            return "我没有识别清楚你想查采购的哪类数据，是采购总览、商品排行、供货商排行，还是采购异常？";
        }
        if ("库存".equals(domain)) {
            return "我没有识别清楚你想查库存的哪类数据，是库存总览、商品库存排行、门店库存排行，还是缺货/补货风险？";
        }
        if ("菜品销量".equals(domain)) {
            return "我没有识别清楚你想查菜品销量的哪类数据，是销量排行、销售额排行，还是某个具体菜品？";
        }
        return "这个问题当前不在系统已登记的数据查询能力里，请确认你想查的是"
                + domain
                + "的总览、排行还是明细。";
    }

    private static String unsupportedSlotComboQuestion(SemanticContractClarificationRequest request) {
        String domain = domainLabel(request.getSelectedDomain());
        if ("采购".equals(domain)) {
            return "我识别到你想查采购，但查询对象、指标或口径不完整，请确认你想按商品、供货商还是门店查看？";
        }
        if ("库存".equals(domain)) {
            return "我识别到你想查库存，但查询对象、指标或口径不完整，请确认你想看总览、商品排行还是门店排行？";
        }
        return "我识别到你想查"
                + domain
                + "，但查询对象、指标或口径不完整，请补充你想按什么维度查看。";
    }

    private static String missingRequiredSlotQuestion(SemanticContractClarificationRequest request) {
        List<String> missing = request.getMissingSlots();
        if (missing != null && !missing.isEmpty()) {
            String slots = String.join("、", missing);
            return "为了准确查询，还需要确认：" + slots + "。请补充后再试。";
        }
        return "为了准确查询，还需要补充查询对象或指标，请说明你想查什么。";
    }

    private static String anchorMismatchQuestion(SemanticContractClarificationRequest request) {
        return "这个问题需要指定具体对象（例如某商品、某门店或上一轮结果），请补充你想查的是哪一个。";
    }

    private static String noCapabilityContractQuestion(SemanticContractClarificationRequest request) {
        String domain = domainLabel(request.getSelectedDomain());
        return "当前还没有为「" + domain + "」登记可查询能力合同，请换一种问法或选择其他业务域。";
    }

    private static String routeUnknownQuestion() {
        return "我还不能确定你想查哪类业务，请说明是想看采购、出库、库存、营业额、菜品还是经营诊断。";
    }

    private static String routeAmbiguousQuestion(SemanticContractClarificationRequest request) {
        String options = formatDomainOptions(request.getCandidateDomains());
        if (StringUtils.hasText(options)) {
            return "这个问题可能涉及多个业务域，请确认你想查" + options + "？";
        }
        return "这个问题可能涉及多个业务域，请确认你想查采购、出库、库存、营业额还是菜品？";
    }

    private static String defaultClarification() {
        return "我还不能准确理解你的问题，请补充你想查的业务对象和时间范围。";
    }

    private static String domainLabel(String domainCode) {
        if (!StringUtils.hasText(domainCode)) {
            return "该业务";
        }
        return DOMAIN_LABELS.getOrDefault(domainCode.trim().toUpperCase(Locale.ROOT), domainCode.trim());
    }

    private static String formatDomainOptions(List<String> candidateDomains) {
        if (candidateDomains == null || candidateDomains.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (String code : candidateDomains) {
            if (StringUtils.hasText(code)) {
                labels.add(domainLabel(code));
            }
        }
        if (labels.isEmpty()) {
            return null;
        }
        return String.join("、", labels);
    }

    @Value
    @Builder
    public static class SemanticContractClarificationRequest {
        SemanticContractViolationCode violationCode;
        String selectedDomain;
        String unsupportedWire;
        List<String> missingSlots;
        List<String> candidateDomains;
    }
}
