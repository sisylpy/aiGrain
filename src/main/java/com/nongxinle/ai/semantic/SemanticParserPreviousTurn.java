package com.nongxinle.ai.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * v2 语义解析输入：上一轮 Run 的结构化摘要（无 SQL、无数值型组织 ID）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticParserPreviousTurn {

    private String intentCode;
    private String pathCode;
    private String structuredIntentDetail;
    private String purchaseSourceType;

    private String timeLabel;
    private String startDate;
    private String endDate;

    private String scopeType;

    private String mentionedStoreName;
    private List<String> mentionedStoreNames;

    private String mentionedDishName;
}
