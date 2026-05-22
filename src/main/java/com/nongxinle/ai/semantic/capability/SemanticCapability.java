package com.nongxinle.ai.semantic.capability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.function.BiPredicate;

/**
 * 一条可注册业务能力（Phase 1：采购下钻）；匹配谓词在注册时注入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticCapability {

    private String capabilityId;
    private String description;
    private String targetPurchasePlanType;
    private String queryMode;
    private BiPredicate<SemanticContextFrame, SemanticCapabilitySlot> matcher;
}
