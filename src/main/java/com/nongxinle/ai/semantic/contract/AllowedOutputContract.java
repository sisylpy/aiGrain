package com.nongxinle.ai.semantic.contract;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * 注入 LLM 的每轮 allowed 视图（P2：经 {@link DomainContractSelector} 写入 {@link com.nongxinle.ai.semantic.SemanticParserInput}）。
 */
@Value
@Builder
public class AllowedOutputContract {

    public static final String SCHEMA_VERSION_V1 = "allowed_output_contract.v1";

    @Builder.Default
    String schemaVersion = SCHEMA_VERSION_V1;

    @Singular("candidateDomain")
    List<String> candidateDomains;

    @Singular("entry")
    List<SemanticCapabilityContract> entries;

    @Singular("globalRule")
    List<String> globalRules;
}
