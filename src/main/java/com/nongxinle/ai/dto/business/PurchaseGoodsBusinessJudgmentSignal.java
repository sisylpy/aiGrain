package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 原料采购经营分析：结构化判断信号（Composer 只读，不写 NL 结论）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseGoodsBusinessJudgmentSignal {

    public static final String SEVERITY_INFO = "INFO";
    public static final String SEVERITY_WARN = "WARN";

    private String code;
    private String severity;
    @Builder.Default
    private List<String> evidenceRefs = new ArrayList<>();
}
