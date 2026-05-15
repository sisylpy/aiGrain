package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDiagnosisCompositeAnswerPlanDebug {

    private String builderVersion;
    private String sourceTraceFingerprint;
    @Builder.Default
    private Map<String, Object> mappingNotes = new LinkedHashMap<>();
}
