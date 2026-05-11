package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiBusinessOverviewResult;
import com.nongxinle.ai.dto.business.AiDishProfitDishBrief;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.AiOverviewCoveredStoreItem;
import com.nongxinle.ai.dto.business.AiOverviewStoreIssueItem;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiNumericPlainText;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answer Composer：汇入 Tool/诊断结果与 DeepSeek 生成最终自然语言。<br>
 * 有结构化卡片（成本诊断 / 经营概览）时，正文只输出短口语化结论，明细由前端卡片承载。<br>
 * DeepSeek 经 {@link LlmGateway} 接入；仅占位时请设 {@code ai.agent.llm.stub=true}
 *（{@link com.nongxinle.ai.gateway.PlaceholderLlmGateway}）。
 */
@Component
@RequiredArgsConstructor
public class StubAnswerComposerNode implements AgentNode {

    private static final Pattern PREV_TURN_PURCHASE_CARRY_PREFIX =
            Pattern.compile("^carry_po=(\\d+),carry_amt=([^|]+)\\|");

    private record PurchaseCarryHint(int orderCount, String amountToken) {
    }

    private static final int MAX_FALLBACK_FINDINGS = 3;
    private static final int MAX_FALLBACK_RECOMMENDATIONS = 3;

    /** 库存重量对用户展示单位（与业务库存字段常见口径一致）。 */
    private static final String W_STOCK_WEIGHT_UNIT = "斤";

    private static String fmtStockWeightCn(Object value) {
        return plainNumericHint(value) + " " + W_STOCK_WEIGHT_UNIT;
    }

    /** 写入「剩余 {n} 斤」时中间的数字部分（斤前留空格）。 */
    private static String stockWeightNumberOnly(Object value) {
        return plainNumericHint(value);
    }

    private static boolean warehouseOverviewHasVisibleWarehouses(Map<String, Object> wo) {
        if (wo == null) {
            return false;
        }
        Object v = wo.get("visibleWarehouses");
        return v instanceof List<?> l && !l.isEmpty();
    }

    private static final String COST_COMPOSER_SYSTEM =
            "你是餐饮集团 AI 经营顾问。前端「成本诊断卡片」已展示：风险、摘要、关键指标、发现问题、建议动作、是否需要更多数据等完整结构化内容。\n"
                    + "\n"
                    + "你的任务：把下面 JSON 里的诊断要点改写成老板能一眼看完的短回复。\n"
                    + "硬性要求：\n"
                    + "- 只用中文简体；不要输出 JSON、代码块、不要用「##」类标题。\n"
                    + "- 不要复述卡片中已有的整段关键指标明细，不要逐条抄写数值表。\n"
                    + "- 正文结构：① 一句话结论；② 至多 3 条重点发现；③ 至多 3 条建议动作；篇幅简短。\n"
                    + "- 文末可加一句「详细指标见下方成本诊断卡片」若语气自然；不要冗长。\n"
                    + "- 严格基于输入中的 summary、riskLevel、findings、recommendations 的含义，不编造数字。\n"
                    + "- 禁止在回答中出现 dataPlanTools、toolResults、workspaceMode 等技术词。\n";

    private static final String BUSINESS_COMPOSER_SYSTEM =
            "你是餐饮门店/集团经营 AI 助手。前端有「经营概览」结构化卡片，但聊天正文必须先让老板看清真实数字。\n"
                    + "\n"
                    + "你的任务：根据输入 JSON（含 queryScopeBanner、queryScopeCoverage、numericHeadlineText、dashboardStatsCn 摘录、摘要与发现）写短回复。\n"
                    + "硬性要求：\n"
                    + "- 只用中文简体；不要输出 JSON、代码块。\n"
                    + "- 【必须】正文第一段先复述 queryScopeBanner 与 queryScopeCoverage（若为非空字符串），明确是集团／门店范围及门店覆盖（用白话，勿出现「登记口径」「父级网点」「主体」「节点」等后台用语）；"
                    + "第二段再复述 numericHeadlineText 的具体数字（营业额、天数、日均、订单、客单、券/平台费列、退款、外卖）；"
                    + "numericHeadlineText 已含查询起止日期与「录入营业额的自然日」含义，勿擅自改写为「本月」「这个月」，除非用户问题明确指向当月；summary 字段亦勿强加「本月」。\n"
                    + "dashboardStatsCn 里有的键才可引用数值，没有的写「暂无」，禁止编造。\n"
                    + "- 【金额与数字】一律用日常十进制写法（如 30、85.4、854），禁止使用科学计数法或类似 3E+1、2E+1 的写法；也不要自行给金额加括号拆解。\n"
                    + "- 【定性】统计天数少于 5 时，禁止输出「集团经营规模较小」「规模较小」等对整体规模的武断评价；可提示样本少、不宜据此判断整体经营水平。\n"
                    + "- 【门店关注】集团广角且看板有效时，priorityStoresBrief 要么以「需要优先关注的门店：」开头列出至多 3 家原因摘要，要么整句为「当前没有识别到明显异常门店。」；禁止输出「当前未识别到需要单独点名处理的门店」或其它相近含糊话术。\n"
                    + "- 【禁止】在未见分项利润与外送成本明细时断言「外卖净贡献为负」「外卖拖累净利」或对利润下绝对结论；\n"
                    + "- 【外卖与平台费】仅当 JSON 中 overviewScope.platformFeeExceedsTakeoutRevenue 严格为 true 时，才可写「平台费/优惠券合计高于外卖营业额」或提示两者可能口径混用需核对；"
                    + "若该字段为 false、为 null 或 JSON 中无此键，禁止写「外卖营业额低于平台费」「外卖低于券费」类比较（含颠倒两金额大小），只能分别读出两列金额；需要对比时只能说「请核对后台口径」，禁止臆断谁高谁低。\n"
                    + "- 【优先】若有 priorityStoresBrief（需要优先关注的门店 Top3），在「重点观察」中点到为止复述，不要超过 3 家门店，不要展开卡片中的完整清单。\n"
                    + "- 结构：① 两句话内完成查询范围复述 + 结论（须含至少一个数字）；② 至多 3 条重点观察；③ 至多 3 条可执行建议。\n"
                    + "- keyMetrics/findings/recommendations 可作补充来源，但以 queryScopeCoverage 与 numericHeadlineText、日营收字段为准。\n"
                    + "- 不编造环比/同比或未提供的数字。\n"
                    + "- 禁止 dataPlanTools、toolResults、workspaceMode、英文字段键名等与用户无关的词。\n";

    /** 前端可有菜品毛利结构化卡片；正文须引用真实菜名与销售/毛利率可读串，严禁编造明细。 */
    private static final String DISH_PROFIT_COMPOSER_SYSTEM =
            "你是餐饮门店/集团菜品经营顾问。输入为「菜品毛利透视」结构化 JSON。\n"
                    + "硬性要求：\n"
                    + "- 只用中文简体；不要输出 JSON、代码块、不要用「##」标题。\n"
                    + "- 【开篇范围】必须使用 queryScopeBanner（若为非空）：逐字复述其核心含义（集团/门店、可见门店家数与店名、参与统计门店与缺数据门店）；"
                    + "禁止使用「下面按你可查看的门店菜品数据」等模糊句替代 queryScopeBanner。"
                    + "若 queryScopeBanner 为空，再用一句说明当前为门店视角。\n"
                    + "- 【综合结论】复述 summary 中的销售额、理论成本、实际成本、毛利额、综合毛利率数字；"
                    + "若 grossProfitRateUncertain=true，必须说明这是按当前可取得成本的粗算参考，不能当作已审计的最终毛利结论，不得同时写「已准确计算」「非常准确」之类措辞。\n"
                    + "- 【三段菜品】必须分三块叙述，标题用简短中文句首，不用 markdown：\n"
                    + "  A）毛利表现较好的菜：仅列 reliableProfitDishes（或 topProfitDishes，二者一致），只含成本口径相对完整的菜；逐条含菜名、销量、销售额、理论成本、实际成本、毛利率要点。\n"
                    + "  B）需要关注的低毛利或成本偏高菜：列 lowProfitDishes，含原因（可引用 riskReason）。\n"
                    + "  C）成本数据不完整的菜：列 costDataIncompleteDishes，说明缺 BOM/出库核销等，明确当前显示的高毛利率（如 100%）不可靠；不得把 C 类菜放进 A 类。\n"
                    + "  若 B）/C）某块列表为空可写「暂无」；若 A）列表为空须写「该统计周期内暂未识别到成本数据完整且毛利表现突出的菜品」（勿仅写「暂无」）。\n"
                    + "- riskLevel=data_incomplete 时不得与「综合毛利率约 X%」的确定性语气矛盾：应改为「仅基于可见行的粗算」并指向 costDataIncompleteDishes。\n"
                    + "- 金额与份量用口语十进制写法，禁用科学计数法。\n"
                    + "- 禁止 dataPlanTools、toolResults、workspaceMode、grossMarginRateOnListPrice 等内部键名或未解释英文字段。\n";

    private static final String GENERIC_CHAT_SYSTEM =
            "你是餐饮集团 AI 顾问，面向店长、集团管理、库房与门店采购等业务同事说话。\n"
                    + "- 仅用自然中文简体；勿输出 JSON / 代码块；勿复述英文字段。\n"
                    + "- **禁止**在答复中出现下列任何字眼或同类意思：dataPlanTools、toolResults、workspaceMode、"
                    + "BUSINESS_CHAT、「系统尚未执行任何数据查询工具」「当前时间窗口是」「子树范围」等开发与调试话术。\n"
                    + "- 禁止使用「建议您补充口径」「请用户收窄问题并补充口径」等生硬措辞；说不清时可引导用户换一种问法。\n"
                    + "- 若当前上下文不足以形成经营或成本概要，请先说明「当前可用数据不足，暂时无法给出完整分析」，"
                    + "再给出一两条可操作的检查方向（如对账月份、选对门店归属等）；不要推断内部执行状态。\n";

    private static final String GENERIC_CHAT_EMPTY_LLM_FALLBACK =
            "当前可用数据不足，暂时无法给出完整分析。";

    private static final String PURCHASE_COMPOSER_SYSTEM =
            "你是餐饮供应链顾问。用户可能使用「经营怎么样」等话术，但若上下文标明「门店采购角色」或「经营概览已切换为采购视角」，则回答必须严格限定在采购入库与核销/出库摘要。\n"
                    + "硬性要求：\n"
                    + "- 用中文简体短回复；仅覆盖输入中给出的采购/核销数字；不编造。\n"
                    + "- **总览数据**：须写明统计周期内采购入库「笔数」与「总金额（元）」；**勿**向用户报告「采购总重量」或把不同单位混成「斤」汇总。\n"
                    + "- **采购方式**：若 JSON「采购概览」中 purchaseNarrativeMode 为 purchase_source_amount_query，"
                    + "用一两句话直接给出金额与笔数，可附带至多两个「金额最高」单品；"
                    + "**禁止**输出商品频次完整排行、核销分项长段、门店覆盖复述、采购方式「其中」拆分或「其中自采/供货商」重复句式"
                    + "（数据可能已是来源过滤后的结果）。\n"
                    + "- **采购方式（其它）**：若 purchaseNarrativeMode 不是 purchase_source_amount_query，且 purchaseMethodBreakdownSupported 为 true 且含 purchaseMethodSummaryFragment，"
                    + "须在总金额后接「其中」+ 该片段（笔数与金额与字段一致）；若为 false 但有 purchaseMethodNote，用一两句人话说明暂不按方式拆分即可，勿编方式占比。\n"
                    + "- **商品频次/金额**：须含「次」与「元」；频次列表用 goodsPurchaseFrequencyTop（每项 purchaseTimes），金额列表用 goodsPurchaseAmountTop 或 highAmountItems（purchaseSubtotal）。勿写「采购次数最多的是A、B等」而无具体次数。\n"
                    + "- 若 JSON 含「集团门店采购覆盖说明_须向用户复述」，须完整引用该句，勿改写店名与分支结论。\n"
                    + "- 供货商名称沿用输入；不得自拟「供货商-1」类假名；若已为「未维护供货商名称」或「供货商ID…（名称未维护）」则照读。\n"
                    + "- 若 purchaseNarrativeMode（或工具概览中的同义字段）为供货商/供应商「金额排行」（supplier_amount_ranking），"
                    + "只允许输出：时间范围 + 查询范围一句 + 名次列表（采购金额元、笔数）+ 真实供货商家数一句；"
                    + "禁止复述全部采购总金额、自采/供货商拆分、单品频次或金额排行、核销分项、采购方式「其中」片段、尾段建议。\n"
                    + "- 若核销各分项均为 0 或上下文仅说明「统计周期内暂无核销/出库记录」，勿再罗列「均为0」式排比句。\n"
                    + "- 若有核销非零：可用「核销方面：生产耗用…元，出品…元，废弃…元，损耗（亦称报损）…元，退货…元。」\n"
                    + "- 禁止出现或暗示：总营业额、日均营业额、订单数、客单价、毛利率、利润、经营规模、集团经营情况等完整经营指标。\n"
                    + "- 禁止 dataPlanTools、toolResults、workspaceMode、蛇形英文名工具代号、purchaseMethodBreakdownSupported 等技术字段名照抄给用户；只用中文叙述。\n";

    private static final String WAREHOUSE_COMPOSER_SYSTEM =
            "你是餐饮库房与库存管理顾问。用户可能用「这个月经营怎么样」或「库存怎么样」提问；若上下文标明库房端、门店库存视角或集团库存汇总（scopeType=GROUP），则回答只能围绕：当前库存商品种数与批次规模、库存剩余金额与重量、查询区间内入库金额与入库重量、核销与出库分型（生产耗用、废弃、损耗、退货），以及分三段输出的关注清单。\n"
                    + "硬性要求：\n"
                    + "- 中文简体短回复；仅用输入中的数字与清单；不编造。\n"
                    + "- **称谓与开篇**：必须严格遵守输入 JSON 中的「称谓与开篇_模型须严格遵守」；若与该条矛盾，以该字段为准；被要求客观开篇或无称呼时，勿用「店长」「老板」等硬套对方岗位，亦勿用「库管」称呼对方。\n"
                    + "- 【重量】必须写成「剩余 0.7 斤」或「重量约 9.20（单位见字段）」；禁止「9.20重量」「剩余重量9.20」等老板难懂的拼接。\n"
                    + "- 【三段清单】须按顺序分块标题输出：「低库存 / 需补货」「库存偏高 / 建议优先消耗」「早入库批次 / 建议盘点」；同一商品**禁止**同时出现在低库存与积压两类（输入 JSON 已去重，你也不得把同一商品写进两类）。\n"
                    + "- 若 scopeType=GROUP 或上下文写明集团汇总：开篇明确为集团下属门店范围，若上下文中同时出现库房视角再写「门店/库房」；**禁止**反问用户指定哪家门店或品类；不得输出营业额、订单、客单价、毛利、利润、菜品销售收入。\n"
                    + "- 第一段复述摘要中的核心数字；库存权重若有 weightDisplayUnit 字段须与摘要一致，勿擅自改成斤。\n"
                    + "- 禁止在商品名后加「（积压）」等与分类重复的标记。\n"
                    + "- 禁止营业额、订单、客单价、毛利、利润、集团经营概况、菜品销售收入；不要把主线写成采购员式的供应商分析或采购议价话术。\n"
                    + "- 禁止 dataPlanTools、toolResults、workspaceMode 等技术词。\n";

    /** 按岗位去掉不当的「店长」寒暄（与 {@link #warehouseSalutationDirective} 一致）。 */
    private static final Pattern WAREHOUSE_LEADING_SALUTATION =
            Pattern.compile("^((好的|嗯|您好)[，,\\s]*)?((亲爱的)?店长)([，,。．、:\\s]+|(?=本库房|以下是|以下按|当前|说明|目前|共有))");

    private static final Pattern WAREHOUSE_LEADING_MANAGER_NO_PUNCT =
            Pattern.compile("^店长(?=本库房|以下是|以下按|当前|说明|目前|共有)");

    private final LlmGateway llmGateway;
    private final AiSseEventPublisher publisher;

    @Override
    public String name() {
        return "AnswerComposer";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return true;
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "AnswerComposerNode",
                "displayText", "正在生成自然语言回答…"
        ));

        String scopeP = AiAnswerBoundary.scopeConvergencePrefix(state.getScopeConvergenceNote());
        String intentP = AiAnswerBoundary.costIntentConvergencePrefix(state.getCostIntentConvergenceNote());
        String permPrefix = AiAnswerBoundary.composeHumanPrefix(state.getPermissionDenials());
        String boundaryNote = "";
        if (state.getResolvedQueryContext() != null) {
            String n = state.getResolvedQueryContext().getAnswerBoundaryNote();
            if (n != null && !n.isBlank()) {
                boundaryNote = n.trim();
            }
        }

        String answer;
        if (state.isNeedClarification() && state.getClarificationQuestion() != null
                && !state.getClarificationQuestion().isBlank()) {
            answer = state.getClarificationQuestion().trim();
        } else if (state.isCouponCostInsightBlocked()) {
            answer = "";
        } else if (state.getCostDiagnosisResult() != null) {
            AiCostDiagnosisResult d = state.getCostDiagnosisResult();
            String llm = llmGateway.chatSimple(COST_COMPOSER_SYSTEM, JSON.toJSONString(compactCostPayload(state, d)));
            answer = pickLlmSanitized(llm, shortFallbackCost(d));
        } else if (state.isDishProfitPath()) {
            AiDishProfitOverviewResult dp = state.getDishProfitOverviewResult();
            if (dp != null) {
                if (dishProfitUseDeterministicSummaryOnly(state)) {
                    answer = pickLlmSanitized("", shortFallbackDishProfit(dp, state));
                } else {
                    String llm = llmGateway.chatSimple(DISH_PROFIT_COMPOSER_SYSTEM,
                            JSON.toJSONString(compactDishProfitPayload(state, dp)));
                    answer = pickLlmSanitized(llm, shortFallbackDishProfit(dp, state));
                }
            } else {
                answer = shortFallbackDishProfit(null, state);
            }
        } else if (state.getBusinessOverviewResult() != null) {
            AiBusinessOverviewResult o = state.getBusinessOverviewResult();
            String llm = llmGateway.chatSimple(BUSINESS_COMPOSER_SYSTEM, JSON.toJSONString(compactBusinessPayload(state, o)));
            answer = pickLlmSanitized(llm, shortFallbackBusiness(state, o));
        } else if (state.isWarehouseStockOverviewPath()) {
            Map<String, Object> woForSalutation = extractWarehouseOverviewPayload(state);
            LinkedHashMap<String, Object> whCtx = summarizeWarehouseToolPresenceCn(state);
            state.setWarehouseOverview(buildWarehouseOverviewStructured(state));
            String fb = warehouseStockFallback(state);
            String llmRaw = "";
            try {
                String payload;
                try {
                    payload = JSON.toJSONString(whCtx);
                } catch (Exception jsonEx) {
                    payload = "{}";
                }
                llmRaw = llmGateway.chatSimple(WAREHOUSE_COMPOSER_SYSTEM, payload);
            } catch (Exception ignored) {
                llmRaw = "";
            }
            String llmUse = llmLooksUnavailable(llmRaw) ? "" : llmRaw;
            answer = pickLlmSanitized(llmUse, fb);
            answer = applyWarehouseSalutationPolicy(answer, state, woForSalutation);
        } else if (state.isStockReduceQueryPath()) {
            answer = stockReduceQueryDeterministicFallback(state);
        } else if (state.isPurchaseCostInsightPath()) {
            Map<String, Object> poRaw = extractPurchaseOverviewPayload(state);
            if (!poRaw.isEmpty()) {
                state.setPurchaseOverview(new LinkedHashMap<>(poRaw));
            }
            boolean deterministicPurchaseOnly = shouldForceDeterministicPurchaseAnswer(state);
            String llm = "";
            if (!deterministicPurchaseOnly) {
                LinkedHashMap<String, Object> purchaseCtx = new LinkedHashMap<>();
                purchaseCtx.put("用户问题", nz(state.getNormalizedUserInput()));
                purchaseCtx.putAll(summarizePurchaseToolPresenceCn(state));
                try {
                    llm = llmGateway.chatSimple(PURCHASE_COMPOSER_SYSTEM, JSON.toJSONString(purchaseCtx));
                } catch (Exception ignored) {
                    llm = "";
                }
            }
            answer = pickLlmSanitized(llm, purchaseCostFallback(state));
        } else {
            LinkedHashMap<String, Object> ctx = composeSafeFallbackContext(state);
            String llmOnly = llmGateway.chatSimple(GENERIC_CHAT_SYSTEM, JSON.toJSONString(ctx));
            answer = pickLlmSanitized(llmOnly, GENERIC_CHAT_EMPTY_LLM_FALLBACK);
        }
        StringBuilder head = new StringBuilder();
        if (!boundaryNote.isEmpty()) {
            head.append(boundaryNote);
        }
        if (!scopeP.isBlank()) {
            if (head.length() > 0) {
                head.append('\n');
            }
            head.append(scopeP.trim());
        }
        if (!intentP.isBlank()) {
            if (head.length() > 0) {
                head.append('\n');
            }
            head.append(intentP.trim());
        }
        if (!permPrefix.isBlank()) {
            if (head.length() > 0) {
                head.append('\n');
            }
            head.append(permPrefix.trim());
        }
        if (head.length() > 0) {
            answer = head + (answer.isEmpty() ? "" : "\n" + answer);
        }
        state.setFinalAnswerText(AiAnswerBoundary.stripDeveloperFacingLeakage(answer.trim()));

        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "AnswerComposerNode",
                "displayText", "回答草稿已就绪",
                "hasStructuredCostDiagnosis", state.getCostDiagnosisResult() != null,
                "hasDishProfitOverview", state.getDishProfitOverviewResult() != null,
                "hasBusinessOverview", state.getBusinessOverviewResult() != null,
                "purchaseCostInsightPath", state.isPurchaseCostInsightPath(),
                "warehouseStockOverviewPath", state.isWarehouseStockOverviewPath(),
                "stockReduceQueryPath", state.isStockReduceQueryPath()
        ));
        return state;
    }

    private static String pickLlmSanitized(String llm, String fallback) {
        String picked = llm == null || llm.isBlank() ? fallback : llm.trim();
        return AiAnswerBoundary.stripDeveloperFacingLeakage(picked);
    }

    /**
     * {@link com.nongxinle.ai.DeepSeekCompletionClient} 在 HTTP/解析异常时返回「抱歉…」短句；
     * 库存链路必须以工具摘要作答，不能把该句当作正式回复。
     */
    private static boolean llmLooksUnavailable(String llm) {
        if (llm == null || llm.isBlank()) {
            return true;
        }
        String t = llm.trim();
        return t.startsWith("抱歉，AI 服务")
                || t.contains("AI 服务出现异常")
                || t.contains("AI 服务暂时不可用")
                || t.startsWith("AI 未返回有效");
    }

    /**
     * 不含 keyMetrics：指标由前端 costDiagnosis 卡片展示，避免模型在正文复述。
     */
    private static Map<String, Object> compactCostPayload(AiRunState state, AiCostDiagnosisResult d) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("hint", "成本诊断卡片已含关键指标；聊天正文勿重复罗列指标数值");
        m.put("userQuestion", nz(state.getNormalizedUserInput()));
        m.put("summary", d.getSummary());
        m.put("riskLevel", d.getRiskLevel());
        m.put("needMoreData", d.getNeedMoreData());
        m.put("findings", d.getFindings());
        m.put("recommendations", d.getRecommendations());
        m.put("questions", d.getQuestions());
        return m;
    }

    private static Map<String, Object> compactDishProfitPayload(AiRunState state, AiDishProfitOverviewResult dp) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("queryScopeBanner", nz(dp.getQueryScopeBanner()));
        m.put("scopeIntro", nz(state.getScopeConvergenceNote()));
        m.put("userQuestion", nz(state.getNormalizedUserInput()));
        m.put("scopeType", nz(dp.getScopeType()));
        m.put("scopeName", nz(dp.getScopeName()));
        m.put("visibleStores", dp.getVisibleStores() == null ? List.of() : dp.getVisibleStores());
        m.put("coveredStores", dp.getCoveredStores() == null ? List.of() : dp.getCoveredStores());
        m.put("dataMissingStores", dp.getDataMissingStores() == null ? List.of() : dp.getDataMissingStores());
        m.put("summary", nz(dp.getSummary()));
        m.put("statStartDate", nz(dp.getStatStartDate()));
        m.put("statEndDate", nz(dp.getStatEndDate()));
        m.put("dishCount", dp.getDishCount());
        m.put("totalDishSalesAmount", nz(dp.getTotalDishSalesAmount()));
        m.put("totalTheoreticalCost", nz(dp.getTotalTheoreticalCost()));
        m.put("totalActualCost", nz(dp.getTotalActualCost()));
        m.put("grossProfitAmount", nz(dp.getGrossProfitAmount()));
        m.put("grossProfitRate", nz(dp.getGrossProfitRate()));
        m.put("grossProfitRateUncertain", dp.isGrossProfitRateUncertain());
        m.put("riskLevel", nz(dp.getRiskLevel()));
        m.put("reliableProfitDishes", capDishBriefs(dp.getReliableProfitDishes(), 5));
        m.put("lowProfitDishes", capDishBriefs(dp.getLowProfitDishes(), 5));
        m.put("costDataIncompleteDishes", capDishBriefs(dp.getCostDataIncompleteDishes(), 8));
        m.put("topProfitDishes", capDishBriefs(dp.getTopProfitDishes(), 5));
        m.put("abnormalDishes", capDishBriefs(dp.getAbnormalDishes(), 6));
        m.put("recommendations", dp.getRecommendations() == null ? List.of() : dp.getRecommendations());
        return m;
    }

    private static List<Map<String, Object>> capDishBriefs(List<AiDishProfitDishBrief> xs, int max) {
        if (xs == null || xs.isEmpty()) {
            return List.of();
        }
        List<AiDishProfitDishBrief> deduped = dedupeDishBriefsForComposer(xs);
        List<AiDishProfitDishBrief> sub = deduped.size() <= max ? deduped : deduped.subList(0, max);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiDishProfitDishBrief b : sub) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("dishName", nz(b.getDishName()));
            row.put("salesQty", nz(b.getSalesQty()));
            row.put("salesAmount", nz(b.getSalesAmount()));
            row.put("theoreticalCost", nz(b.getTheoreticalCost()));
            row.put("actualCost", nz(b.getActualCost()));
            row.put("grossProfitAmount", nz(b.getGrossProfitAmount()));
            row.put("grossProfitRate", nz(b.getGrossProfitRate()));
            row.put("riskReason", nz(b.getRiskReason()));
            out.add(row);
        }
        return out;
    }

    private static List<AiDishProfitDishBrief> dedupeDishBriefsForComposer(List<AiDishProfitDishBrief> xs) {
        LinkedHashMap<String, AiDishProfitDishBrief> m = new LinkedHashMap<>();
        for (AiDishProfitDishBrief b : xs) {
            String key;
            if (b.getFoodId() != null && !b.getFoodId().isBlank()) {
                key = "id:" + b.getFoodId().trim();
            } else if (b.getDishName() != null && !b.getDishName().isBlank()) {
                key = "n:" + b.getDishName().trim();
            } else {
                key = "row:" + m.size();
            }
            m.putIfAbsent(key, b);
        }
        return new ArrayList<>(m.values());
    }

    private static String shortFallbackDishProfit(AiDishProfitOverviewResult r, AiRunState state) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        if (r == null) {
            String range = tw != null && tw.getDisplayTimeRange() != null ? tw.getDisplayTimeRange() : "该统计区间";
            return "按「" + range + "」口径，当前可用的菜品利润/毛利数据不足，暂时无法给出有效分析。"
                    + "请确认该门店在该统计周期内是否有完整销售、成本与配方/核销数据。";
        }
        if (dishProfitUseDeterministicSummaryOnly(state)) {
            if (r.getSummary() != null && !r.getSummary().isBlank()) {
                return r.getSummary().trim();
            }
            if (r.getQueryScopeBanner() != null && !r.getQueryScopeBanner().isBlank()) {
                return r.getQueryScopeBanner().trim();
            }
            return "当前结构化菜品毛利数据不足或本轮工具未返回明细，请先核对配方与出库核销数据是否齐备。";
        }
        StringBuilder sb = new StringBuilder();
        if (r.getQueryScopeBanner() != null && !r.getQueryScopeBanner().isBlank()) {
            sb.append(r.getQueryScopeBanner().trim());
        }
        if (r.getSummary() != null && !r.getSummary().isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(r.getSummary().trim());
        }
        if (dishProfitAnswerIsActualOutboundOnly(state)) {
            String s = sb.toString().trim();
            return !s.isEmpty() ? s : nz(r.getSummary());
        }
        appendDishSectionOrPlaceholder(sb, "毛利表现较好的菜（成本口径相对完整）", r.getReliableProfitDishes(), 3, false,
                tw);
        appendDishSectionOrPlaceholder(sb, "需要关注的低毛利或成本偏高菜", r.getLowProfitDishes(), 3, false, tw);
        appendDishSectionOrPlaceholder(sb, "成本数据不完整、毛利率仅供参考的菜", r.getCostDataIncompleteDishes(), 4, true,
                tw);
        String s = sb.toString().trim();
        if (!s.isEmpty()) {
            return s;
        }
        if (r.getDishCount() > 0 && r.getSummary() != null && !r.getSummary().isBlank()) {
            return r.getSummary().trim();
        }
        if (r.getDishCount() > 0) {
            return "本轮识别到 " + r.getDishCount()
                    + " 道菜品销量记录，但草稿中未能展开结构化明细行；请查看上方摘要或菜品卡片。";
        }
        return "当前结构化菜品毛利数据不足或本轮工具未返回明细，请先核对配方与出库核销数据是否齐备。";
    }

    private static boolean dishProfitUseDeterministicSummaryOnly(AiRunState state) {
        if (state == null) {
            return false;
        }
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        var qi = ctx != null ? ctx.getQueryIntent() : null;
        if (qi == null) {
            return false;
        }
        String sid = qi.getStructuredIntentDetail();
        if (sid == null || sid.isBlank()) {
            return false;
        }
        return AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(sid)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(sid)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(sid)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_LOW_PROFIT_REASON.equals(sid);
    }

    private static boolean dishProfitAnswerIsActualOutboundOnly(AiRunState state) {
        if (state == null) {
            return false;
        }
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        var qi = ctx != null ? ctx.getQueryIntent() : null;
        return qi != null
                && AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(qi.getStructuredIntentDetail());
    }

    private static void appendDishSectionOrPlaceholder(StringBuilder sb, String title, List<AiDishProfitDishBrief> dishes,
            int max, boolean incompleteCost, AiTimeWindowTextFormatter.UserPhrases tw) {
        if (dishes == null || dishes.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            if (title != null && title.contains("毛利表现较好")) {
                sb.append(title).append("：").append(tw.getDisplayTimeRange())
                        .append("内暂未识别到成本数据完整且毛利表现突出的菜品。");
            } else {
                sb.append(title).append("：暂无");
            }
            return;
        }
        appendDishSection(sb, title, dishes, max, incompleteCost);
    }

    private static void appendDishSection(StringBuilder sb, String title, List<AiDishProfitDishBrief> dishes, int max,
            boolean incompleteCost) {
        if (dishes == null || dishes.isEmpty()) {
            return;
        }
        List<AiDishProfitDishBrief> use = dedupeDishBriefsForComposer(dishes);
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(title).append("：");
        int n = 0;
        for (AiDishProfitDishBrief b : use) {
            if (n >= max || b == null) {
                break;
            }
            if (b.getDishName() == null || b.getDishName().isBlank()) {
                continue;
            }
            sb.append("\n• ").append(nz(b.getDishName()))
                    .append("：销量约 ").append(nz(b.getSalesQty()))
                    .append("，销售额约 ").append(nz(b.getSalesAmount()))
                    .append("，理论成本 ").append(nz(b.getTheoreticalCost()))
                    .append("，实际成本 ").append(nz(b.getActualCost()))
                    .append("，毛利率 ").append(nz(b.getGrossProfitRate()));
            if (incompleteCost) {
                sb.append("（成本未齐，该毛利率不可靠；请先补 BOM/出库核销）");
            } else if (b.getRiskReason() != null && !b.getRiskReason().isBlank()) {
                sb.append("（").append(b.getRiskReason().trim()).append("）");
            }
            n++;
        }
        if (n == 0) {
            sb.append("\n暂无");
        }
    }

    private static Map<String, Object> compactBusinessPayload(AiRunState state, AiBusinessOverviewResult o) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> os = o.getOverviewScope();
        if (os != null && !os.isEmpty()) {
            Object pb = os.get("primaryBanner");
            Object cd = os.get("coverageDetail");
            m.put("queryScopeBanner", pb == null ? "" : pb.toString().trim());
            m.put("queryScopeCoverage", cd == null ? "" : cd.toString().trim());
            m.put("overviewScope", os);
        } else {
            m.put("queryScopeBanner", "");
            m.put("queryScopeCoverage", "");
        }
        m.put("visibleStores", o.getVisibleStores() == null ? List.of() : o.getVisibleStores());
        m.put("hint", "numericHeadlineText 必须由模型在查询范围复述之后照抄复述；缺失项仅写暂无");
        m.put("numericHeadlineText", nz(extractOverviewNumericHeadline(state, o)));
        m.put("dashboardStatsCn摘录", excerptDashboardStatsCn(o.getDashboardStatsCn()));
        m.put("userQuestion", nz(state.getNormalizedUserInput()));
        m.put("summary", o.getSummary());
        m.put("priorityStoresBrief", nz(o.getPriorityStoresBrief()));
        m.put("coveredStoresBrief", nz(o.getCoveredStoresBrief()));
        m.put("coveredStores", capCoveredStoresPreview(o.getCoveredStores(), 80));
        m.put("dataMissingStores", capIssueItemsPreview(o.getDataMissingStores(), 12));
        m.put("attentionStores", capIssueItemsPreview(o.getAttentionStores(), 12));
        m.put("riskLevel", o.getRiskLevel());
        m.put("needMoreData", o.getNeedMoreData());
        m.put("keyMetrics", o.getKeyMetrics());
        m.put("findings", o.getFindings());
        m.put("recommendations", o.getRecommendations());
        m.put("questions", o.getQuestions());
        return m;
    }

    private static List<AiOverviewCoveredStoreItem> capCoveredStoresPreview(List<AiOverviewCoveredStoreItem> full, int max) {
        if (full == null || full.isEmpty()) {
            return List.of();
        }
        if (full.size() <= max) {
            return new ArrayList<>(full);
        }
        return new ArrayList<>(full.subList(0, max));
    }

    private static List<AiOverviewStoreIssueItem> capIssueItemsPreview(List<AiOverviewStoreIssueItem> full, int max) {
        if (full == null || full.isEmpty()) {
            return List.of();
        }
        if (full.size() <= max) {
            return new ArrayList<>(full);
        }
        return new ArrayList<>(full.subList(0, max));
    }

    /** 与日营收看板中文键对齐的摘录，避免整包 stats 过长。 */
    private static Map<String, Object> excerptDashboardStatsCn(Map<String, Object> stats) {
        if (stats == null || stats.isEmpty()) {
            return Map.of();
        }
        String[] keys = {
                "数据口径说明",
                "统计开始日期", "统计结束日期",
                "统计天数", "总营业额", "日均营业额", "日均订单数", "客单价",
                "平台费合计", "退款合计", "外卖营业额合计", "日均净收入",
                "盈亏状态", "利润率", "日均利润含库存成本"
        };
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (String k : keys) {
            if (stats.containsKey(k)) {
                Object v = stats.get(k);
                if (v == null) {
                    out.put(k, "暂无");
                } else if ("数据口径说明".equals(k) || "盈亏状态".equals(k)) {
                    out.put(k, v.toString().trim());
                } else if ("统计开始日期".equals(k) || "统计结束日期".equals(k)) {
                    String d = v.toString().trim();
                    out.put(k, d.isBlank() ? "暂无" : d);
                } else {
                    out.put(k, plainStatSnippet(v));
                }
            }
        }
        return out;
    }

    private static String extractOverviewNumericHeadline(AiRunState state, AiBusinessOverviewResult o) {
        Map<String, Object> st = o.getDashboardStatsCn();
        if (st == null || st.isEmpty()) {
            st = loadStatsFallbackFromTool(state);
        }
        if (st != null && !st.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            appendDistinctRevenueDayLead(sb, st);
            sb.append("，总营业额 ");
            appendPlainValue(sb, st.get("总营业额"));
            sb.append(" 元，日均营业额 ");
            appendPlainValue(sb, st.get("日均营业额"));
            sb.append(" 元，日均订单数 ");
            appendPlainValue(sb, st.get("日均订单数"));
            sb.append(" 单/天，客单价 ");
            appendPlainValue(sb, st.get("客单价"));
            sb.append(" 元。优惠券/平台费合计 ");
            appendPlainValue(sb, st.get("平台费合计"));
            sb.append(" 元，退款合计 ");
            appendPlainValue(sb, st.get("退款合计"));
            sb.append(" 元，外卖营业额合计 ");
            appendPlainValue(sb, st.get("外卖营业额合计"));
            sb.append(" 元");
            Object profit = st.get("盈亏状态");
            if (profit != null && !profit.toString().isBlank()) {
                String ps = profit.toString().trim();
                if (!"-".equals(ps) && !"—".equals(ps)) {
                    sb.append("。盈亏状态：").append(ps);
                }
            }
            sb.append("。");
            return sb.toString();
        }
        String fromSummary = o.getSummary();
        if (fromSummary != null && !fromSummary.isBlank()) {
            return fromSummary.trim();
        }
        return "暂无日营收经营看板数据，无法列出查询区间内具体数字。";
    }

    /**
     * 与 {@link com.nongxinle.service.impl.GbAiDailyRevenueDashboardServiceImpl#buildGroupWideIncomeFlattened}
     * 等指标一致：先说明本次查询日期边界，再说明「统计天数」是区间内有营业额入账的自然日数（非日历满跨度）。
     */
    private static void appendDistinctRevenueDayLead(StringBuilder sb, Map<String, Object> statsCn) {
        String qStart = trimStatDate(statsCn.get("统计开始日期"));
        String qEnd = trimStatDate(statsCn.get("统计结束日期"));
        if (!qStart.isEmpty() && !qEnd.isEmpty()) {
            if (qStart.equals(qEnd)) {
                sb.append(qStart).append(" 当日");
            } else {
                sb.append("所选区间 ").append(qStart).append("～").append(qEnd).append(" 内");
            }
        } else if (!qStart.isEmpty()) {
            sb.append("自 ").append(qStart).append(" 起");
        } else if (!qEnd.isEmpty()) {
            sb.append("截至 ").append(qEnd);
        } else {
            sb.append("本查询区间内");
        }
        sb.append("，录入营业额的自然日共 ");
        appendPlainValue(sb, statsCn.get("统计天数"));
        sb.append(" 天");
    }

    private static String trimStatDate(Object raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.toString().trim();
        return t.isBlank() ? "" : t;
    }

    private static Map<String, Object> loadStatsFallbackFromTool(AiRunState state) {
        Map<String, Object> bo = overviewToolData(state);
        @SuppressWarnings("unchecked")
        Map<String, Object> st = bo.get("stats") instanceof Map ? (Map<String, Object>) bo.get("stats") : Map.of();
        return st;
    }

    private static void appendPlainValue(StringBuilder sb, Object v) {
        if (v == null || v.toString().isBlank()) {
            sb.append("暂无");
            return;
        }
        String raw = v.toString().trim();
        if ("-".equals(raw) || "—".equals(raw) || "不适用".equals(raw)) {
            sb.append(raw);
            return;
        }
        sb.append(AiNumericPlainText.plainNumber(v));
    }

    /** 给模型看的摘录：金额类避免科学计数法。 */
    private static String plainStatSnippet(Object v) {
        if (v == null || v.toString().isBlank()) {
            return "暂无";
        }
        String raw = v.toString().trim();
        if ("-".equals(raw) || "—".equals(raw) || "不适用".equals(raw)) {
            return raw;
        }
        if (v instanceof String) {
            return AiNumericPlainText.plainNumber(v);
        }
        if (v instanceof Number) {
            return AiNumericPlainText.plainNumber(v);
        }
        try {
            return AiNumericPlainText.plainNumber(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> overviewToolData(AiRunState state) {
        Object env = state.getToolResults().get(AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY);
        if (!(env instanceof Map)) {
            return Map.of();
        }
        Object data = ((Map<String, Object>) env).get("data");
        if (!(data instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) data;
    }

    private static String shortFallbackCost(AiCostDiagnosisResult d) {
        StringBuilder sb = new StringBuilder();
        if (d.getSummary() != null && !d.getSummary().isBlank()) {
            sb.append(d.getSummary().trim());
        } else {
            sb.append("已根据当前可查数据完成成本诊断初步判断。");
        }
        sb.append('\n');
        appendNumbered(sb, "重点发现", capCopy(d.getFindings(), MAX_FALLBACK_FINDINGS), MAX_FALLBACK_FINDINGS);
        appendNumbered(sb, "建议先做", capCopy(d.getRecommendations(), MAX_FALLBACK_RECOMMENDATIONS), MAX_FALLBACK_RECOMMENDATIONS);
        sb.append("\n下面的成本诊断卡片里有详细指标。");
        return sb.toString().trim();
    }

    private static String shortFallbackBusiness(AiRunState state, AiBusinessOverviewResult o) {
        StringBuilder sb = new StringBuilder();
        Map<String, Object> os = o.getOverviewScope();
        if (os != null && !os.isEmpty()) {
            Object pb = os.get("primaryBanner");
            Object cd = os.get("coverageDetail");
            if (pb != null && !pb.toString().isBlank()) {
                sb.append(pb.toString().trim());
            }
            if (cd != null && !cd.toString().isBlank()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(cd.toString().trim());
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
        }
        String cs = nz(o.getCoveredStoresBrief()).trim();
        if (!cs.isBlank()) {
            sb.append(cs).append('\n');
        }
        if (os != null && !os.isEmpty()) {
            Object dmb = os.get("dataMissingStoresBrief");
            if (dmb != null && !dmb.toString().isBlank()) {
                sb.append(dmb.toString().trim()).append('\n');
            }
        }
        sb.append(extractOverviewNumericHeadline(state, o));
        sb.append('\n');
        String ps = nz(o.getPriorityStoresBrief()).trim();
        if (!ps.isBlank()) {
            sb.append(ps).append('\n');
        }
        appendNumbered(sb, "当前重点", capCopy(o.getFindings(), MAX_FALLBACK_FINDINGS), MAX_FALLBACK_FINDINGS);
        appendNumbered(sb, "建议动作", capCopy(o.getRecommendations(), MAX_FALLBACK_RECOMMENDATIONS), MAX_FALLBACK_RECOMMENDATIONS);
        sb.append("\n完整指标详见下方经营概览卡片。");
        return sb.toString().trim();
    }

    private static void appendNumbered(StringBuilder sb, String title, List<String> lines, int max) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        sb.append('\n').append(title).append("：\n");
        int n = Math.min(lines.size(), max);
        for (int i = 0; i < n; i++) {
            sb.append(i + 1).append(". ").append(lines.get(i).trim()).append('\n');
        }
    }

    private static List<String> capCopy(List<String> list, int max) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        if (list.size() <= max) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>(list.subList(0, max));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String nz(Object o) {
        return o == null ? "" : o.toString();
    }

    private static LinkedHashMap<String, Object> summarizePurchaseToolPresenceCn(AiRunState state) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        m.put("timeRangeForAnswer", tw.getBracketTimeRangeLine());
        Map<String, Object> innerPo = toolDataInnerMap(state, AiBusinessToolIds.PURCHASE_OVERVIEW);
        Object pOverview = innerPo.get("purchaseOverview");
        if (pOverview instanceof Map<?, ?> pom) {
            m.put("采购概览", pom);
            Object scs = pom.get("storeCoverageSummary");
            if (scs != null && !scs.toString().isBlank()) {
                m.put("集团门店采购覆盖说明_须向用户复述", scs.toString().trim());
            }
        }
        Map<String, Object> pu = toolDataInnerMap(state, AiBusinessToolIds.PURCHASE_QUERY);
        Map<String, Object> stk = toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        m.put("采购入库有可读结果", !pu.isEmpty());
        m.put("核销出库有可读结果", !stk.isEmpty());
        if (isBusinessOverviewToPurchaseConvergence(state)) {
            m.put("答复口径",
                    "用户用经营类话术提问，但账号为门店采购：仅总结采购笔数、采购金额、采购方式拆分（若有）及核销/出库结构；禁止营业额与毛利类表述；勿提采购总重量。");
        }
        if (!pu.isEmpty()) {
            m.put("统计周期内采购入库金额_元", plainNumericHint(pu.get("purchaseSubTotal")));
            m.put("采购明细行数", plainNumericHint(pu.get("purchaseRowCount")));
        }
        if (!stk.isEmpty()) {
            m.put("核销生产耗用合计", plainNumericHint(stk.get("productionTotal")));
            m.put("核销出品合计", plainNumericHint(stk.get("produceTotal")));
            m.put("核销废弃合计_type2", plainNumericHint(stk.get("wasteTotal")));
            m.put("核销损耗合计_type3", plainNumericHint(stk.get("lossTotal")));
            m.put("核销退货合计", plainNumericHint(stk.get("returnTotal")));
        }
        return m;
    }

    private static String plainNumericHint(Object v) {
        if (v == null) {
            return "暂无";
        }
        if (v instanceof BigDecimal bd) {
            return AiNumericPlainText.plainNumber(bd);
        }
        if (v instanceof Number n) {
            return AiNumericPlainText.plainNumber(n);
        }
        String s = v.toString().trim();
        return s.isEmpty() ? "暂无" : s;
    }

    private static boolean isBusinessOverviewToPurchaseConvergence(AiRunState state) {
        Map<String, String> ic = state.getIntentConvergence();
        return ic != null
                && "BUSINESS_OVERVIEW".equals(ic.get("from"))
                && "PURCHASE_OVERVIEW".equals(ic.get("to"));
    }

    private static boolean shouldForceDeterministicPurchaseAnswer(AiRunState state) {
        var ctx = state.getResolvedQueryContext();
        if (ctx == null || ctx.getQueryIntent() == null) {
            return false;
        }
        String sid = ctx.getQueryIntent().getStructuredIntentDetail();
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(sid)) {
            return true;
        }
        if (AiQuerySemanticLexicon.isSupplierAmountRankingDetail(sid)) {
            return true;
        }
        String msg = nz(state.getNormalizedUserInput());
        return AiQuerySemanticLexicon.looksLikeSupplierRanking(msg)
                && !AiQuerySemanticLexicon.looksLikeExplicitPurchaseGeneralOverviewOrGoodsRankingOnly(msg);
    }

    private static String purchaseCostFallback(AiRunState state) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        Map<String, Object> overview = extractPurchaseOverviewPayload(state);
        Map<String, Object> stk = toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        boolean convergence = isBusinessOverviewToPurchaseConvergence(state);
        if (!overview.isEmpty()) {
            return purchaseOverviewStructuredFallback(state, overview, stk, convergence, tw);
        }
        Map<String, Object> p = toolDataInnerMap(state, AiBusinessToolIds.PURCHASE_QUERY);
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        if (convergence) {
            sb.append("说明：以下仅基于采购与库存权限汇总，不包含营业额、订单数、客单价、毛利或利润等经营指标。\n");
        } else {
            sb.append("（采购视角）已按权限汇总采购入库与核销/出库数据，不包含营业额与毛利率诊断。\n");
        }
        boolean purchaseHasRows = purchaseHasDataRows(p);
        if (!p.isEmpty() && purchaseHasRows) {
            sb.append(tw.getDisplayTimeRange()).append("，采购入库金额约 ")
                    .append(plainNumericHint(p.get("purchaseSubTotal")))
                    .append(" 元；采购明细行数 ")
                    .append(plainNumericHint(p.get("purchaseRowCount")))
                    .append("。\n");
        }
        appendPurchaseStockReduceParagraph(sb, stk, true, tw);
        if (!purchaseHasRows && stk.isEmpty()) {
            if (convergence) {
                sb.append("你当前账号可查看采购相关数据，但").append(tw.getDisplayTimeRange())
                        .append("暂未查询到采购记录；核销/出库侧亦无可用汇总。\n");
            } else {
                sb.append("当前可用数据不足，暂时无法给出完整分析；请核对本岗权限、所选门店与时间区间是否与录入一致。\n");
            }
        } else if (!purchaseHasRows && !stk.isEmpty() && convergence) {
            sb.append(tw.getDisplayTimeRange()).append("采购入库侧暂未查询到明细记录，可先结合上方核销/出库汇总排查是否与入库录入一致。\n");
        }
        sb.append("供应商价格与品类波动建议在采购或供货商模块导出核对。");
        return sb.toString().trim();
    }

    private static String purchaseOverviewStructuredFallback(AiRunState state, Map<String, Object> overview,
            Map<String, Object> stk, boolean convergence, AiTimeWindowTextFormatter.UserPhrases tw) {
        Object narrativeObj = overview.get("purchaseNarrativeMode");
        String narrative = narrativeObj != null ? narrativeObj.toString().trim() : "";
        if (narrative.isBlank()) {
            narrative = AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY;
        }
        Object focusObj = overview.get("purchaseSourceFocus");
        String purchaseFocus = focusObj != null ? focusObj.toString().trim() : "";
        boolean treatAsSupplierRanking = useSupplierRankingFocusedTemplate(state, narrative);
        if (treatAsSupplierRanking) {
            return purchaseSupplierRankingFallback(state, overview, tw);
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(narrative)) {
            return purchaseSourceAmountOnlyFallback(state, overview, purchaseFocus, tw);
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(narrative)) {
            return purchaseSourceGoodsNarrowFallback(state, overview, purchaseFocus, tw);
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(narrative)) {
            return purchaseSourceSummaryNarrowFallback(state, overview, purchaseFocus, tw);
        }
        return purchaseOverviewFullSummaryFallback(state, overview, stk, convergence, purchaseFocus, tw);
    }

    /** 是否走「仅供货商采购金额排行」短答（与 purchase_overview_summary 全量模板区分）。 */
    private static boolean useSupplierRankingFocusedTemplate(AiRunState state, String narrativeFromOverview) {
        if (AiQuerySemanticLexicon.isSupplierAmountRankingDetail(narrativeFromOverview)) {
            return true;
        }
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        var qi = state.getResolvedQueryContext().getQueryIntent();
        if (qi != null && AiQuerySemanticLexicon.isSupplierAmountRankingDetail(qi.getStructuredIntentDetail())) {
            return true;
        }
        String msg = nz(state.getNormalizedUserInput());
        if (!AiQuerySemanticLexicon.looksLikeSupplierRanking(msg)) {
            return false;
        }
        if (AiQuerySemanticLexicon.looksLikeExplicitPurchaseGeneralOverviewOrGoodsRankingOnly(msg)) {
            return false;
        }
        return true;
    }

    private static PurchaseCarryHint tryParsePurchaseCarryFromPreviousTurn(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return null;
        }
        AiConversationTurnMemory prev = state.getResolvedQueryContext().getPreviousTurn();
        if (prev == null || prev.getLastToolSummary() == null) {
            return null;
        }
        Matcher m = PREV_TURN_PURCHASE_CARRY_PREFIX.matcher(prev.getLastToolSummary().trim());
        if (!m.find()) {
            return null;
        }
        try {
            int po = Integer.parseInt(m.group(1));
            String amtTok = m.group(2).trim();
            return new PurchaseCarryHint(po, amtTok);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 供货商渠道汇总为 0 时的说明正文（不含时间括号行）；若上一轮有 carry 则对比全口径结论。
     */
    private static void appendSupplierPurchaseZeroNarrativeBody(StringBuilder sb, AiRunState state,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        PurchaseCarryHint carry = tryParsePurchaseCarryFromPreviousTurn(state);
        String storeSubject = resolveSinglePurchaseStoreSubject(state);
        String timePhrase = resolvedTimeSubjectPhrase(tw);
        String leadInStoreTime = spacedStorePlusTimePhrase(storeSubject, timePhrase);
        String resultStoreTime = compactResultStoreTimePhrase(storeSubject, timePhrase);
        sb.append("沿用上文 ").append(leadInStoreTime).append("口径，本轮只看供货商采购。");
        sb.append("查询结果：").append(resultStoreTime).append("暂无供货商采购记录，供货商采购 0 笔、0 元。");
        if (carry != null && carry.orderCount() > 0) {
            String amtDisp = plainNumericHint(carry.amountToken());
            sb.append("结合上一轮").append(resultStoreTime).append("总采购 ").append(carry.orderCount()).append(" 笔、")
                    .append(amtDisp).append(" 元，可判断这些采购均为自采记录。");
        } else {
            sb.append("（未附带上一轮全口径对照数据时无法在答复中自动判断是否均为自采，请在系统中按入库来源拆分核对供货商/自采。）");
        }
    }

    /** 与用户可见【查询范围】前缀配合：阐明继承口径下的供货商筛选结论，避免笼统「暂无有效采购」。 */
    private static String supplierPurchaseFilteredZeroParagraph(AiRunState state,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        appendSupplierPurchaseZeroNarrativeBody(sb, state, tw);
        return sb.toString().trim();
    }

    private static String resolveSinglePurchaseStoreSubject(AiRunState state) {
        AiResolvedQueryContext ctx = state != null ? state.getResolvedQueryContext() : null;
        if (ctx == null || ctx.getOrgScope() == null || ctx.getOrgScope().getVisibleStores() == null
                || ctx.getOrgScope().getVisibleStores().size() != 1) {
            return "";
        }
        AiStoreScopeDTO s = ctx.getOrgScope().getVisibleStores().get(0);
        if (s == null || s.getStoreName() == null || s.getStoreName().isBlank()) {
            return "";
        }
        return s.getStoreName().trim();
    }

    private static String resolvedTimeSubjectPhrase(AiTimeWindowTextFormatter.UserPhrases tw) {
        String time = tw.getTimeSubjectText();
        if (time == null || time.isBlank()) {
            return "该统计区间";
        }
        return time.trim();
    }

    private static String spacedStorePlusTimePhrase(String store, String timePhrase) {
        if (store == null || store.isBlank()) {
            return timePhrase;
        }
        return store + " + " + timePhrase;
    }

    private static String compactResultStoreTimePhrase(String store, String timePhrase) {
        if (store == null || store.isBlank()) {
            return timePhrase;
        }
        return store + " " + timePhrase;
    }

    /** 自采/供货商「金额是多少」类：只报金额、笔数，至多 2 个金额最高单品；不追加「其中…」拆分与核销长段。 */
    private static String purchaseSourceAmountOnlyFallback(AiRunState state, Map<String, Object> overview,
            String purchaseFocus,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        int cnt = intHint(overview.get("purchaseOrderCount"));
        double amt = parseDoubleLoose(overview.get("totalPurchaseAmount"));
        boolean selfFocus = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseFocus);
        boolean supFocus = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseFocus);
        if (cnt <= 0 && amt <= 0) {
            if (selfFocus) {
                return "当前范围内暂未查询到自采入库记录，请确认时间与门店范围内是否有自采入库数据。";
            }
            if (supFocus) {
                return supplierPurchaseFilteredZeroParagraph(state, tw);
            }
            return "当前范围内暂未查询到有效采购记录，请确认采购入库数据是否已录入。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        if (selfFocus) {
            sb.append(tw.getDisplayTimeRange()).append("，自采金额为")
                    .append(plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元，共")
                    .append(cnt)
                    .append("笔自采入库");
        } else if (supFocus) {
            sb.append(tw.getDisplayTimeRange()).append("，供货商渠道采购金额为")
                    .append(plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元，共")
                    .append(cnt)
                    .append("笔供货商采购入库");
        } else {
            sb.append(tw.getDisplayTimeRange()).append("，采购入库总金额为")
                    .append(plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元，共")
                    .append(cnt)
                    .append("笔");
        }
        sb.append("。");
        List<String> tops = pickPurchaseAmountTopParts(overview, 2);
        if (!tops.isEmpty()) {
            if (selfFocus) {
                sb.append("自采金额最高的商品是").append(String.join("、", tops)).append("。");
            } else if (supFocus) {
                sb.append("供货商渠道采购金额最高的商品是").append(String.join("、", tops)).append("。");
            } else {
                sb.append("采购金额最高的商品是").append(String.join("、", tops)).append("。");
            }
        }
        return sb.toString();
    }

    /** 「自采了哪些商品」：笔数+金额 + 频次/金额 Top；不展开核销与门店覆盖长段。 */
    private static String purchaseSourceGoodsNarrowFallback(AiRunState state, Map<String, Object> overview,
            String purchaseFocus, AiTimeWindowTextFormatter.UserPhrases tw) {
        int cnt = intHint(overview.get("purchaseOrderCount"));
        double amt = parseDoubleLoose(overview.get("totalPurchaseAmount"));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        sb.append("（采购视角）以下汇总采购入库商品情况，不包含营业额与毛利率诊断。\n");
        Object b = overview.get("queryScopeBanner");
        if (b != null && !b.toString().isBlank()) {
            sb.append(b.toString().trim()).append("\n");
        }
        appendPurchaseCountAmountLine(sb, state, overview, purchaseFocus, tw);
        if (cnt <= 0 && amt <= 0) {
            return sb.toString().trim();
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append("\n");
        }
        appendGoodsFrequencyTopSentence(sb, overview.get("goodsPurchaseFrequencyTop"), purchaseFocus);
        Object amtTop = overview.get("goodsPurchaseAmountTop");
        if (!(amtTop instanceof List<?>) || ((List<?>) amtTop).isEmpty()) {
            amtTop = overview.get("highAmountItems");
        }
        appendGoodsAmountTopSentence(sb, amtTop, purchaseFocus);
        return sb.toString().trim();
    }

    /** 「自采有多少」：笔数+金额 + 可 Top 商品；不展开完整概览。 */
    private static String purchaseSourceSummaryNarrowFallback(AiRunState state, Map<String, Object> overview,
            String purchaseFocus, AiTimeWindowTextFormatter.UserPhrases tw) {
        int cnt = intHint(overview.get("purchaseOrderCount"));
        double amt = parseDoubleLoose(overview.get("totalPurchaseAmount"));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        sb.append("（采购视角）以下汇总采购入库情况，不包含营业额与毛利率诊断。\n");
        Object b = overview.get("queryScopeBanner");
        if (b != null && !b.toString().isBlank()) {
            sb.append(b.toString().trim()).append("\n");
        }
        appendPurchaseCountAmountLine(sb, state, overview, purchaseFocus, tw);
        if (cnt <= 0 && amt <= 0) {
            return sb.toString().trim();
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append("\n");
        }
        appendGoodsFrequencyTopSentence(sb, overview.get("goodsPurchaseFrequencyTop"), purchaseFocus);
        Object amtTop = overview.get("goodsPurchaseAmountTop");
        if (!(amtTop instanceof List<?>) || ((List<?>) amtTop).isEmpty()) {
            amtTop = overview.get("highAmountItems");
        }
        appendGoodsAmountTopSentence(sb, amtTop, purchaseFocus);
        return sb.toString().trim();
    }

    /** 供货商采购金额排行：只输出名次与户数，不包含全量采购/自采拆分/单品 Top/核销。 */
    private static String purchaseSupplierRankingFallback(AiRunState state, Map<String, Object> overview,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        StringBuilder sb = new StringBuilder();
        sb.append(tw != null ? tw.getDisplayTimeRange() : "统计周期")
                .append("，")
                .append(supplierRankingScopeLead(state, overview))
                .append("供货商采购金额排名如下：\n");
        Object topRaw = overview.get("topSuppliers");
        if (!(topRaw instanceof List<?> topList) || topList.isEmpty()) {
            int po = intHint(overview.get("purchaseOrderCount"));
            double amt = parseDoubleLoose(overview.get("totalPurchaseAmount"));
            if (po <= 0 && amt <= 0) {
                sb.append("当前范围内暂未查询到采购入库记录。");
            } else {
                sb.append("暂无真实供货商采购记录。");
                sb.append("本周期仍有采购入账，但未识别到挂靠供货商的入账行（常见于全部为自采或入库未登记供货商）；与上一轮若为「全自采」结论一致时也属正常。");
            }
            return sb.toString().trim();
        }
        int pos = 1;
        for (Object o : topList) {
            if (pos > 50) {
                break;
            }
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Object nm = row.get("supplierName");
            if (nm == null || nm.toString().isBlank()) {
                continue;
            }
            int lines = supplierRankingLineCountHint(row);
            double rowAmt = parseDoubleLoose(row.get("totalPurchaseAmount"));
            sb.append("第")
                    .append(pos)
                    .append("名：")
                    .append(nm.toString().trim())
                    .append("，采购金额")
                    .append(rowAmt > 1e-9 ? plainNumericHint(row.get("totalPurchaseAmount")) : plainNumericHint(0))
                    .append("元，共")
                    .append(lines > 0 ? lines : Math.max(intHint(row.get("orderCount")), intHint(row.get("lineCount"))))
                    .append("笔。\n");
            pos++;
        }
        int counted = pos - 1;
        if (counted <= 0) {
            sb.append("暂无真实供货商采购记录。");
        } else {
            sb.append("\n当前口径下仅查询到")
                    .append(counted)
                    .append("家真实供货商采购记录。");
        }
        return sb.toString().trim();
    }

    private static int supplierRankingLineCountHint(Map<?, ?> row) {
        int a = intHint(row.get("purchaseLineCount"));
        if (a > 0) {
            return a;
        }
        return intHint(row.get("purchaseOrderCount"));
    }

    /** 接在时间及逗号后的范围提示，如「集团范围」。 */
    private static String supplierRankingScopeLead(AiRunState state, Map<String, Object> overview) {
        Object b = overview != null ? overview.get("queryScopeBanner") : null;
        String banner = b != null ? b.toString().trim() : "";
        if (banner.contains("集团")) {
            return "集团范围";
        }
        if (state != null && state.getResolvedQueryContext() != null) {
            var org = state.getResolvedQueryContext().getOrgScope();
            if (org != null) {
                if (AiResolvedOrgScope.SCOPE_GROUP.equals(org.getScopeType())) {
                    return "集团范围";
                }
                if (org.getVisibleStores() != null && org.getVisibleStores().size() == 1) {
                    return "当前门店范围";
                }
            }
        }
        if (!banner.isEmpty()) {
            return banner.replaceFirst("^【?查询范围】?[:：]?\\s*", "").trim();
        }
        return "当前查询范围";
    }

    private static void appendPurchaseCountAmountLine(StringBuilder sb, AiRunState state,
            Map<String, Object> overview, String purchaseFocus, AiTimeWindowTextFormatter.UserPhrases tw) {
        int cnt = intHint(overview.get("purchaseOrderCount"));
        double amt = parseDoubleLoose(overview.get("totalPurchaseAmount"));
        boolean selfFocus = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseFocus);
        boolean supFocus = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseFocus);
        if (cnt <= 0 && amt <= 0) {
            if (selfFocus) {
                sb.append("当前范围内暂未查询到自采入库记录，请确认时间与门店范围内是否有自采入库数据。");
            } else if (supFocus) {
                if (state != null) {
                    appendSupplierPurchaseZeroNarrativeBody(sb, state, tw);
                } else {
                    sb.append(
                            "本轮按供货商采购渠道汇总：暂无供货商入库记录（0 笔、0 元）；未表示全口径采购为空，请核对时间与录入口径。");
                }
            } else {
                sb.append("当前范围内暂未查询到有效采购记录，请确认采购入库数据是否已录入。");
            }
            return;
        }
        String rangeLead = tw != null ? tw.getDisplayTimeRange() : "统计周期";
        if (selfFocus) {
            sb.append(rangeLead).append("，自采入库共")
                    .append(cnt)
                    .append("笔，自采金额")
                    .append(plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元。");
        } else if (supFocus) {
            sb.append(rangeLead).append("，供货商渠道采购入库共")
                    .append(cnt)
                    .append("笔，金额")
                    .append(plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元。");
        } else {
            sb.append(rangeLead).append("，采购入库共")
                    .append(cnt)
                    .append("笔，金额")
                    .append(plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元。");
        }
    }

    private static List<String> pickPurchaseAmountTopParts(Map<String, Object> overview, int maxN) {
        if (maxN <= 0) {
            return Collections.emptyList();
        }
        Object amtTop = overview.get("goodsPurchaseAmountTop");
        if (!(amtTop instanceof List<?>) || ((List<?>) amtTop).isEmpty()) {
            amtTop = overview.get("highAmountItems");
        }
        if (!(amtTop instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        for (Object o : list) {
            if (parts.size() >= maxN) {
                break;
            }
            if (o instanceof Map<?, ?> row) {
                Object nm = row.get("goodsName");
                Object sub = row.get("purchaseSubtotal");
                if (nm != null && !nm.toString().isBlank() && sub != null
                        && parseDoubleLoose(sub) > 1e-9) {
                    parts.add(nm.toString().trim() + plainNumericHint(sub) + "元");
                }
            }
        }
        return parts;
    }

    private static String purchaseOverviewFullSummaryFallback(AiRunState state, Map<String, Object> overview,
            Map<String, Object> stk, boolean convergence, String purchaseFocus,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        if (convergence) {
            sb.append("说明：以下仅基于采购与库存权限汇总，不包含营业额、订单数、客单价、毛利或利润等经营指标。\n");
        } else {
            sb.append("（采购视角）已按权限汇总采购入库与核销/出库数据，不包含营业额与毛利率诊断。\n");
        }
        Object b = overview.get("queryScopeBanner");
        if (b != null && !b.toString().isBlank()) {
            sb.append(b.toString().trim()).append("\n");
        }
        Object scs = overview.get("storeCoverageSummary");
        if (scs != null && !scs.toString().isBlank()) {
            sb.append(scs.toString().trim()).append("\n");
        }
        int cnt = intHint(overview.get("purchaseOrderCount"));
        double amt = parseDoubleLoose(overview.get("totalPurchaseAmount"));
        boolean selfFocus = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseFocus);
        boolean supFocus = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseFocus);
        if (cnt <= 0 && amt <= 0) {
            if (selfFocus) {
                sb.append("当前范围内暂未查询到自采入库记录，请确认时间与门店范围内是否有自采入库数据。\n");
            } else if (supFocus) {
                if (state != null) {
                    appendSupplierPurchaseZeroNarrativeBody(sb, state, tw);
                    sb.append("\n");
                } else {
                    sb.append(
                            "本轮按供货商采购渠道汇总：暂无供货商入库记录（0 笔、0 元）；未表示全口径采购为空，请核对时间与录入口径。\n");
                }
            } else {
                sb.append("当前范围内暂未查询到有效采购记录，请确认采购入库数据是否已录入。\n");
            }
        } else {
            String rangeLead = tw.getDisplayTimeRange();
            sb.append(rangeLead).append("，");
            if (selfFocus) {
                sb.append("自采入库共");
            } else if (supFocus) {
                sb.append("供货商渠道采购入库共");
            } else {
                sb.append("采购入库共");
            }
            sb.append(cnt)
                    .append("笔，")
                    .append(selfFocus ? "自采金额" : (supFocus ? "供货商渠道采购金额" : "总金额"))
                    .append(plainNumericHint(overview.get("totalPurchaseAmount")))
                    .append("元");
            boolean methodOk = Boolean.TRUE.equals(overview.get("purchaseMethodBreakdownSupported"));
            Object frag = overview.get("purchaseMethodSummaryFragment");
            Object methodNote = overview.get("purchaseMethodNote");
            if (methodOk && frag != null && !frag.toString().isBlank() && !selfFocus && !supFocus) {
                sb.append("其中").append(frag.toString().trim()).append("。");
            } else if (methodNote != null && !methodNote.toString().isBlank() && !selfFocus && !supFocus) {
                sb.append(" ").append(methodNote.toString().trim()).append("。");
            } else {
                sb.append("。");
            }
            appendGoodsFrequencyTopSentence(sb, overview.get("goodsPurchaseFrequencyTop"), purchaseFocus);
            Object amtTop = overview.get("goodsPurchaseAmountTop");
            if (!(amtTop instanceof List<?>) || ((List<?>) amtTop).isEmpty()) {
                amtTop = overview.get("highAmountItems");
            }
            appendGoodsAmountTopSentence(sb, amtTop, purchaseFocus);
            appendPrimarySuppliersSentence(sb, overview.get("topSuppliers"));

            appendBriefPurchaseList(sb, "价格波动较明显的商品", overview.get("priceChangeItems"), "goodsName", 3);
            appendBriefPurchaseList(sb, "有采购但无销售/无核销（待核对）", overview.get("purchaseWithoutSalesItems"),
                    "goodsName", 3);
        }
        appendWarehouseRecommendations(sb, overview.get("recommendations"));
        appendPurchaseStockReduceParagraph(sb, stk, true, tw);
        sb.append("供应商价格与品类波动建议在采购或供货商模块导出核对。");
        return sb.toString().trim();
    }

    private static void appendGoodsFrequencyTopSentence(StringBuilder sb, Object listObj, String purchaseSourceFocus) {
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        int n = 0;
        for (Object o : list) {
            if (n >= 5) {
                break;
            }
            if (o instanceof Map<?, ?> row) {
                Object nm = row.get("goodsName");
                int times = intHint(row.get("purchaseTimes"));
                if (nm != null && !nm.toString().isBlank() && times > 0) {
                    parts.add(nm.toString().trim() + times + "次");
                    n++;
                }
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        String head = "采购次数较多的是";
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseSourceFocus)) {
            head = "自采商品采购频次较高的是";
        } else if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseSourceFocus)) {
            head = "供货商采购商品采购频次较高的是";
        }
        sb.append(head).append(String.join("、", parts)).append("。\n");
    }

    private static void appendGoodsAmountTopSentence(StringBuilder sb, Object listObj, String purchaseSourceFocus) {
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        int n = 0;
        for (Object o : list) {
            if (n >= 5) {
                break;
            }
            if (o instanceof Map<?, ?> row) {
                Object nm = row.get("goodsName");
                Object sub = row.get("purchaseSubtotal");
                if (nm != null && !nm.toString().isBlank() && sub != null
                        && parseDoubleLoose(sub) > 1e-9) {
                    parts.add(nm.toString().trim() + plainNumericHint(sub) + "元");
                    n++;
                }
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        String head = "采购金额最高的是";
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseSourceFocus)) {
            head = "自采商品采购金额较高的是";
        } else if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseSourceFocus)) {
            head = "供货商采购商品金额较高的是";
        }
        sb.append(head).append(String.join("、", parts)).append("。\n");
    }

    private static void appendPrimarySuppliersSentence(StringBuilder sb, Object listObj) {
        appendPrimarySuppliersSentence(sb, listObj, 4);
    }

    private static void appendPrimarySuppliersSentence(StringBuilder sb, Object listObj, int maxSuppliers) {
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        int cap = maxSuppliers > 0 ? maxSuppliers : 4;
        List<String> parts = new ArrayList<>();
        int n = 0;
        for (Object o : list) {
            if (n >= cap) {
                break;
            }
            if (o instanceof Map<?, ?> row) {
                Object nm = row.get("supplierName");
                Object am = row.get("totalPurchaseAmount");
                if (nm != null && !nm.toString().isBlank()) {
                    StringBuilder one = new StringBuilder(nm.toString().trim());
                    if (am != null && parseDoubleLoose(am) > 1e-9) {
                        one.append("（").append(plainNumericHint(am)).append("元）");
                    }
                    parts.add(one.toString());
                    n++;
                }
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        sb.append("主要供货商为").append(String.join("、", parts)).append("。\n");
    }

    private static void appendPurchaseStockReduceParagraph(StringBuilder sb, Map<String, Object> stk,
            boolean closureHint, AiTimeWindowTextFormatter.UserPhrases tw) {
        if (stk == null || stk.isEmpty()) {
            return;
        }
        if (allPurchaseStockReduceMetricsZero(stk)) {
            String range = tw != null ? tw.getDisplayTimeRange() : "统计周期";
            sb.append(range).append("暂无核销/出库记录。\n");
            return;
        }
        sb.append("核销方面：生产耗用 ")
                .append(plainNumericHint(stk.get("productionTotal")))
                .append(" 元，出品 ")
                .append(plainNumericHint(stk.get("produceTotal")))
                .append(" 元，废弃 ")
                .append(plainNumericHint(stk.get("wasteTotal")))
                .append(" 元，损耗 ")
                .append(plainNumericHint(stk.get("lossTotal")))
                .append(" 元（亦称报损），退货 ")
                .append(plainNumericHint(stk.get("returnTotal")))
                .append(" 元");
        if (closureHint) {
            sb.append("。请结合入库核对链路是否闭合");
        }
        sb.append("。\n");
    }

    private static boolean allPurchaseStockReduceMetricsZero(Map<String, Object> stk) {
        if (stk == null || stk.isEmpty()) {
            return true;
        }
        String[] keys = {"productionTotal", "produceTotal", "wasteTotal", "lossTotal", "returnTotal"};
        for (String k : keys) {
            if (parseDoubleLoose(stk.get(k)) > 1e-9) {
                return false;
            }
        }
        return true;
    }

    private static void appendBriefPurchaseList(StringBuilder sb, String title, Object listObj, String nameKey, int max) {
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder();
        int n = 0;
        for (Object o : list) {
            if (n >= max) {
                break;
            }
            if (o instanceof Map<?, ?> row) {
                Object nm = row.get(nameKey);
                if (nm == null) {
                    nm = row.get("goodsName");
                }
                if (nm != null && !nm.toString().isBlank()) {
                    line.append(nm.toString().trim()).append("；");
                    n++;
                }
            }
        }
        if (n > 0) {
            sb.append(title).append("：").append(line).append("\n");
        }
    }

    private static boolean purchaseHasDataRows(Map<String, Object> p) {
        if (p == null || p.isEmpty()) {
            return false;
        }
        Object rc = p.get("purchaseRowCount");
        if (rc instanceof Number n) {
            return n.intValue() > 0;
        }
        try {
            return rc != null && Integer.parseInt(rc.toString().trim()) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isBusinessToWarehouseStockConvergence(AiRunState state) {
        Map<String, String> ic = state.getIntentConvergence();
        return ic != null
                && "BUSINESS_OVERVIEW".equals(ic.get("from"))
                && "WAREHOUSE_STOCK_OVERVIEW".equals(ic.get("to"));
    }

    private static String resolveAiRoleCode(AiRunState state) {
        AiUserContext ctx = state.getAiUserContext();
        if (ctx != null && ctx.getRoleCode() != null && !ctx.getRoleCode().isBlank()) {
            return ctx.getRoleCode().trim();
        }
        if (ctx != null && ctx.getSourceAdminRole() != null) {
            return AiRoleMapper.resolveAdmin(ctx.getSourceAdminRole())
                    .map(AiRoleMapper.AiRoleDefinition::roleCode)
                    .orElse("");
        }
        return "";
    }

    private static boolean isPurchasingRoleForWarehouse(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return AiRoleCodes.STORE_PURCHASER.equals(roleCode)
                || AiRoleCodes.GROUP_PURCHASER.equals(roleCode)
                || AiRoleCodes.WAREHOUSE_PURCHASER.equals(roleCode)
                || AiRoleCodes.CENTRAL_KITCHEN_PURCHASER.equals(roleCode)
                || AiRoleCodes.REGION_PURCHASER.equals(roleCode);
    }

    private static boolean isWarehouseStaffRole(String roleCode) {
        return AiRoleCodes.WAREHOUSE_MANAGER.equals(roleCode)
                || AiRoleCodes.REGION_WAREHOUSE.equals(roleCode);
    }

    /**
     * 是否禁止在库存答复中使用「店长」等称呼（与产品约定一致：仅非集团视角下的店长岗可称店长）。
     */
    private static boolean forbidsStoreManagerSalutation(String roleCode, boolean groupScope) {
        if (groupScope) {
            return true;
        }
        if (roleCode == null || roleCode.isBlank()) {
            return true;
        }
        if (AiRoleCodes.GROUP_MANAGER.equals(roleCode)) {
            return true;
        }
        if (isWarehouseStaffRole(roleCode)) {
            return true;
        }
        if (isPurchasingRoleForWarehouse(roleCode)) {
            return true;
        }
        return !AiRoleCodes.STORE_MANAGER.equals(roleCode);
    }

    private static boolean warehouseOverviewIndicatesGroupScope(Map<String, Object> wo) {
        return wo != null && !wo.isEmpty()
                && "GROUP".equalsIgnoreCase(String.valueOf(wo.get("scopeType")).trim());
    }

    private static String warehouseSalutationDirective(AiRunState state, Map<String, Object> wo) {
        String rc = resolveAiRoleCode(state);
        boolean groupScope = state.isGroupWarehouseStockOverview() || warehouseOverviewIndicatesGroupScope(wo);
        if (AiRoleCodes.GROUP_MANAGER.equals(rc) || groupScope) {
            return "【开篇】用「以下是集团范围库存汇总」或等价客观句起首（可接门店名枚举）；禁止「店长」「老板」及「好的，店长」类寒暄；不要反问指定门店。";
        }
        if (AiRoleCodes.STORE_MANAGER.equals(rc)) {
            return "【开篇】可称呼「店长」，也可无称呼直接写库存数据。";
        }
        if (isWarehouseStaffRole(rc)) {
            return "【开篇】用「以下是你当前可查看库房/所属门店」类客观句起首（可与 queryScopeBanner 一致带出门店名）；禁止「店长」「老板」；勿写「店长，本库房…」。";
        }
        if (isPurchasingRoleForWarehouse(rc)) {
            return "【开篇】可用「以下按采购视角分析」起首（再写库存数字）；禁止「店长」「老板」。";
        }
        return "【开篇】若不确定对方具体岗位，不要使用老板/店长/库管等称呼；直接写库存客观表述。";
    }

    private static String applyWarehouseSalutationPolicy(String answer, AiRunState state, Map<String, Object> wo) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        String rc = resolveAiRoleCode(state);
        boolean groupScope = state.isGroupWarehouseStockOverview() || warehouseOverviewIndicatesGroupScope(wo);
        if (!forbidsStoreManagerSalutation(rc, groupScope)) {
            return answer;
        }
        String t = answer.trim();
        for (int i = 0; i < 10; i++) {
            Matcher m1 = WAREHOUSE_LEADING_SALUTATION.matcher(t);
            if (m1.find() && m1.start() == 0) {
                t = t.substring(m1.end()).trim();
                continue;
            }
            Matcher m2 = WAREHOUSE_LEADING_MANAGER_NO_PUNCT.matcher(t);
            if (m2.find() && m2.start() == 0) {
                t = t.substring(m2.end()).trim();
                continue;
            }
            break;
        }
        return t.isBlank() ? answer : t;
    }

    private static LinkedHashMap<String, Object> summarizeWarehouseToolPresenceCn(AiRunState state) {
        Map<String, Object> wo = extractWarehouseOverviewPayload(state);
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("用户问题", nz(state.getNormalizedUserInput()));
        m.put("称谓与开篇_模型须严格遵守", warehouseSalutationDirective(state, wo));
        if (!wo.isEmpty()) {
            m.put("库房库存概览工具已聚合", true);
            Object qb = wo.get("queryScopeBanner");
            if (qb != null && !qb.toString().isBlank()) {
                m.put("查询范围_queryScopeBanner", qb.toString().trim());
            }
            if (state.isGroupWarehouseStockOverview()) {
                boolean mixWh = warehouseOverviewHasVisibleWarehouses(wo);
                m.put("查询范围",
                        mixWh ? "集团下属门店/库房库存汇总（默认不按登录 departmentId 单一门店）"
                                : "集团下属门店库存汇总（默认不按登录 departmentId 单一门店）");
                m.put("答复禁忌", "禁止反问指定门店或品类；勿默认称呼「店长」；禁止营业额/订单/客单价。");
            }
            Object st = wo.get("scopeType");
            if (st != null && !st.toString().isBlank()) {
                m.put("scopeType", st.toString().trim());
            }
            Object sn = wo.get("scopeName");
            if (sn != null && !sn.toString().isBlank()) {
                m.put("scopeName", sn.toString().trim());
            }
            if (wo.containsKey("visibleStoreCount")) {
                m.put("纳入门店数_visibleStoreCount", plainNumericHint(wo.get("visibleStoreCount")));
            }
            if (wo.containsKey("dataAvailableStoreCount")) {
                m.put("有库存信号门店数", plainNumericHint(wo.get("dataAvailableStoreCount")));
            }
            if (wo.containsKey("dataMissingStoreCount")) {
                m.put("暂无库存信号门店数", plainNumericHint(wo.get("dataMissingStoreCount")));
            }
            if (wo.get("coveredStores") instanceof List<?> cov && !cov.isEmpty()) {
                m.put("有数据门店摘要条数", cov.size());
            }
            if (wo.get("dataMissingStores") instanceof List<?> miss && !miss.isEmpty()) {
                m.put("缺数据门店摘要条数", miss.size());
            }
            m.put("摘要_summary", nz(wo.get("summary")));
            m.put("库存商品种数", plainNumericHint(wo.get("stockItemCount")));
            m.put("库存批次行数", plainNumericHint(wo.get("stockBatchRowCount")));
            m.put("库存剩余总金额约_元", plainNumericHint(wo.get("totalStockAmount")));
            m.put("库存剩余总重量", fmtStockWeightCn(wo.get("totalStockWeight")));
            m.put("区间内入库金额约_元", plainNumericHint(wo.get("inboundAmount")));
            m.put("区间内入库重量", fmtStockWeightCn(wo.get("inboundWeight")));
            m.put("核销出品金额", plainNumericHint(wo.get("produceAmount")));
            m.put("核销出库合计金额", plainNumericHint(wo.get("stockReduceAmount")));
            m.put("核销废弃金额_type2", plainNumericHint(wo.get("wasteAmount")));
            m.put("核销损耗金额_type3", plainNumericHint(wo.get("lossAmount")));
            m.put("核销退货金额", plainNumericHint(wo.get("returnAmount")));
            m.put("低库存商品条目", wo.get("lowStockItems"));
            m.put("积压偏高商品条目", wo.get("overStockItems"));
            m.put("早入库仍有剩余批次", wo.get("inactiveStockItems"));
            m.put("建议动作", wo.get("recommendations"));
        } else {
            Map<String, Object> sq = toolDataInnerMap(state, AiBusinessToolIds.STOCK_QUERY);
            Map<String, Object> stk = toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
            m.put("库房库存快照有结果", !sq.isEmpty());
            m.put("核销出库有结果", !stk.isEmpty());
            if (!sq.isEmpty()) {
                m.put("库存批次行数", plainNumericHint(sq.get("stockBatchRowCount")));
                m.put("库存剩余金额约_元", plainNumericHint(sq.get("stockRestSubtotal")));
                m.put("库存剩余重量汇总", fmtStockWeightCn(sq.get("stockRestWeightTotal")));
                m.put("区间内入库批次金额约_元", plainNumericHint(sq.get("periodInboundSubtotal")));
                m.put("区间内入库重量汇总", fmtStockWeightCn(sq.get("periodInboundWeightTotal")));
            }
            if (!stk.isEmpty()) {
                m.put("核销生产耗用合计", plainNumericHint(stk.get("productionTotal")));
                m.put("核销出品", plainNumericHint(stk.get("produceTotal")));
                m.put("核销废弃_type2", plainNumericHint(stk.get("wasteTotal")));
                m.put("核销损耗_type3", plainNumericHint(stk.get("lossTotal")));
                m.put("核销退货", plainNumericHint(stk.get("returnTotal")));
            }
        }
        if (isBusinessToWarehouseStockConvergence(state)) {
            m.put("答复口径", "经营类话术已切换为库房库存视角：禁止营业额与菜品销售；不作采购员式采购分析主线。");
        } else if (state.isGroupWarehouseStockOverview()) {
            m.put("答复口径", "集团库存汇总：开篇写明集团范围；禁止反问指定门店；禁止营业额/订单/客单价；勿默认称呼店长。");
        }
        return m;
    }

    private static Map<String, Object> buildWarehouseOverviewStructured(AiRunState state) {
        Map<String, Object> wo = extractWarehouseOverviewPayload(state);
        if (!wo.isEmpty()) {
            return new LinkedHashMap<>(wo);
        }
        Map<String, Object> sq = toolDataInnerMap(state, AiBusinessToolIds.STOCK_QUERY);
        Map<String, Object> stk = toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        LinkedHashMap<String, Object> legacy = new LinkedHashMap<>();
        List<String> metrics = new ArrayList<>();
        if (!sq.isEmpty()) {
            metrics.add("库存批次行数 " + plainNumericHint(sq.get("stockBatchRowCount")));
            metrics.add("库存剩余金额约 " + plainNumericHint(sq.get("stockRestSubtotal")) + " 元");
            metrics.add("库存剩余重量 " + fmtStockWeightCn(sq.get("stockRestWeightTotal")));
            metrics.add("区间内入库金额约 " + plainNumericHint(sq.get("periodInboundSubtotal")) + " 元");
            metrics.add("区间内入库重量 " + fmtStockWeightCn(sq.get("periodInboundWeightTotal")));
        }
        if (!stk.isEmpty()) {
            metrics.add("核销生产耗用合计 " + plainNumericHint(stk.get("productionTotal")));
        }
        legacy.put("keyMetrics", metrics);
        legacy.put("stockWarnings", new ArrayList<String>());
        List<String> rec = new ArrayList<>();
        rec.add("重点核对盘点剩余与核销明细是否闭合；异常批次建议在库存模块复查。");
        legacy.put("recommendations", rec);
        String summary = (sq.isEmpty() && stk.isEmpty())
                ? "暂无可用库房库存汇总数据。"
                : "已按库房权限汇总库存剩余与区间内入库，并结合核销/出库结构给出摘要（旧版降级字段）。";
        legacy.put("summary", summary);
        return legacy;
    }

    private static String warehouseStockFallback(AiRunState state) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        Map<String, Object> wo = extractWarehouseOverviewPayload(state);
        if (!wo.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            Object qb = wo.get("queryScopeBanner");
            if (qb != null && !qb.toString().isBlank()) {
                sb.append(qb.toString().trim()).append("\n\n");
            }
            sb.append(tw.getBracketTimeRangeLine()).append("\n");
            boolean group = "GROUP".equalsIgnoreCase(String.valueOf(wo.get("scopeType")).trim());
            boolean groupStoresOnly = group && !warehouseOverviewHasVisibleWarehouses(wo);
            sb.append(group
                    ? (groupStoresOnly
                            ? "说明：以下为集团下属门店合并库存汇总（按门店根部门逐店聚合），不包含营业额、订单、客单价、毛利或利润。\n"
                            : "说明：以下为集团下属门店/库房合并库存汇总（按门店根部门逐店聚合），不包含营业额、订单、客单价、毛利或利润。\n")
                    : "说明：以下按库房库存视角汇总，不包含营业额、订单、客单价、毛利或利润；不作采购员式采购分析。\n");
            sb.append(nz(wo.get("summary"))).append("\n\n");
            sb.append("【库存规模】约有 ").append(plainNumericHint(wo.get("stockItemCount")))
                    .append(" 种商品仍有账面剩余（全库批次约 ")
                    .append(plainNumericHint(wo.get("stockBatchRowCount"))).append(" 行）；库存剩余总金额约 ")
                    .append(plainNumericHint(wo.get("totalStockAmount"))).append(" 元，剩余总重量约 ")
                    .append(fmtStockWeightCn(wo.get("totalStockWeight"))).append("。\n");
            sb.append("【入库】入库金额约 ").append(plainNumericHint(wo.get("inboundAmount")))
                    .append(" 元，入库重量约 ").append(fmtStockWeightCn(wo.get("inboundWeight"))).append("。\n");
            sb.append("【核销/出库】出品约 ").append(plainNumericHint(wo.get("produceAmount")))
                    .append(" 元；废弃 ").append(plainNumericHint(wo.get("wasteAmount")))
                    .append(" 元，损耗 ").append(plainNumericHint(wo.get("lossAmount")))
                    .append(" 元，退货 ").append(plainNumericHint(wo.get("returnAmount")))
                    .append(" 元；各类型合计约 ").append(plainNumericHint(wo.get("stockReduceAmount")))
                    .append(" 元。\n\n");
            appendWarehouseConcernSection(sb, "低库存 / 需补货", wo.get("lowStockItems"),
                    WarehouseConcernKind.LOW);
            appendWarehouseConcernSection(sb, "库存偏高 / 建议优先消耗", wo.get("overStockItems"),
                    WarehouseConcernKind.OVER);
            appendWarehouseConcernSection(sb, "早入库批次 / 建议盘点", wo.get("priorityStocktakeItems"),
                    WarehouseConcernKind.INACTIVE);
            appendWarehouseRecommendations(sb, wo.get("recommendations"));
            return sb.toString().trim();
        }
        Map<String, Object> sq = toolDataInnerMap(state, AiBusinessToolIds.STOCK_QUERY);
        Map<String, Object> stk = toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        sb.append("说明：以下按库房库存视角汇总，不包含营业额、订单、客单价、毛利或集团经营口径；不作采购员式的采购专项分析。\n");
        boolean hasStock = stockSnapshotHasSignal(sq, stk, extractWarehouseOverviewPayload(state));
        if (!sq.isEmpty() && hasStock) {
            sb.append("当前库房库存侧：可见批次约 ")
                    .append(plainNumericHint(sq.get("stockBatchRowCount")))
                    .append(" 行；库存剩余金额约 ")
                    .append(plainNumericHint(sq.get("stockRestSubtotal")))
                    .append(" 元，剩余重量汇总 ")
                    .append(fmtStockWeightCn(sq.get("stockRestWeightTotal")))
                    .append("。\n");
            sb.append("查询区间内入库批次金额约 ")
                    .append(plainNumericHint(sq.get("periodInboundSubtotal")))
                    .append(" 元，入库重量汇总 ")
                    .append(fmtStockWeightCn(sq.get("periodInboundWeightTotal")))
                    .append("。\n");
        }
        if (!stk.isEmpty()) {
            sb.append("核销/出库：生产耗用合计约 ")
                    .append(plainNumericHint(stk.get("productionTotal")))
                    .append("（出品 ")
                    .append(plainNumericHint(stk.get("produceTotal")))
                    .append("，废弃 ")
                    .append(plainNumericHint(stk.get("wasteTotal")))
                    .append("，损耗 ")
                    .append(plainNumericHint(stk.get("lossTotal")))
                    .append("，退货 ")
                    .append(plainNumericHint(stk.get("returnTotal")))
                    .append("）。\n");
        }
        if (!hasStock && stk.isEmpty()) {
            sb.append("你当前账号可查看库房库存数据，但当前库房暂未查询到有效库存记录。\n");
        } else if (!hasStock && !stk.isEmpty()) {
            sb.append("库存快照侧暂未拉到有效剩余汇总，可先依据核销/出库数据核对是否与实物一致。\n");
        }
        sb.append("如需单品预警或批次明细，请在库存管理模块按商品/批次下钻。");
        return sb.toString().trim();
    }

    private enum WarehouseConcernKind {
        LOW,
        OVER,
        INACTIVE
    }

    private static void appendWarehouseConcernSection(StringBuilder sb, String title, Object listObj,
            WarehouseConcernKind kind) {
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        sb.append(title).append("：\n");
        int i = 1;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> mm)) {
                continue;
            }
            Object snStore = mm.get("storeName");
            Object nm = mm.get("goodsName");
            if (nm == null || nm.toString().isBlank()) {
                continue;
            }
            String goods = sanitizeWarehouseGoodsLabel(nm.toString().trim());
            Object rw = mm.get("restWeightTotal");
            Object ra = mm.get("restAmountTotal");
            Object bd = mm.get("batchDate");
            Object rw2 = mm.get("restWeight");
            Object batchId = mm.get("stockBatchId");

            sb.append(i++).append(". ");
            if (snStore != null && !snStore.toString().isBlank()) {
                sb.append(snStore.toString().trim()).append(" · ");
            }
            if (kind == WarehouseConcernKind.LOW) {
                Object wSrc = pickWeightForDisplay(rw, rw2);
                sb.append(goods).append("：")
                        .append(formatRestWeightPhrase(wSrc, mm))
                        .append("，金额 ")
                        .append(plainNumericHint(ra))
                        .append(" 元。建议关注补货。\n");
            } else if (kind == WarehouseConcernKind.OVER) {
                sb.append(goods).append("：")
                        .append(formatRestWeightPhrase(rw, mm))
                        .append("，金额 ")
                        .append(plainNumericHint(ra))
                        .append(" 元。\n");
            } else {
                Object wSrc = pickWeightForDisplay(rw2, rw);
                sb.append(goods).append("：");
                if (batchId != null && !batchId.toString().isBlank()) {
                    sb.append("库存批次号 ").append(batchId.toString().trim()).append("，");
                }
                if (bd != null && !bd.toString().isBlank()) {
                    sb.append(bd.toString().trim()).append(" 入库的批次仍有剩余 ")
                            .append(stockWeightNumberOnly(wSrc))
                            .append(" ")
                            .append(weightUnitSuffix(mm))
                            .append("，建议盘点核对。\n");
                } else {
                    sb.append("仍有剩余 ")
                            .append(stockWeightNumberOnly(wSrc))
                            .append(" ")
                            .append(weightUnitSuffix(mm))
                            .append("，建议盘点核对。\n");
                }
            }
            if (i > 9) {
                break;
            }
        }
        sb.append("\n");
    }

    private static Object pickWeightForDisplay(Object primary, Object secondary) {
        if (primary != null && !primary.toString().isBlank()) {
            return primary;
        }
        return secondary;
    }

    /** 与「剩余 0.7 斤」可读口径一致；若条目带 weightDisplayUnit 则用该单位，否则用斤。 */
    private static String formatRestWeightPhrase(Object weightObj, Map<?, ?> item) {
        return "剩余 " + stockWeightNumberOnly(weightObj) + " " + weightUnitSuffix(item);
    }

    private static String weightUnitSuffix(Map<?, ?> item) {
        if (item == null) {
            return W_STOCK_WEIGHT_UNIT;
        }
        Object u = item.get("weightDisplayUnit");
        if (u != null && !u.toString().isBlank()) {
            return u.toString().trim();
        }
        return W_STOCK_WEIGHT_UNIT;
    }

    private static String sanitizeWarehouseGoodsLabel(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("（积压）", "").replace("(积压)", "").trim();
    }

    private static void appendWarehouseRecommendations(StringBuilder sb, Object recObj) {
        if (!(recObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        sb.append("建议：\n");
        int i = 1;
        for (Object o : list) {
            if (o == null || o.toString().isBlank()) {
                continue;
            }
            sb.append(i++).append(". ").append(o.toString().trim()).append("\n");
            if (i > 6) {
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractPurchaseOverviewPayload(AiRunState state) {
        Map<String, Object> inner = toolDataInnerMap(state, AiBusinessToolIds.PURCHASE_OVERVIEW);
        Object po = inner.get("purchaseOverview");
        if (!(po instanceof Map)) {
            return Map.of();
        }
        Map<String, Object> raw = (Map<String, Object>) po;
        if (raw.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(raw);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractWarehouseOverviewPayload(AiRunState state) {
        Map<String, Object> inner = toolDataInnerMap(state, AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        Object wo = inner.get("warehouseOverview");
        if (!(wo instanceof Map)) {
            return Map.of();
        }
        Map<String, Object> raw = (Map<String, Object>) wo;
        if (raw.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(raw);
    }

    private static boolean stockSnapshotHasSignal(Map<String, Object> sq, Map<String, Object> stk,
            Map<String, Object> wo) {
        if (wo != null && !wo.isEmpty()) {
            int sku = intHint(wo.get("stockItemCount"));
            double amt = parseDoubleLoose(wo.get("totalStockAmount"));
            double wt = parseDoubleLoose(wo.get("totalStockWeight"));
            double inbound = parseDoubleLoose(wo.get("inboundAmount"));
            double reduce = parseDoubleLoose(wo.get("stockReduceAmount"));
            return sku > 0 || amt > 0 || wt > 0 || inbound > 0 || reduce > 0;
        }
        if (sq == null || sq.isEmpty()) {
            return stk != null && !stk.isEmpty();
        }
        Object rc = sq.get("stockBatchRowCount");
        int rows = 0;
        if (rc instanceof Number n) {
            rows = n.intValue();
        } else if (rc != null) {
            try {
                rows = Integer.parseInt(rc.toString().trim());
            } catch (Exception ignored) {
                rows = 0;
            }
        }
        double rest = parseDoubleLoose(sq.get("stockRestSubtotal"));
        double inbound = parseDoubleLoose(sq.get("periodInboundSubtotal"));
        return rows > 0 || rest > 0 || inbound > 0;
    }

    private static int intHint(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDoubleLoose(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** 出库/核销专线 stub：可读数字 + 分项，避免走错采购/成本话术。 */
    private static String stockReduceQueryDeterministicFallback(AiRunState state) {
        Map<String, Object> d = toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        if (d == null || d.isEmpty()) {
            return "暂时没有拿到出库/核销汇总数据，请确认统计周期与门店权限后重试。";
        }
        boolean mock = Boolean.TRUE.equals(toolEnvelope(state, AiBusinessToolIds.STOCK_REDUCE_QUERY).get("mock"));
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        String timeLine = nz(tw.getTimeSubjectText());
        String basisNote = "CALENDAR_NATURAL_DAY".equals(String.valueOf(d.get("totalsBasis")))
                ? "（自然日历日四类金额合计；不按「仅日营业额日」过滤）"
                : "（与同段成本工具一致：仅日营业额日核销口径）";

        List<String> storeNames = new ArrayList<>();
        AiResolvedQueryContext ctx = state != null ? state.getResolvedQueryContext() : null;
        if (ctx != null && ctx.getOrgScope() != null && ctx.getOrgScope().getVisibleStores() != null) {
            for (AiStoreScopeDTO s : ctx.getOrgScope().getVisibleStores()) {
                if (s != null && s.getStoreName() != null && !s.getStoreName().isBlank()) {
                    storeNames.add(s.getStoreName().trim());
                }
            }
        }
        boolean groupAgg = Boolean.TRUE.equals(d.get("groupStockReduceAggregation"));
        String scopeLine = storeNames.isEmpty()
                ? (groupAgg ? "范围为集团你可查看门店集合。" : "范围为当前账号可见门店。")
                : ("门店：" + String.join("、", storeNames) + "。");

        String wireDetail =
                ctx != null && ctx.getQueryIntent() != null ? nz(ctx.getQueryIntent().getStructuredIntentDetail()) : "";

        String pAmt = plainNumericHint(d.get("produceTotal"));
        String wAmt = plainNumericHint(d.get("wasteTotal"));
        String lAmt = plainNumericHint(d.get("lossTotal"));
        String rAmt = plainNumericHint(d.get("returnTotal"));
        String gAmt = plainNumericHint(d.get("grandTotalFourTypes"));

        if (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(wireDetail)) {
            Object rawTop = d.get("topGoodsOutboundBySubtotal");
            StringBuilder ranks = new StringBuilder();
            if (rawTop instanceof List<?> list) {
                int idx = 0;
                for (Object rowObj : list) {
                    if (!(rowObj instanceof Map<?, ?> row)) {
                        continue;
                    }
                    if (idx >= 5) {
                        break;
                    }
                    if (ranks.length() > 0) {
                        ranks.append(' ');
                    }
                    ranks.append(idx + 1).append(')').append(nz(row.get("name"))).append(" ")
                            .append(plainNumericHint(row.get("amount"))).append(" 元.");
                    idx++;
                }
            }
            String mockNote = mock ? "（提示：当前结果为占位或数据源不足，请稍后重试。）" : "";
            return String.format("%s在%s%s，%s出库金额最高的商品：%s%s",
                    mock ? "[数据待完善] " : "",
                    timeLine.isBlank() ? "该时段" : timeLine,
                    basisNote,
                    scopeLine,
                    ranks.length() > 0 ? ranks.toString() : "暂未查询到明细。",
                    mockNote);
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PRODUCE_CONSUME.equals(wireDetail)) {
            return String.format("在%s%s，%s生产耗用（type1）出库金额合计约 %s 元。分项：废弃 %s 元、损耗 %s 元、退货 %s 元%s",
                    timeLine.isBlank() ? "该时段" : timeLine, basisNote, scopeLine,
                    pAmt, wAmt, lAmt, rAmt, mock ? " [mock]" : "");
        }
        if (AiQuerySemanticLexicon.STRUCTURED_WASTE.equals(wireDetail)) {
            return String.format("在%s%s，%s废弃（type2）出库金额约 %s 元。", timeLine.isBlank() ? "该时段" : timeLine,
                    basisNote, scopeLine, wAmt) + (mock ? " [mock]" : "");
        }
        if (AiQuerySemanticLexicon.STRUCTURED_LOSS.equals(wireDetail)) {
            return String.format("在%s%s，%s损耗（type3，口语常称报损）出库金额约 %s 元。", timeLine.isBlank() ? "该时段" : timeLine,
                    basisNote, scopeLine, lAmt) + (mock ? " [mock]" : "");
        }
        if (AiQuerySemanticLexicon.STRUCTURED_RETURN.equals(wireDetail)) {
            return String.format("在%s%s，%s退货出库（type4）金额约 %s 元。", timeLine.isBlank() ? "该时段" : timeLine,
                    basisNote, scopeLine, rAmt) + (mock ? " [mock]" : "");
        }

        String head = mock ? "[占位/不完整数据] " : "";
        return head + String.format(
                "%s%s，%s出库/核销金额合计（四类之和）约 %s 元，其中生产耗用 %s 元、废弃 %s 元、损耗 %s 元、退货 %s 元。",
                timeLine.isBlank() ? "该时段" : timeLine, basisNote, scopeLine,
                gAmt, pAmt, wAmt, lAmt, rAmt);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolEnvelope(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        return env instanceof Map ? (Map<String, Object>) env : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolDataInnerMap(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return Map.of();
        }
        Object data = ((Map<String, Object>) env).get("data");
        if (!(data instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) data;
    }

    /** 不向模型暴露 Workspace、Tool 英文名与原始 trace。 */
    private static LinkedHashMap<String, Object> composeSafeFallbackContext(AiRunState state) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("本轮用户输入", nz(state.getNormalizedUserInput()));
        String start = state.getStatStartDate();
        String end = state.getStatEndDate();
        if (start != null && end != null && !start.isBlank() && !end.isBlank()) {
            m.put("统计口径起止日期", start + " 至 " + end);
        }
        return m;
    }
}
