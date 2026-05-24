package com.nongxinle.ai.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * v2「用户语义解析」LLM 输入协议：JSON 序列化后作为 user 消息正文；
 * 生产 Resolver 调用 {@link AiQuerySemanticLlmParser#parse(SemanticParserInput)}（v2）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticParserInput {

    /** 经清洗的本轮用户问句。 */
    private String currentUserMessage;

    /** 语义「今天」锚点，ISO yyyy-MM-dd（与 {@link java.time.LocalDate} 一致）。 */
    private String today;

    /** 上一轮快照；首轮可为 null。 */
    private SemanticParserPreviousTurn previousTurn;

    /** 当前用户权限内可见门店（仅店名）。 */
    private List<SemanticParserVisibleStore> visibleStores;

    /** P2 Step 1：Intake 路由摘要（不含 wire）。 */
    private SemanticParserIntakeRouteInput semanticRoute;

    /** P2 Step 2：单域 ACTIVE allowed 摘要；capability 缺失时为 null。 */
    private SemanticParserAllowedOutputContract allowedOutputContract;
}
