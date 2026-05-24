package com.nongxinle.ai.semantic.intake;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticIntakeSubQuestion {

    private Integer index;
    private String canonicalQuestion;
    private String primaryDomain;
    private List<String> candidateDomains;
    private String routeType;
    private Double confidence;
    private Boolean needClarification;
    private String clarificationQuestion;
    private String reason;
}
