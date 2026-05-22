package com.nongxinle.ai.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** v2 parser 输入：Step 1 路由摘要（不含 wire）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticParserRouteInput {

    private String primaryDomain;
    private List<String> candidateDomains;
    private String routeType;
    private Double confidence;
}
