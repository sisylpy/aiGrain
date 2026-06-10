package com.nongxinle.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.ai.DeepSeekCompletionClient;
import com.nongxinle.ai.orchestration.GoodsCatalogL1L2PickParser;
import com.nongxinle.ai.orchestration.GoodsCatalogL1L2PickParser.BranchDecision;
import com.nongxinle.ai.orchestration.GoodsCatalogL1L2PickParser.ParsedBranch;
import com.nongxinle.ai.orchestration.GoodsCatalogExpandLlmParser;
import com.nongxinle.ai.orchestration.GoodsCatalogExpandLlmParser.ParsedExpand;
import com.nongxinle.ai.orchestration.GoodsCatalogMatchLlmParser;
import com.nongxinle.ai.orchestration.GoodsCatalogMatchLlmParser.Decision;
import com.nongxinle.ai.orchestration.GoodsCatalogMatchLlmParser.ParsedMatch;
import com.nongxinle.ai.orchestration.GoodsNameNearAliasParser;
import com.nongxinle.ai.orchestration.GoodsNameNearAliasParser.ParsedAliases;
import com.nongxinle.dto.GbAiGoodsAddAnalyzeRequest;
import com.nongxinle.dto.GbAiGoodsAddConfirmRequest;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerStandardEntity;
import com.nongxinle.entity.NxGoodsEntity;
import com.nongxinle.service.GbAiGoodsAddService;
import com.nongxinle.service.GbAiGoodsAddSessionStore;
import com.nongxinle.service.GbAiGoodsAddSessionStore.GoodsAddSessionSnapshot;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerStandardService;
import com.nongxinle.service.NxGoodsService;
import com.nongxinle.utils.R;

import static com.nongxinle.utils.PinYin4jUtils.getHeadStringByString;
import static com.nongxinle.utils.PinYin4jUtils.hanziToPinyin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbAiGoodsAddServiceImpl implements GbAiGoodsAddService {

    /** IDE / 日志检索统一前缀，调试时请过滤 {@code [AI-GOODS-ADD]} */
    private static final String L = "[AI-GOODS-ADD]";

    private static final int MAX_USER_PURPOSE = 500;
    private static final int MAX_CHOICE_RETURN = 8;
    /** 一级下多个二级分支合并拉 SKU 时，最多合并几条二级 */
    private static final int MAX_BRANCH_AMBIGUOUS = 6;
    private static final int MAX_SKU_PER_GRAND = 85;
    private static final int MAX_SKU_TOTAL_FOR_TABLE = 140;
    private static final int LLM_SKU_PHASE_MAX_TOKENS = 2800;
    /** 批发商 + 农鑫目录预检索合并后最多返回条数（含同父兄弟 SKU） */
    private static final int MAX_DIS_CATALOG_PREFETCH = 48;
    /** DeepSeek「近义检索词」一步 max_tokens */
    private static final int GOODS_NAME_ALIAS_LLM_MAX_TOKENS = 420;
    /** DeepSeek「近义词」最多用于库检索的词数（与提示词一致）。 */
    private static final int MAX_ALIAS_QUERY_TERMS = 3;

    /** 预检索结果：列表内全部为「本批发商已入库」SKU（仅占位跳转下单等）。 */
    private static final String FLOW_DIS_CATALOG_GB_ONLY = "DIS_CATALOG_GB_ONLY";
    /** 预检索结果：列表内全部为农鑫目录有、批发商尚未下载的 SKU。 */
    private static final String FLOW_DIS_CATALOG_NX_ONLY = "DIS_CATALOG_NX_ONLY";
    /** 预检索结果：既有已入库 SKU，又有待下载 SKU。 */
    private static final String FLOW_DIS_CATALOG_MIXED = "DIS_CATALOG_MIXED";
    /** 该 nx SKU 已对应本批发商商品 */
    private static final String DIS_IMPORT_ALREADY = "ALREADY_IN_MY_GOODS";
    /** 农鑫目录有该 SKU，本批发商尚未创建对应商品，可 confirm 下载 */
    private static final String DIS_IMPORT_NOT = "NOT_DOWNLOADED";
    private static final String DIS_PREFETCH_HINT_MIXED =
            "下列商品中包含您已有批发商商品（可直接去下单）与仍未下载的目录规格（选一后确认加入）；都不是请点「继续用 AI 对照目录」。";
    private static final String DIS_PREFETCH_HINT_GB_ONLY =
            "下列商品均已在本批发商商品清单中；选择一条去下单即可。若仍要领新规格，可改用「在同品类新增 SKU」或继续用 AI。";
    private static final String DIS_PREFETCH_HINT_NX_ONLY =
            "下列为公司目录中在售规格，尚未加入您的批发商商品；选择一条后可确认下载，无需先新增目录。若都不合适，请改用 AI。";
    /** 名称+规格与您输入完全一致时短路预检索的主文案（不再展示模糊与同父扩展说明） */
    private static final String DIS_PREFETCH_HINT_EXACT_MATCH =
            "已与目录中某项「名称与规格」完全匹配；请直接确认使用或按需操作。";
    /** 首轮「原名称」未命中后，用大模型近似词再走库时的话术前缀 */
    private static final String DIS_PREFETCH_HINT_ALIAS_BRIDGE =
            "在您输入的名称下暂未直接检出商品组合，系统已用词义接近的常用名再检索了一遍；以下为可能的候选。";

    /** 低于此分不做 SKU 文本回退（避免「果丹皮」被凑成与话梅等无关多选）；走 BRANCH_CONFIRM 让用户确认大类后扩目录 */
    private static final int MIN_SKU_TEXT_FALLBACK_SCORE = 52;

    /**
     * {@code flowState=BRANCH_CONFIRM} 且已给出 {@code branchOptions} 时的统一话术（覆盖模型返回的 userFacingSummary，避免生硬或误导）。
     */
    private static final String BRANCH_CONFIRM_ASSISTANT_HINT =
            "我们根据您填的名称，在下面给了一个推荐的一级、二级分类，您先瞅瞅是不是您心里想归的那一类——"
                    + "要是对，点确认就行，系统会在这个大类底下帮您把这件商品加上；"
                    + "要是完全不对路，您可以改用手动选目录自己挑类，也可以直接加成临时商品，怎么方便怎么来。";

    /** {@link com.nongxinle.dto.GbAiGoodsAddConfirmRequest#getNxCatalogIntent()}：沿用匹配到的目录 SKU */
    private static final String NX_INTENT_USE_MATCHED = "USE_MATCHED";
    /** 在匹配 SKU 的品名父（nx level=2）下新增一条 SKU（level=3），名称规格取用户输入 */
    private static final String NX_INTENT_ADD_SIBLING_SKU = "ADD_SIBLING_SKU";

    /** {@link com.nongxinle.dto.GbAiGoodsAddAnalyzeRequest#getAnalyzeMode()} 默认：AI 匹配 */
    private static final String ANALYZE_MODE_AI = "AI";
    /** 用户逐级自选目录，不调用 L1L2/SKU 模型 */
    private static final String ANALYZE_MODE_MANUAL = "MANUAL_CATALOG";
    /**
     * 跳过 DeepSeek 与目录匹配，当场落库临时商品并返回 SUCCESS（与 confirm TEMP 成功体一致），无需再调 confirm。
     * 等价别名（均大写比较）：{@code ADD_TEMP}、{@code TEMP_ONLY}、{@code TEMP_GOODS}。
     */
    private static final String ANALYZE_MODE_DIRECT_TEMP = "DIRECT_TEMP";
    /** 同品类下新增 nx SKU（{@link #insertSiblingSkuUnderMatchedFather}）写入 {@code nx_goods_status} */
    private static final int NX_GOODS_STATUS_AI_EXPAND = -1;
    private static final String SESSION_CATALOG_MANUAL = "MANUAL";
    /** {@code analyzeMode=DIRECT_TEMP}：名称+规格与已有批发商商品完全一致，未重复落库 */
    private static final String FLOW_TEMP_DUPLICATE = "TEMP_DUPLICATE";
    /**
     * {@code analyzeMode=DIRECT_TEMP}：名称与已有商品相同、规格不同——建议走订货规格，不新建商品行
     */
    private static final String FLOW_TEMP_ADD_ORDER_STANDARD = "TEMP_ADD_ORDER_STANDARD";

    private final NxGoodsService nxGoodsService;
    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDistributerStandardService gbDistributerStandardService;
    private final GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    private final GbAiGoodsAddSessionStore sessionStore;
    private final DeepSeekCompletionClient deepSeekCompletionClient;

    @Value("${ai.deepseek.goods-add-temperature:0.2}")
    private double goodsAddTemperature;

    @Value("${ai.deepseek.goods-add-max-tokens:900}")
    private int goodsAddMaxTokens;

    @Override
    public R analyze(GbAiGoodsAddAnalyzeRequest req) {
        log.info("{} analyze step=begin", L);
        if (req == null) {
            log.warn("{} analyze step=validate_fail reason=request_null", L);
            return errorFlow("请求体不能为空");
        }
        String goodsName = StrUtil.trimToEmpty(req.getGoodsName());
        String goodsSpec = StrUtil.trimToEmpty(req.getGoodsSpec());
        if (StrUtil.isBlank(goodsName) || StrUtil.isBlank(goodsSpec)) {
            log.warn("{} analyze step=validate_fail reason=name_or_spec_blank", L);
            return errorFlow("请填写商品名称和规格");
        }
        if (req.getDistributerId() == null || req.getDepId() == null) {
            log.warn("{} analyze step=validate_fail reason=missing_dis_or_dep", L);
            return errorFlow("缺少 distributerId 或 depId");
        }
        String goodsFurtherDescription = truncate(StrUtil.trimToEmpty(req.getGoodsFurtherDescription()), MAX_USER_PURPOSE);

        int disId = req.getDistributerId();
        int depId = req.getDepId();
        log.info("{} analyze step=input_ok disId={} depId={} depFatherId={} departmentId={} sessionIdIn={} goodsName={} goodsSpec={} furtherDescLen={}",
                L, disId, depId, req.getDepFatherId(), req.getDepartmentId(),
                StrUtil.blankToDefault(req.getSessionId(), "(new)"),
                logPreview(goodsName, 40), goodsSpec, goodsFurtherDescription.length());

        String analyzeMode = StrUtil.blankToDefault(StrUtil.trimToEmpty(req.getAnalyzeMode()), ANALYZE_MODE_AI)
                .toUpperCase(Locale.ROOT);
        if (isAnalyzeModeDirectTemp(analyzeMode)) {
            String sessionIdDirect = StrUtil.isBlank(req.getSessionId())
                    ? UUID.randomUUID().toString().replace("-", "")
                    : req.getSessionId().trim();
            log.info("{} analyze step=direct_temp skip_deepseek=1 sessionId={} disId={} depId={} analyzeModeRaw={}",
                    L, sessionIdDirect, disId, depId, analyzeMode);
            return analyzeDirectTempPersist(sessionIdDirect, req, goodsName, goodsSpec, goodsFurtherDescription);
        }

        if (req.getConfirmedGrandNxGoodsId() != null) {
            if (StrUtil.isBlank(req.getSessionId())) {
                log.warn("{} analyze step=expand_validate_fail reason=missing_sessionId", L);
                return errorFlow("确认扩充目录须携带 sessionId");
            }
            GoodsAddSessionSnapshot snapExpand = sessionStore.get(req.getSessionId().trim());
            if (snapExpand == null) {
                log.warn("{} analyze step=expand_validate_fail reason=session_miss", L);
                return errorFlow("会话已过期，请重新分析");
            }
            return analyzeExpandCatalog(req, snapExpand);
        }

        String sessionId = StrUtil.isBlank(req.getSessionId())
                ? UUID.randomUUID().toString().replace("-", "")
                : req.getSessionId().trim();

        if (ANALYZE_MODE_MANUAL.equals(analyzeMode)) {
            log.info("{} analyze step=manual_catalog sessionId={} disId={} depId={}", L, sessionId, disId, depId);
            return handleManualCatalogBrowse(req, sessionId, disId, depId, goodsName, goodsSpec, goodsFurtherDescription);
        }

        if (!Boolean.TRUE.equals(req.getSkipCatalogPrefetch())) {
            R disPre = tryDisCatalogPrefetchAnalyze(req, sessionId, disId, depId, goodsName, goodsSpec, goodsFurtherDescription);
            if (disPre != null) {
                return disPre;
            }
        } else {
            log.info("{} analyze step=skip_dis_catalog_prefetch sessionId={}", L, sessionId);
        }

        Integer pendingGgId = null;
        List<Integer> pendingGrandIds = new ArrayList<>();

        log.info("{} analyze step=two_phase_l1l2_then_sku sessionId={} disId={} depId={}", L, sessionId, disId, depId);

        List<NxGoodsEntity> l0Rows = listVisibleCatalogByLevel(0);
        List<NxGoodsEntity> l1Rows = listVisibleCatalogByLevel(1);
        Map<Integer, String> l0NameById = l0Rows.stream()
                .filter(e -> e.getNxGoodsId() != null)
                .collect(Collectors.toMap(NxGoodsEntity::getNxGoodsId, e -> StrUtil.nullToEmpty(e.getNxGoodsName()), (a, b) -> a, LinkedHashMap::new));
        String l1l2CatalogMd = buildL0L1MarkdownTable(l0Rows, l1Rows, l0NameById);
        log.info("{} analyze step=catalog_tables l0Count={} l1Count={} markdownChars={}", L, l0Rows.size(), l1Rows.size(), l1l2CatalogMd.length());

        String skillL1L2 = loadSkillFile("ai-skill-goods-catalog-l1l2.md");
        String systemL1L2 = skillL1L2 + "\n\n" + l1l2CatalogMd
                + "\n\n请只输出一个 JSON 对象，不要 Markdown 围栏或其它文字。";
        String userBlock = buildUserBlock(goodsName, goodsSpec, goodsFurtherDescription);
        List<Map<String, String>> messagesL1L2 = List.of(
                Map.of("role", "system", "content", systemL1L2),
                Map.of("role", "user", "content", userBlock)
        );
        log.info("{} analyze step=llm_l1l2_request systemChars={} userChars={} maxTokens={}", L,
                systemL1L2.length(), userBlock.length(), goodsAddMaxTokens);
        String rawL1L2 = deepSeekCompletionClient.complete(messagesL1L2, "goods-add-l1l2", goodsAddTemperature, goodsAddMaxTokens);
        log.info("{} analyze step=llm_l1l2_response rawChars={} rawPreview={}", L,
                rawL1L2 != null ? rawL1L2.length() : 0, logPreview(rawL1L2, 500));

        ParsedBranch branch = GoodsCatalogL1L2PickParser.parse(rawL1L2);
        log.info("{} analyze step=l1l2_parse structuredOk={} decision={} gg={} grand={} ambGrandSize={}", L,
                branch.structuredOk(), branch.decision(), branch.greatGrandNxGoodsId(), branch.grandNxGoodsId(),
                branch.ambiguousGrandNxGoodsIds() != null ? branch.ambiguousGrandNxGoodsIds().size() : 0);

        String flowState;
        Map<String, Object> matchSummary = null;
        List<Map<String, Object>> candidatesOut = null;
        String assistant;
        List<Integer> sessionAllowedIds = List.of();
        Map<Integer, Map<String, Object>> sessionCandidateMaps = new LinkedHashMap<>();

        if (!branch.structuredOk()) {
            log.warn("{} analyze step=l1l2_branch parse_fail -> NO_MATCH", L);
            flowState = "NO_MATCH";
            assistant = "目录分类步骤解析失败，请稍后重试或添加临时商品。";
        } else if (branch.decision() == BranchDecision.NONE) {
            flowState = "NO_MATCH";
            assistant = StrUtil.blankToDefault(branch.userFacingSummary(),
                    "未能对应到明确的一级/二级分类，可补充说明后再试或添加临时商品。");
        } else {
            Integer ggId = branch.greatGrandNxGoodsId();
            List<Integer> grandIds = resolveValidatedGrandIds(branch);
            if (ggId == null || grandIds.isEmpty()) {
                log.warn("{} analyze step=l1l2_branch validate_fail ggId={} grandIdsSize={}", L, ggId, grandIds.size());
                flowState = "NO_MATCH";
                assistant = StrUtil.blankToDefault(branch.userFacingSummary(),
                        "所选分类与库中不一致，请补充说明后再试或添加临时商品。");
            } else {
                pendingGgId = ggId;
                pendingGrandIds = new ArrayList<>(grandIds);
                List<NxGoodsEntity> skuCandidates = loadMergedSkuCandidates(ggId, grandIds, goodsName, goodsFurtherDescription);
                log.info("{} analyze step=sku_pool ggId={} grandBranches={} skuCandidateCount={}", L, ggId, grandIds.size(), skuCandidates.size());
                if (skuCandidates.isEmpty()) {
                    flowState = "BRANCH_CONFIRM";
                    assistant = "";
                    matchSummary = null;
                    candidatesOut = null;
                    sessionAllowedIds = List.of();
                    sessionCandidateMaps = new LinkedHashMap<>();
                } else {
                    Set<Integer> allowedSkuIds = skuCandidates.stream()
                            .map(NxGoodsEntity::getNxGoodsId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    Map<Integer, NxGoodsEntity> skuById = skuCandidates.stream()
                            .filter(e -> e.getNxGoodsId() != null)
                            .collect(Collectors.toMap(NxGoodsEntity::getNxGoodsId, e -> e, (a, b) -> a, LinkedHashMap::new));
                    Map<Integer, String> idToNameSku = loadAncestorNames(skuCandidates);
                    Map<Integer, Map<String, Object>> candidateMaps = new LinkedHashMap<>();
                    for (NxGoodsEntity e : skuCandidates) {
                        Map<String, Object> cand = toCandidateMap(e, idToNameSku);
                        enrichCandidateWithDisImport(cand, e, disId);
                        candidateMaps.put(e.getNxGoodsId(), cand);
                    }

                    String skillSku = loadSkillFile("ai-skill-goods-catalog-match.md");
                    String skuTable = buildSkuMarkdownTable(skuCandidates, idToNameSku);
                    String systemSku = skillSku + "\n\n【候选 SKU 表】（仅允许使用下列 nxGoodsId）\n" + skuTable
                            + "\n\n请严格遵守技能说明：只输出一个 JSON 对象，不要其它文字。";
                    List<Map<String, String>> messagesSku = List.of(
                            Map.of("role", "system", "content", systemSku),
                            Map.of("role", "user", "content", userBlock + "\n（已根据一级/二级分类限定候选范围。）\n")
                    );
                    log.info("{} analyze step=llm_sku_request systemChars={} skuRows={} maxTokens={}", L,
                            systemSku.length(), skuCandidates.size(), LLM_SKU_PHASE_MAX_TOKENS);
                    String rawSku = deepSeekCompletionClient.complete(messagesSku, "goods-add-sku-under-branch",
                            goodsAddTemperature, LLM_SKU_PHASE_MAX_TOKENS);
                    log.info("{} analyze step=llm_sku_response rawChars={} rawPreview={}", L,
                            rawSku != null ? rawSku.length() : 0, logPreview(rawSku, 600));

                    ParsedMatch parsed = GoodsCatalogMatchLlmParser.parse(rawSku, allowedSkuIds);
                    log.info("{} analyze step=sku_parse structuredOk={} decision={} pickedNxGoodsId={} ambiguousSize={}", L,
                            parsed.structuredOk(), parsed.decision(), parsed.pickedNxGoodsId(),
                            parsed.ambiguousNxGoodsIds() != null ? parsed.ambiguousNxGoodsIds().size() : 0);

                    if (!parsed.structuredOk()) {
                        log.warn("{} analyze step=sku_branch parse_fail -> NO_MATCH", L);
                        flowState = "NO_MATCH";
                        assistant = "SKU 匹配步骤解析失败，请添加临时商品或稍后重试。";
                    } else if (parsed.decision() == Decision.NONE) {
                        SkuFallbackUi fb = buildFallbackSkuWhenLlmNone(
                                skuCandidates, candidateMaps, idToNameSku, goodsName, goodsFurtherDescription,
                                parsed.userFacingSummary());
                        if (fb != null) {
                            flowState = fb.flowState();
                            assistant = fb.assistant();
                            matchSummary = fb.matchSummary();
                            candidatesOut = fb.candidatesOut();
                            sessionAllowedIds = fb.sessionAllowedIds();
                            sessionCandidateMaps = fb.sessionCandidateMaps();
                            log.info("{} analyze step=sku_none_text_fallback flowState={} whitelistSize={}", L,
                                    flowState, sessionAllowedIds.size());
                        } else {
                            flowState = "NO_MATCH";
                            assistant = StrUtil.blankToDefault(parsed.userFacingSummary(),
                                    "当前分类下无名称足够接近的目录 SKU。若您认为仍属于该大类，请下一步确认一级/二级分类；确认后可新增品名与规格 SKU，或使用临时商品。");
                            log.info("{} analyze step=sku_none_no_text_fallback -> NO_MATCH then_branch_confirm", L);
                        }
                    } else if (parsed.decision() == Decision.SINGLE && parsed.pickedNxGoodsId() != null) {
                        NxGoodsEntity pickedEntity = skuById.get(parsed.pickedNxGoodsId());
                        if (isCatalogSku(pickedEntity)) {
                            matchSummary = candidateMaps.get(parsed.pickedNxGoodsId());
                            sessionCandidateMaps.put(parsed.pickedNxGoodsId(), matchSummary);
                            sessionAllowedIds = List.of(parsed.pickedNxGoodsId());
                            flowState = "MATCH_SINGLE";
                            assistant = StrUtil.isBlank(parsed.userFacingSummary())
                                    ? "已为您对齐到目录商品，请确认后添加。" : parsed.userFacingSummary();
                        } else {
                            log.warn("{} analyze step=reject_sku_pick nxGoodsId={}", L, parsed.pickedNxGoodsId());
                            flowState = "NO_MATCH";
                            assistant = "模型选择的 SKU 无效，请添加临时商品或稍后重试。";
                        }
                    } else if (parsed.decision() == Decision.AMBIGUOUS) {
                        List<Integer> amb = parsed.ambiguousNxGoodsIds() != null ? parsed.ambiguousNxGoodsIds() : List.of();
                        List<NxGoodsEntity> resolved = new ArrayList<>();
                        for (Integer id : amb) {
                            if (id == null || resolved.size() >= MAX_CHOICE_RETURN) {
                                continue;
                            }
                            NxGoodsEntity e = skuById.get(id);
                            if (isCatalogSku(e)) {
                                resolved.add(e);
                            }
                        }
                        if (resolved.isEmpty()) {
                            flowState = "NO_MATCH";
                            assistant = StrUtil.blankToDefault(parsed.userFacingSummary(),
                                    "候选 SKU 无法确认，请添加临时商品或补充描述。");
                        } else if (resolved.size() == 1) {
                            NxGoodsEntity e = resolved.get(0);
                            matchSummary = candidateMaps.get(e.getNxGoodsId());
                            sessionCandidateMaps.put(e.getNxGoodsId(), matchSummary);
                            sessionAllowedIds = List.of(e.getNxGoodsId());
                            flowState = "MATCH_SINGLE";
                            assistant = StrUtil.isBlank(parsed.userFacingSummary())
                                    ? "已为您对齐到目录商品，请确认后添加。" : parsed.userFacingSummary();
                        } else {
                            for (NxGoodsEntity e : resolved) {
                                sessionCandidateMaps.put(e.getNxGoodsId(), candidateMaps.get(e.getNxGoodsId()));
                            }
                            sessionAllowedIds = resolved.stream().map(NxGoodsEntity::getNxGoodsId).collect(Collectors.toList());
                            candidatesOut = resolved.stream().map(e -> toCandidateMap(e, idToNameSku)).collect(Collectors.toList());
                            flowState = "MATCH_CHOICE";
                            assistant = StrUtil.isBlank(parsed.userFacingSummary())
                                    ? "请选择最符合的一项后再确认添加。" : parsed.userFacingSummary();
                            log.info("{} analyze step=branch_result flowState=MATCH_CHOICE returnIds={}", L,
                                    candidatesOut.stream().map(m -> m.get("nxGoodsId")).collect(Collectors.toList()));
                        }
                    } else {
                        flowState = "NO_MATCH";
                        assistant = "无法完成 SKU 匹配，请添加临时商品或稍后重试。";
                    }
                }
            }
        }

        List<Map<String, Object>> branchOptionsPayload = List.of();
        if ("BRANCH_CONFIRM".equals(flowState) && pendingGgId != null && !pendingGrandIds.isEmpty()) {
            branchOptionsPayload = buildBranchOptionsForConfirm(pendingGgId, pendingGrandIds, l0NameById);
        } else if ("NO_MATCH".equals(flowState) && pendingGgId != null && !pendingGrandIds.isEmpty()) {
            flowState = "BRANCH_CONFIRM";
            matchSummary = null;
            candidatesOut = null;
            sessionAllowedIds = List.of();
            sessionCandidateMaps = new LinkedHashMap<>();
            branchOptionsPayload = buildBranchOptionsForConfirm(pendingGgId, pendingGrandIds, l0NameById);
            log.info("{} analyze step=no_match_to_branch_confirm grandBranches={}", L, pendingGrandIds.size());
        }

        if ("BRANCH_CONFIRM".equals(flowState) && !branchOptionsPayload.isEmpty()) {
            assistant = BRANCH_CONFIRM_ASSISTANT_HINT;
        }

        List<Map<String, Object>> snapBranchOptions = "BRANCH_CONFIRM".equals(flowState) ? branchOptionsPayload : List.of();
        List<Integer> snapPendingGrands = pendingGgId != null ? pendingGrandIds : List.of();

        GoodsAddSessionSnapshot snap = new GoodsAddSessionSnapshot(
                sessionId, disId, req.getDepartmentId(), depId, req.getDepFatherId(),
                goodsName, goodsSpec, goodsFurtherDescription, sessionAllowedIds, sessionCandidateMaps,
                pendingGgId, snapPendingGrands, snapBranchOptions, null, System.currentTimeMillis());
        sessionStore.put(snap);
        log.info("{} analyze step=session_saved sessionId={} whitelistSize={} flowState={} branchOptionsSize={}", L, sessionId,
                sessionAllowedIds.size(), flowState, snapBranchOptions.size());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("flowState", flowState);
        data.put("assistantMessage", assistant);
        data.put("matchSummary", matchSummary);
        data.put("candidates", candidatesOut);
        data.put("branchOptions", "BRANCH_CONFIRM".equals(flowState) ? branchOptionsPayload : null);
        if ("NO_MATCH".equals(flowState) || "BRANCH_CONFIRM".equals(flowState)) {
            data.put("tempPreview", buildTempPreview(goodsName, goodsSpec, goodsFurtherDescription));
        } else {
            data.put("tempPreview", null);
        }
        data.put("persistedGoods", null);
        if ("MATCH_SINGLE".equals(flowState) || "MATCH_CHOICE".equals(flowState)) {
            data.put("nxCatalogConfirmIntents", nxCatalogConfirmIntentsPayload());
        } else {
            data.put("nxCatalogConfirmIntents", null);
        }
        if ("NO_MATCH".equals(flowState)) {
            data.put("catalogLevel1Options", toCatalogLevelOptionMaps(listVisibleCatalogByLevel(0)));
            data.put("analyzeModeHint", "NO_MATCH 时可改选手动选目录：下次请求传 analyzeMode=MANUAL_CATALOG 与同一 sessionId，从一级开始逐级选择。");
        } else {
            data.put("catalogLevel1Options", null);
            data.put("analyzeModeHint", null);
        }
        data.put("analyzeMode", ANALYZE_MODE_AI);

        R r = R.ok();
        r.put("flowState", flowState);
        r.put("data", data);
        log.info("{} analyze step=exit sessionId={} flowState={} matchSummaryNxId={} candidateReturnCount={}",
                L, sessionId, flowState,
                matchSummary != null ? matchSummary.get("nxGoodsId") : null,
                candidatesOut != null ? candidatesOut.size() : 0);
        return r;
    }

    /**
     * {@code analyzeMode=DIRECT_TEMP}：不调目录、不调 LLM；**当场落库临时商品**并返回 {@code SUCCESS}（与 **confirm** {@code TEMP} 成功体一致：{@code persistedGoods}、{@code gbDistributerGoods}、{@code gbDepartmentDisGoods}），无需再调 confirm。
     * 若同一批发商下已存在**名称 + 规格**完全相同的商品，则**不落库**，返回 {@code flowState=TEMP_DUPLICATE} 及已有商品摘要。
     * 若仅**名称相同**、主规格不同：若该规格已在同名商品的**订货规格**中存在则同 {@code TEMP_DUPLICATE}；否则返回 {@code TEMP_ADD_ORDER_STANDARD}，提示去维护订货规格。
     */
    private R analyzeDirectTempPersist(String sessionId, GbAiGoodsAddAnalyzeRequest req,
                                       String goodsName, String goodsSpec, String goodsFurtherDescription) {
        GbDistributerGoodsEntity same = findExistingDistributerGoodsByNameAndSpec(
                req.getDistributerId(), goodsName, goodsSpec);
        if (same != null && same.getGbDistributerGoodsId() != null) {
            GbDistributerGoodsEntity reloaded = gbDistributerGoodsService.getById(same.getGbDistributerGoodsId());
            if (reloaded != null) {
                same = reloaded;
            }
            GbDepartmentDisGoodsEntity depDup = ensureDepDisGoodsForDirectTemp(
                    req, sessionId, goodsName, goodsSpec, goodsFurtherDescription, same, goodsSpec);
            log.info("{} analyze direct_temp step=duplicate_hit gbDistributerGoodsId={} gbDepartmentDisGoodsId={}",
                    L, same.getGbDistributerGoodsId(),
                    depDup != null ? depDup.getGbDepartmentDisGoodsId() : null);
            return directTempDuplicatePayload(sessionId, goodsName, goodsSpec, goodsFurtherDescription, same, depDup);
        }

        List<GbDistributerGoodsEntity> sameNameRows = listDisGoodsByGoodsName(req.getDistributerId(), goodsName);
        if (!sameNameRows.isEmpty()) {
            GbDistributerGoodsEntity stdDupRef = findSameNameGoodsHavingOrderStandard(sameNameRows, goodsSpec);
            if (stdDupRef != null) {
                GbDistributerGoodsEntity ref = gbDistributerGoodsService.getById(stdDupRef.getGbDistributerGoodsId());
                if (ref == null) {
                    ref = stdDupRef;
                }
                GbDepartmentDisGoodsEntity depDup = ensureDepDisGoodsForDirectTemp(
                        req, sessionId, goodsName, goodsSpec, goodsFurtherDescription, ref, goodsSpec);
                String msg = "同名商品下已包含订货规格「" + goodsSpec + "」，无需新增商品或重复添加该规格。";
                log.info("{} analyze direct_temp step=same_name_order_std_dup gbDistributerGoodsId={}", L, ref.getGbDistributerGoodsId());
                return directTempDuplicatePayload(sessionId, goodsName, goodsSpec, goodsFurtherDescription, ref, depDup, msg);
            }
            GbDistributerGoodsEntity firstNamed = sameNameRows.get(0);
            GbDistributerGoodsEntity refGoods = gbDistributerGoodsService.getById(firstNamed.getGbDistributerGoodsId());
            if (refGoods == null) {
                refGoods = firstNamed;
            }
            GbDepartmentDisGoodsEntity depForRef = ensureDepDisGoodsForDirectTemp(
                    req, sessionId, goodsName, goodsSpec, goodsFurtherDescription, refGoods, goodsSpec);
            log.info("{} analyze direct_temp step=same_name_add_order_std hint gbDistributerGoodsId={} reqSpec={}",
                    L, refGoods.getGbDistributerGoodsId(), logPreview(goodsSpec, 20));
            return directTempAddOrderStandardPayload(sessionId, goodsName, goodsSpec, goodsFurtherDescription,
                    refGoods, depForRef);
        }

        GoodsAddSessionSnapshot snap = new GoodsAddSessionSnapshot(
                sessionId, req.getDistributerId(), req.getDepartmentId(), req.getDepId(), req.getDepFatherId(),
                goodsName, goodsSpec, goodsFurtherDescription,
                List.of(), new LinkedHashMap<>(), null, List.of(), List.of(), null, System.currentTimeMillis());
        try {
            R r = persistTempGoodsFromSnap(snap, goodsName, goodsSpec, goodsFurtherDescription);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) r.get("data");
            if (data != null) {
                data.put("analyzeMode", ANALYZE_MODE_DIRECT_TEMP);
            }
            return r;
        } catch (IllegalStateException | IllegalArgumentException ex) {
            log.warn("{} analyze direct_temp fail_biz msg={}", L, ex.getMessage());
            return errorFlow(ex.getMessage());
        } catch (Exception ex) {
            log.error("{} analyze direct_temp fail_unknown", L, ex);
            return errorFlow("落库失败，请稍后重试");
        }
    }

    /** 与 confirm {@code TEMP} 相同的落库与成功响应（不写会话；调用方负责日志）。 */
    private R persistTempGoodsFromSnap(GoodsAddSessionSnapshot snap, String gName, String gSpec, String detail) {
        Integer depFather = snap.depFatherId() != null ? snap.depFatherId() : snap.depId();
        log.info("{} persist_temp name={} spec={} detailChars={} depFather={}", L,
                logPreview(gName, 40), logPreview(gSpec, 20), detail != null ? detail.length() : 0, depFather);
        GbDistributerGoodsEntity g = gbDistributerGoodsService.saveLinshiGoodsGb(
                null, gName, gSpec, detail,
                snap.distributerId(), depFather, snap.depId(), depFather, null, null, null);
        if (g.getGbDistributerGoodsId() != null) {
            GbDistributerGoodsEntity reloaded = gbDistributerGoodsService.getById(g.getGbDistributerGoodsId());
            if (reloaded != null) {
                g = reloaded;
            }
        }
        GbDepartmentDisGoodsEntity depDis = tryCreateDepDisGoodsForJjLikeImport(snap, g, gSpec);
        Map<String, Object> persisted = persistSummary(g,
                depDis != null ? depDis.getGbDepartmentDisGoodsId() : null);
        log.info("{} persist_temp step=exit SUCCESS temp gbDistributerGoodsId={} gbDepartmentDisGoodsId={}", L,
                persisted.get("gbDistributerGoodsId"),
                depDis != null ? depDis.getGbDepartmentDisGoodsId() : null);
        return successPayload("已添加临时商品。", persisted, depDis, g);
    }

    /** 本批发商下与「商品名称 + 标准规格」字面完全一致的行（含临时品与目录品），用于直连临时添加去重。 */
    private GbDistributerGoodsEntity findExistingDistributerGoodsByNameAndSpec(int disId, String goodsName, String goodsSpec) {
        LambdaQueryWrapper<GbDistributerGoodsEntity> w = new LambdaQueryWrapper<>();
        w.eq(GbDistributerGoodsEntity::getGbDgDistributerId, disId)
                .eq(GbDistributerGoodsEntity::getGbDgGoodsName, goodsName)
                .eq(GbDistributerGoodsEntity::getGbDgGoodsStandardname, goodsSpec)
                .last("LIMIT 1");
        return gbDistributerGoodsService.getOne(w, false);
    }

    private GbDepartmentDisGoodsEntity findDepDisGoodsByDisGoodsAndDep(Integer gbDistributerGoodsId, Integer depId) {
        if (gbDistributerGoodsId == null || depId == null) {
            return null;
        }
        LambdaQueryWrapper<GbDepartmentDisGoodsEntity> w = new LambdaQueryWrapper<>();
        w.eq(GbDepartmentDisGoodsEntity::getGbDdgDisGoodsId, gbDistributerGoodsId)
                .eq(GbDepartmentDisGoodsEntity::getGbDdgDepartmentId, depId)
                .last("LIMIT 1");
        return gbDepartmentDisGoodsService.getOne(w, false);
    }

    /**
     * 直连临时：若本部门尚无 {@code gb_department_dis_goods}，则与 {@link #persistTempGoodsFromSnap} 一致补建，便于与 SUCCESS 响应字段对齐。
     * 部门订货口径见 {@code orderStandardForDep}（缺省用批发商商品主规格）。
     */
    private GbDepartmentDisGoodsEntity ensureDepDisGoodsForDirectTemp(
            GbAiGoodsAddAnalyzeRequest req,
            String sessionId,
            String goodsName,
            String goodsSpec,
            String goodsFurtherDescription,
            GbDistributerGoodsEntity gbDisGoods,
            String orderStandardForDep) {
        if (gbDisGoods == null || gbDisGoods.getGbDistributerGoodsId() == null || req.getDepId() == null) {
            return null;
        }
        GbDepartmentDisGoodsEntity existing = findDepDisGoodsByDisGoodsAndDep(
                gbDisGoods.getGbDistributerGoodsId(), req.getDepId());
        if (existing != null) {
            return existing;
        }
        GoodsAddSessionSnapshot snap = new GoodsAddSessionSnapshot(
                sessionId, req.getDistributerId(), req.getDepartmentId(), req.getDepId(), req.getDepFatherId(),
                goodsName, goodsSpec, goodsFurtherDescription,
                List.of(), new LinkedHashMap<>(), null, List.of(), List.of(), null, System.currentTimeMillis());
        String ord = StrUtil.blankToDefault(orderStandardForDep, gbDisGoods.getGbDgGoodsStandardname());
        return tryCreateDepDisGoodsForJjLikeImport(snap, gbDisGoods, ord);
    }

    private List<GbDistributerGoodsEntity> listDisGoodsByGoodsName(int disId, String goodsName) {
        LambdaQueryWrapper<GbDistributerGoodsEntity> w = new LambdaQueryWrapper<>();
        w.eq(GbDistributerGoodsEntity::getGbDgDistributerId, disId)
                .eq(GbDistributerGoodsEntity::getGbDgGoodsName, goodsName)
                .orderByAsc(GbDistributerGoodsEntity::getGbDistributerGoodsId);
        List<GbDistributerGoodsEntity> list = gbDistributerGoodsService.list(w);
        return list != null ? list : List.of();
    }

    /**
     * 在「同名」批发商商品行中，查找主规格或 {@code gb_distributer_standard} 订货规格与 {@code spec}（trim）字面一致的行。
     * 用于：主商品行规格不同但订货表里已有该规格 → 视为重复，不再引导新增。
     */
    private GbDistributerGoodsEntity findSameNameGoodsHavingOrderStandard(
            List<GbDistributerGoodsEntity> sameNameRows, String spec) {
        String t = StrUtil.trimToEmpty(spec);
        if (t.isEmpty()) {
            return null;
        }
        for (GbDistributerGoodsEntity g : sameNameRows) {
            if (g == null || g.getGbDistributerGoodsId() == null) {
                continue;
            }
            if (t.equals(StrUtil.trimToEmpty(g.getGbDgGoodsStandardname()))) {
                return g;
            }
            List<GbDistributerStandardEntity> stds = gbDistributerStandardService.queryDisStandardByDisGoodsIdGb(
                    g.getGbDistributerGoodsId());
            if (stds == null) {
                continue;
            }
            for (GbDistributerStandardEntity s : stds) {
                if (s != null && t.equals(StrUtil.trimToEmpty(s.getGbDsStandardName()))) {
                    return g;
                }
            }
        }
        return null;
    }

    /** 主规格 + 已维护的订货规格名称列表（去重保序），供小程序展示。 */
    private List<String> collectOrderStandardLabels(GbDistributerGoodsEntity g) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (g == null) {
            return List.of();
        }
        String main = StrUtil.trimToEmpty(g.getGbDgGoodsStandardname());
        if (StrUtil.isNotBlank(main)) {
            set.add(main);
        }
        if (g.getGbDistributerGoodsId() == null) {
            return new ArrayList<>(set);
        }
        List<GbDistributerStandardEntity> stds = gbDistributerStandardService.queryDisStandardByDisGoodsIdGb(
                g.getGbDistributerGoodsId());
        if (stds != null) {
            for (GbDistributerStandardEntity s : stds) {
                if (s != null && StrUtil.isNotBlank(s.getGbDsStandardName())) {
                    set.add(s.getGbDsStandardName().trim());
                }
            }
        }
        return new ArrayList<>(set);
    }

    private R directTempAddOrderStandardPayload(String sessionId, String goodsName, String goodsSpec,
                                                String goodsFurtherDescription,
                                                GbDistributerGoodsEntity referenceGoods,
                                                GbDepartmentDisGoodsEntity depDis) {
        String mainStd = StrUtil.nullToEmpty(referenceGoods.getGbDgGoodsStandardname());
        String msg = "已有同名批发商商品（当前主规格为「" + mainStd + "」）。您填写的新规格「" + goodsSpec
                + "」请在商品详情中为该商品添加「订货规格」即可，无需再新增一条商品。本次未创建新商品。";
        Map<String, Object> persisted = persistSummary(referenceGoods,
                depDis != null ? depDis.getGbDepartmentDisGoodsId() : null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("flowState", FLOW_TEMP_ADD_ORDER_STANDARD);
        data.put("assistantMessage", msg);
        data.put("matchSummary", null);
        data.put("candidates", null);
        data.put("branchOptions", null);
        data.put("tempPreview", buildTempPreview(goodsName, goodsSpec, goodsFurtherDescription));
        data.put("gbDistributerGoods", toGbDistributerGoodsApiMap(referenceGoods));
        data.put("gbDepartmentDisGoods", depDis != null ? toGbDepartmentDisGoodsApiMap(depDis) : null);
        data.put("persistedGoods", persisted);
        data.put("requestedOrderStandard", goodsSpec);
        data.put("existingOrderStandards", collectOrderStandardLabels(referenceGoods));
        data.put("analyzeMode", ANALYZE_MODE_DIRECT_TEMP);
        data.put("nxCatalogConfirmIntents", null);
        data.put("catalogLevel1Options", null);
        data.put("analyzeModeHint", null);
        data.put("catalogHitComposition", null);
        data.put("disCatalogChoices", null);
        R r = R.ok(msg);
        r.put("flowState", FLOW_TEMP_ADD_ORDER_STANDARD);
        r.put("data", data);
        log.info("{} direct_temp_add_order_standard response gbDistributerGoodsId={}", L,
                referenceGoods.getGbDistributerGoodsId());
        return r;
    }

    /**
     * 与 {@link #successPayload} 字段对齐，便于小程序统一解析；{@code flowState} 为 {@link #FLOW_TEMP_DUPLICATE}，**未写库**。
     */
    private R directTempDuplicatePayload(String sessionId, String goodsName, String goodsSpec,
                                         String goodsFurtherDescription,
                                         GbDistributerGoodsEntity existing,
                                         GbDepartmentDisGoodsEntity depDis) {
        return directTempDuplicatePayload(sessionId, goodsName, goodsSpec, goodsFurtherDescription,
                existing, depDis, null);
    }

    /**
     * @param assistantMessageOverride 非空时覆盖默认的「完全一致」提示（例如订货规格已存在）
     */
    private R directTempDuplicatePayload(String sessionId, String goodsName, String goodsSpec,
                                         String goodsFurtherDescription,
                                         GbDistributerGoodsEntity existing,
                                         GbDepartmentDisGoodsEntity depDis,
                                         String assistantMessageOverride) {
        return directTempDuplicatePayload(sessionId, goodsName, goodsSpec, goodsFurtherDescription,
                existing, depDis, assistantMessageOverride, null);
    }

    /**
     * @param analyzeModeForData 非空时写入 {@code data.analyzeMode}（默认 {@link #ANALYZE_MODE_DIRECT_TEMP}）
     */
    private R directTempDuplicatePayload(String sessionId, String goodsName, String goodsSpec,
                                         String goodsFurtherDescription,
                                         GbDistributerGoodsEntity existing,
                                         GbDepartmentDisGoodsEntity depDis,
                                         String assistantMessageOverride,
                                         String analyzeModeForData) {
        String msg = StrUtil.isNotBlank(assistantMessageOverride)
                ? assistantMessageOverride
                : "您填写的商品名称与规格与已有商品完全一致，未重复添加。";
        Map<String, Object> persisted = persistSummary(existing,
                depDis != null ? depDis.getGbDepartmentDisGoodsId() : null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("flowState", FLOW_TEMP_DUPLICATE);
        data.put("assistantMessage", msg);
        data.put("matchSummary", null);
        data.put("candidates", null);
        data.put("branchOptions", null);
        data.put("tempPreview", buildTempPreview(goodsName, goodsSpec, goodsFurtherDescription));
        data.put("gbDistributerGoods", toGbDistributerGoodsApiMap(existing));
        data.put("gbDepartmentDisGoods", depDis != null ? toGbDepartmentDisGoodsApiMap(depDis) : null);
        data.put("persistedGoods", persisted);
        data.put("analyzeMode", StrUtil.blankToDefault(analyzeModeForData, ANALYZE_MODE_DIRECT_TEMP));
        data.put("nxCatalogConfirmIntents", null);
        data.put("catalogLevel1Options", null);
        data.put("analyzeModeHint", null);
        data.put("catalogHitComposition", null);
        data.put("disCatalogChoices", null);
        R r = R.ok(msg);
        r.put("flowState", FLOW_TEMP_DUPLICATE);
        r.put("data", data);
        log.info("{} direct_temp_duplicate response gbDistributerGoodsId={}", L, existing.getGbDistributerGoodsId());
        return r;
    }

    /**
     * 用户自选目录：一级（level=0）→ 二级（1）→ 三级品名父（2）→ 四级 SKU（3）；无 SKU 或与 AI {@code NO_MATCH} 时由前端引导至扩充目录。
     */
    private R handleManualCatalogBrowse(GbAiGoodsAddAnalyzeRequest req, String sessionId, int disId, int depId,
                                        String goodsName, String goodsSpec, String goodsFurtherDescription) {
        Map<Integer, String> l0NameById = listVisibleCatalogByLevel(0).stream()
                .filter(e -> e.getNxGoodsId() != null)
                .collect(Collectors.toMap(NxGoodsEntity::getNxGoodsId, e -> StrUtil.nullToEmpty(e.getNxGoodsName()), (a, b) -> a, LinkedHashMap::new));

        if (req.getManualFatherNxGoodsId() != null) {
            NxGoodsEntity father = nxGoodsService.queryObject(req.getManualFatherNxGoodsId());
            R v = validateManualCatalogNode(father, 2, "三级品名");
            if (v != null) {
                return v;
            }
            if (req.getManualGrandNxGoodsId() != null) {
                if (!Objects.equals(father.getNxGoodsFatherId(), req.getManualGrandNxGoodsId())) {
                    return errorFlow("所选三级品名不属于当前二级分类");
                }
            }
            List<NxGoodsEntity> skus = listVisibleChildrenOfParent(father.getNxGoodsId(), 3).stream()
                    .filter(GbAiGoodsAddServiceImpl::isCatalogSku)
                    .collect(Collectors.toList());
            Map<Integer, String> idToNameSku = loadAncestorNames(skus);
            Map<Integer, Map<String, Object>> candidateMaps = new LinkedHashMap<>();
            for (NxGoodsEntity e : skus) {
                Map<String, Object> cand = toCandidateMap(e, idToNameSku);
                enrichCandidateWithDisImport(cand, e, disId);
                candidateMaps.put(e.getNxGoodsId(), cand);
            }
            List<Integer> allowed = new ArrayList<>(candidateMaps.keySet());
            Integer ggForSnap = father.getNxGoodsGreatGrandId();
            Integer grandForSnap = father.getNxGoodsFatherId();
            if (candidateMaps.isEmpty()) {
                Integer grandId = father.getNxGoodsFatherId();
                NxGoodsEntity grand = nxGoodsService.queryObject(grandId);
                if (grand == null) {
                    return errorFlow("分类数据异常");
                }
                Integer ggId = grand.getNxGoodsFatherId();
                R gv = validateManualCatalogNode(grand, 1, "二级");
                if (gv != null) {
                    return gv;
                }
                List<Map<String, Object>> branchOpts = buildBranchOptionsForConfirm(ggId, List.of(grandId), l0NameById);
                GoodsAddSessionSnapshot snap = new GoodsAddSessionSnapshot(
                        sessionId, disId, req.getDepartmentId(), depId, req.getDepFatherId(),
                        goodsName, goodsSpec, goodsFurtherDescription, List.of(), new LinkedHashMap<>(),
                        ggId, List.of(grandId), branchOpts, SESSION_CATALOG_MANUAL, System.currentTimeMillis());
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("matchSummary", null);
                fields.put("candidates", null);
                fields.put("branchOptions", branchOpts);
                fields.put("catalogLevel1Options", null);
                fields.put("catalogLevel2Options", null);
                fields.put("catalogLevel3Options", toCatalogLevelOptionMaps(List.of(father)));
                return finishManualCatalogResponse(sessionId, "MANUAL_CATALOG_BRANCH",
                        "该品名下暂无目录 SKU。请确认下方一级/二级后，将为您在农鑫目录新增三级品名与四级规格（与「确认分类并生成」相同）。",
                        fields, snap);
            }
            String flowSku;
            Map<String, Object> matchSummary = null;
            List<Map<String, Object>> candidatesOut;
            String assistant;
            if (allowed.size() == 1) {
                flowSku = "MANUAL_CATALOG_SKU_SINGLE";
                matchSummary = candidateMaps.get(allowed.get(0));
                candidatesOut = null;
                assistant = "已定位到唯一目录 SKU，请确认添加或选择「新增同品类 SKU」。";
            } else {
                flowSku = "MANUAL_CATALOG_SKU_CHOICE";
                candidatesOut = allowed.stream().map(candidateMaps::get).collect(Collectors.toList());
                assistant = "请选择要添加的四级目录商品。";
            }
            GoodsAddSessionSnapshot snap = new GoodsAddSessionSnapshot(
                    sessionId, disId, req.getDepartmentId(), depId, req.getDepFatherId(),
                    goodsName, goodsSpec, goodsFurtherDescription, allowed, candidateMaps,
                    ggForSnap, List.of(grandForSnap), List.of(), SESSION_CATALOG_MANUAL, System.currentTimeMillis());
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("matchSummary", matchSummary);
            fields.put("candidates", candidatesOut);
            fields.put("branchOptions", null);
            fields.put("catalogLevel1Options", null);
            fields.put("catalogLevel2Options", null);
            fields.put("catalogLevel3Options", toCatalogLevelOptionMaps(List.of(father)));
            return finishManualCatalogResponse(sessionId, flowSku, assistant, fields, snap);
        }

        if (req.getManualGrandNxGoodsId() != null) {
            NxGoodsEntity grand = nxGoodsService.queryObject(req.getManualGrandNxGoodsId());
            R gv = validateManualCatalogNode(grand, 1, "二级");
            if (gv != null) {
                return gv;
            }
            Integer ggId = grand.getNxGoodsFatherId();
            if (req.getManualGreatGrandNxGoodsId() != null
                    && !Objects.equals(req.getManualGreatGrandNxGoodsId(), ggId)) {
                return errorFlow("所选二级与一级不匹配");
            }
            NxGoodsEntity gg = nxGoodsService.queryObject(ggId);
            R ggv = validateManualCatalogNode(gg, 0, "一级");
            if (ggv != null) {
                return ggv;
            }
            List<NxGoodsEntity> l3 = listVisibleChildrenOfParent(grand.getNxGoodsId(), 2);
            List<Map<String, Object>> branchOpts = l3.isEmpty()
                    ? buildBranchOptionsForConfirm(ggId, List.of(grand.getNxGoodsId()), l0NameById)
                    : List.of();
            String flowL3 = l3.isEmpty() ? "MANUAL_CATALOG_BRANCH" : "MANUAL_CATALOG_L3";
            String assistantL3 = l3.isEmpty()
                    ? "当前二级下暂无三级品名目录。请确认一级/二级后，将新增三级品名与四级 SKU 到农鑫目录。"
                    : "请选择三级品名（品名父）；选好后请再请求并带上 manualFatherNxGoodsId。";
            GoodsAddSessionSnapshot snap = new GoodsAddSessionSnapshot(
                    sessionId, disId, req.getDepartmentId(), depId, req.getDepFatherId(),
                    goodsName, goodsSpec, goodsFurtherDescription, List.of(), new LinkedHashMap<>(),
                    ggId, List.of(grand.getNxGoodsId()), branchOpts, SESSION_CATALOG_MANUAL, System.currentTimeMillis());
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("matchSummary", null);
            fields.put("candidates", null);
            fields.put("branchOptions", l3.isEmpty() ? branchOpts : null);
            fields.put("catalogLevel1Options", null);
            fields.put("catalogLevel2Options", toCatalogLevelOptionMaps(List.of(grand)));
            fields.put("catalogLevel3Options", toCatalogLevelOptionMaps(l3));
            return finishManualCatalogResponse(sessionId, flowL3, assistantL3, fields, snap);
        }

        if (req.getManualGreatGrandNxGoodsId() != null) {
            NxGoodsEntity gg = nxGoodsService.queryObject(req.getManualGreatGrandNxGoodsId());
            R ggv = validateManualCatalogNode(gg, 0, "一级");
            if (ggv != null) {
                return ggv;
            }
            List<NxGoodsEntity> l2 = listVisibleChildrenOfParent(gg.getNxGoodsId(), 1);
            GoodsAddSessionSnapshot snap = new GoodsAddSessionSnapshot(
                    sessionId, disId, req.getDepartmentId(), depId, req.getDepFatherId(),
                    goodsName, goodsSpec, goodsFurtherDescription, List.of(), new LinkedHashMap<>(),
                    null, List.of(), List.of(), SESSION_CATALOG_MANUAL, System.currentTimeMillis());
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("matchSummary", null);
            fields.put("candidates", null);
            fields.put("branchOptions", null);
            fields.put("catalogLevel1Options", toCatalogLevelOptionMaps(List.of(gg)));
            fields.put("catalogLevel2Options", toCatalogLevelOptionMaps(l2));
            fields.put("catalogLevel3Options", null);
            return finishManualCatalogResponse(sessionId, "MANUAL_CATALOG_L2",
                    "请选择二级分类；选好后请带上 manualGrandNxGoodsId 再次请求。", fields, snap);
        }

        List<NxGoodsEntity> l1 = listVisibleCatalogByLevel(0);
        GoodsAddSessionSnapshot snap = new GoodsAddSessionSnapshot(
                sessionId, disId, req.getDepartmentId(), depId, req.getDepFatherId(),
                goodsName, goodsSpec, goodsFurtherDescription, List.of(), new LinkedHashMap<>(),
                null, List.of(), List.of(), SESSION_CATALOG_MANUAL, System.currentTimeMillis());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("matchSummary", null);
        fields.put("candidates", null);
        fields.put("branchOptions", null);
        fields.put("catalogLevel1Options", toCatalogLevelOptionMaps(l1));
        fields.put("catalogLevel2Options", null);
        fields.put("catalogLevel3Options", null);
        return finishManualCatalogResponse(sessionId, "MANUAL_CATALOG_L1",
                "请从一级目录开始选择；选好后请带上 manualGreatGrandNxGoodsId 再次请求。", fields, snap);
    }

    private R finishManualCatalogResponse(String sessionId, String flowState, String assistant,
                                          Map<String, Object> fields, GoodsAddSessionSnapshot snap) {
        sessionStore.put(snap);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("flowState", flowState);
        data.put("assistantMessage", assistant);
        data.put("matchSummary", fields.get("matchSummary"));
        data.put("candidates", fields.get("candidates"));
        data.put("branchOptions", fields.get("branchOptions"));
        data.put("tempPreview", buildTempPreview(snap.goodsName(), snap.goodsSpec(), snap.goodsFurtherDescription()));
        data.put("persistedGoods", null);
        data.put("analyzeMode", ANALYZE_MODE_MANUAL);
        data.put("catalogLevel1Options", fields.get("catalogLevel1Options"));
        data.put("catalogLevel2Options", fields.get("catalogLevel2Options"));
        data.put("catalogLevel3Options", fields.get("catalogLevel3Options"));
        data.put("analyzeModeHint", null);
        boolean skuStep = "MANUAL_CATALOG_SKU_SINGLE".equals(flowState) || "MANUAL_CATALOG_SKU_CHOICE".equals(flowState);
        data.put("nxCatalogConfirmIntents", skuStep ? nxCatalogConfirmIntentsPayload() : null);
        R r = R.ok();
        r.put("flowState", flowState);
        r.put("data", data);
        log.info("{} manual_catalog step=exit sessionId={} flowState={}", L, sessionId, flowState);
        return r;
    }

    /** 合法返回 {@code null}，否则返回 {@link #errorFlow(String)}。 */
    private R validateManualCatalogNode(NxGoodsEntity e, int expectedLevel, String label) {
        if (e == null) {
            return errorFlow(label + "不存在");
        }
        if (!Integer.valueOf(expectedLevel).equals(e.getNxGoodsLevel())) {
            return errorFlow(label + "级别不正确");
        }
        Integer hid = e.getNxGoodsIsHidden();
        if (hid != null && hid != 0) {
            return errorFlow(label + "已隐藏");
        }
        return null;
    }

    private List<NxGoodsEntity> listVisibleChildrenOfParent(int parentNxGoodsId, int childLevel) {
        List<NxGoodsEntity> list = nxGoodsService.list(new LambdaQueryWrapper<NxGoodsEntity>()
                .eq(NxGoodsEntity::getNxGoodsFatherId, parentNxGoodsId)
                .eq(NxGoodsEntity::getNxGoodsLevel, childLevel)
                .and(w -> w.isNull(NxGoodsEntity::getNxGoodsIsHidden).or().eq(NxGoodsEntity::getNxGoodsIsHidden, 0))
                .orderByAsc(NxGoodsEntity::getNxGoodsSort)
                .orderByAsc(NxGoodsEntity::getNxGoodsId));
        return list != null ? list : List.of();
    }

    private static List<Map<String, Object>> toCatalogLevelOptionMaps(List<NxGoodsEntity> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (NxGoodsEntity e : rows) {
            if (e == null || e.getNxGoodsId() == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nxGoodsId", e.getNxGoodsId());
            m.put("nxGoodsName", StrUtil.nullToEmpty(e.getNxGoodsName()));
            m.put("nxGoodsLevel", e.getNxGoodsLevel());
            m.put("nxGoodsSort", e.getNxGoodsSort());
            out.add(m);
        }
        return out;
    }

    /**
     * 用户已确认二级分支：模型生成「三级品名 + 四级 SKU」字段，写入 {@code nx_goods} 并创建批发商商品。
     */
    private R analyzeExpandCatalog(GbAiGoodsAddAnalyzeRequest req, GoodsAddSessionSnapshot snap) {
        Integer grandId = req.getConfirmedGrandNxGoodsId();
        Integer ggId = snap.pendingGreatGrandId();
        List<Integer> allowed = snap.pendingGrandIds() == null ? List.of() : snap.pendingGrandIds();
        if (ggId == null || grandId == null || !allowed.contains(grandId)) {
            log.warn("{} expand step=validate_fail ggId={} grandId={} allowedSize={}", L, ggId, grandId, allowed.size());
            return errorFlow("二级分类无效或未在本会话中提供");
        }
        NxGoodsEntity grand = nxGoodsService.queryObject(grandId);
        if (!isCatalogL1UnderGreatGrand(grand, ggId)) {
            return errorFlow("二级分类校验失败");
        }

        String goodsName = StrUtil.trimToEmpty(req.getGoodsName());
        String goodsSpec = StrUtil.trimToEmpty(req.getGoodsSpec());
        if (StrUtil.isBlank(goodsName) || StrUtil.isBlank(goodsSpec)) {
            return errorFlow("请填写商品名称和规格");
        }
        String goodsFurtherDescription = truncate(StrUtil.trimToEmpty(
                StrUtil.blankToDefault(req.getGoodsFurtherDescription(), snap.goodsFurtherDescription())), MAX_USER_PURPOSE);
        String sid = snap.sessionId();

        NxGoodsEntity ggRow = nxGoodsService.queryObject(ggId);
        String ggName = ggRow != null ? StrUtil.nullToEmpty(ggRow.getNxGoodsName()) : "";
        String grName = StrUtil.nullToEmpty(grand.getNxGoodsName());

        String skill = loadSkillFile("ai-skill-goods-catalog-expand.md");
        String user = buildUserBlock(goodsName, goodsSpec, goodsFurtherDescription)
                + "\n【已确认分类】一级：「" + ggName + "」（id=" + ggId + "） 二级：「" + grName + "」（id=" + grandId + "）\n请只输出一个 JSON 对象，不要 Markdown 围栏。"
                + "\n说明：业务上「三级」对应库 `nx_goods_level=2` 品名父节点，「四级」对应 `nx_goods_level=3` 的 SKU。";
        String system = skill + "\n\n只输出 JSON。";
        List<Map<String, String>> msgs = List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        );
        log.info("{} expand step=llm_request grandId={} systemChars={}", L, grandId, system.length());
        String raw = deepSeekCompletionClient.complete(msgs, "goods-add-expand-catalog", goodsAddTemperature, LLM_SKU_PHASE_MAX_TOKENS);
        log.info("{} expand step=llm_response rawChars={} rawPreview={}", L,
                raw != null ? raw.length() : 0, logPreview(raw, 500));
        ParsedExpand ex = GoodsCatalogExpandLlmParser.parse(raw);
        if (!ex.structuredOk()) {
            log.warn("{} expand step=parse_fail", L);
            return errorFlow("模型未返回有效的扩充目录 JSON，请稍后重试");
        }

        NxGoodsEntity father;
        NxGoodsEntity sku;
        try {
            father = insertLevel2GoodsNameNode(grandId, ggId, ex);
            sku = insertLevel3SkuUnderFather(father, grandId, ggId, ex);
        } catch (Exception e) {
            log.error("{} expand step=nx_goods_save_fail", L, e);
            return errorFlow("写入农鑫目录失败：" + e.getMessage());
        }
        try {
            // 与 GbDistributerGoodsController#createDepartmentOrderFromNxGoodsImport 等一致：新 nx_goods → 批发商部门商品
            GbDistributerGoodsEntity g = gbDistributerGoodsService.createDistributerGoodsFromNxGoods(
                    snap.distributerId(), snap.depId(), sku.getNxGoodsId());
            GbDistributerGoodsEntity gReload = g.getGbDistributerGoodsId() != null
                    ? gbDistributerGoodsService.getById(g.getGbDistributerGoodsId()) : null;
            if (gReload != null) {
                g = gReload;
            }
            GbDepartmentDisGoodsEntity depDis = tryCreateDepDisGoodsForJjLikeImport(snap, g, goodsSpec);
            sessionStore.put(new GoodsAddSessionSnapshot(sid, snap.distributerId(), snap.departmentId(),
                    snap.depId(), snap.depFatherId(), goodsName, goodsSpec, goodsFurtherDescription,
                    List.of(), new LinkedHashMap<>(), null, List.of(), List.of(), null, System.currentTimeMillis()));
            log.info("{} expand step=success nxGoodsId={} fatherNodeId={} gbDistributerGoodsId={} gbDepartmentDisGoodsId={} depId={} disId={}", L,
                    sku.getNxGoodsId(), father.getNxGoodsId(), g.getGbDistributerGoodsId(),
                    depDis != null ? depDis.getGbDepartmentDisGoodsId() : null,
                    g.getGbDgGbDepartmentId(), g.getGbDgDistributerId());
            return buildAnalyzeExpandSuccessResponse(g, sku, father, depDis);
        } catch (Exception e) {
            log.error("{} expand step=distributer_goods_fail rollback_nx", L, e);
            nxGoodsService.removeById(sku.getNxGoodsId());
            nxGoodsService.removeById(father.getNxGoodsId());
            return errorFlow("创建批发商商品失败：" + e.getMessage());
        }
    }

    private R buildAnalyzeExpandSuccessResponse(GbDistributerGoodsEntity g, NxGoodsEntity sku, NxGoodsEntity father,
                                                GbDepartmentDisGoodsEntity depDis) {
        Map<String, Object> gbDg = toGbDistributerGoodsApiMap(g);
        Map<String, Object> persisted = new LinkedHashMap<>(gbDg);
        persisted.put("nxGoodsId", sku.getNxGoodsId());
        persisted.put("nxGoodsFatherNodeId", father.getNxGoodsId());
        if (depDis != null) {
            persisted.put("gbDepartmentDisGoodsId", depDis.getGbDepartmentDisGoodsId());
        }
        String msg = "已新增农鑫目录品名与 SKU，并添加为批发商商品。";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", null);
        data.put("flowState", "SUCCESS");
        data.put("assistantMessage", msg);
        data.put("matchSummary", null);
        data.put("candidates", null);
        data.put("branchOptions", null);
        data.put("tempPreview", null);
        data.put("gbDistributerGoods", gbDg);
        data.put("gbDepartmentDisGoods", depDis != null ? toGbDepartmentDisGoodsApiMap(depDis) : null);
        data.put("persistedGoods", persisted);
        R r = R.ok(msg);
        r.put("flowState", "SUCCESS");
        r.put("data", data);
        return r;
    }

    /** 小程序/前端可用的批发商商品摘要（比 {@code persistSummary} 字段更全，便于列表与下单侧对齐）。 */
    private static Map<String, Object> toGbDistributerGoodsApiMap(GbDistributerGoodsEntity g) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (g == null) {
            return m;
        }
        m.put("gbDistributerGoodsId", g.getGbDistributerGoodsId());
        m.put("gbDgDistributerId", g.getGbDgDistributerId());
        m.put("gbDgGbDepartmentId", g.getGbDgGbDepartmentId());
        m.put("gbDgGoodsName", g.getGbDgGoodsName());
        m.put("gbDgGoodsStandardname", g.getGbDgGoodsStandardname());
        m.put("gbDgGoodsDetail", g.getGbDgGoodsDetail());
        m.put("gbDgGoodsPinyin", g.getGbDgGoodsPinyin());
        m.put("gbDgGoodsPy", g.getGbDgGoodsPy());
        m.put("gbDgNxGoodsId", g.getGbDgNxGoodsId());
        m.put("gbDgNxFatherId", g.getGbDgNxFatherId());
        m.put("gbDgNxGrandId", g.getGbDgNxGrandId());
        m.put("gbDgNxGreatGrandId", g.getGbDgNxGreatGrandId());
        m.put("gbDgNxFatherName", g.getGbDgNxFatherName());
        m.put("gbDgNxGrandName", g.getGbDgNxGrandName());
        m.put("gbDgNxGreatGrandName", g.getGbDgNxGreatGrandName());
        m.put("gbDgGoodsType", g.getGbDgGoodsType());
        m.put("gbDgGoodsStatus", g.getGbDgGoodsStatus());
        m.put("gbDgGoodsStandardWeight", g.getGbDgGoodsStandardWeight());
        m.put("gbDgDfgGoodsFatherId", g.getGbDgDfgGoodsFatherId());
        m.put("gbDgDfgGoodsGrandId", g.getGbDgDfgGoodsGrandId());
        m.put("gbDgDfgGoodsGreatId", g.getGbDgDfgGoodsGreatId());
        m.put("gbDgGoodsInventoryType", g.getGbDgGoodsInventoryType());
        m.put("gbDgPullOff", g.getGbDgPullOff());
        return m;
    }

    /** 前台部门商品列表/订货对齐用：表字段摘要（不含关联对象）。 */
    private static Map<String, Object> toGbDepartmentDisGoodsApiMap(GbDepartmentDisGoodsEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (e == null) {
            return m;
        }
        m.put("gbDepartmentDisGoodsId", e.getGbDepartmentDisGoodsId());
        m.put("gbDdgDepartmentFatherId", e.getGbDdgDepartmentFatherId());
        m.put("gbDdgDepartmentId", e.getGbDdgDepartmentId());
        m.put("gbDdgDisGoodsId", e.getGbDdgDisGoodsId());
        m.put("gbDdgDisGoodsFatherId", e.getGbDdgDisGoodsFatherId());
        m.put("gbDdgDisGoodsGrandId", e.getGbDdgDisGoodsGrandId());
        m.put("gbDdgDisGoodsGreatId", e.getGbDdgDisGoodsGreatId());
        m.put("gbDdgDepGoodsName", e.getGbDdgDepGoodsName());
        m.put("gbDdgDepGoodsPinyin", e.getGbDdgDepGoodsPinyin());
        m.put("gbDdgDepGoodsPy", e.getGbDdgDepGoodsPy());
        m.put("gbDdgDepGoodsStandardname", e.getGbDdgDepGoodsStandardname());
        m.put("gbDdgGoodsType", e.getGbDdgGoodsType());
        m.put("gbDdgGbDepartmentId", e.getGbDdgGbDepartmentId());
        m.put("gbDdgGbDisId", e.getGbDdgGbDisId());
        m.put("gbDdgOrderStandard", e.getGbDdgOrderStandard());
        m.put("gbDdgShowStandardName", e.getGbDdgShowStandardName());
        m.put("gbDdgShowStandardId", e.getGbDdgShowStandardId());
        m.put("gbDdgShowStandardScale", e.getGbDdgShowStandardScale());
        m.put("gbDdgShowStandardWeight", e.getGbDdgShowStandardWeight());
        m.put("gbDdgStockTotalWeight", e.getGbDdgStockTotalWeight());
        m.put("gbDdgStockTotalSubtotal", e.getGbDdgStockTotalSubtotal());
        return m;
    }

    private List<Map<String, Object>> buildBranchOptionsForConfirm(int ggId, List<Integer> grandIds, Map<Integer, String> l0NameById) {
        String ggName = l0NameById.getOrDefault(ggId, "");
        List<Map<String, Object>> out = new ArrayList<>();
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        for (Integer gid : grandIds) {
            if (gid == null || !seen.add(gid)) {
                continue;
            }
            NxGoodsEntity gr = nxGoodsService.queryObject(gid);
            if (!isCatalogL1UnderGreatGrand(gr, ggId)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("greatGrandNxGoodsId", ggId);
            m.put("greatGrandName", ggName);
            m.put("grandNxGoodsId", gid);
            m.put("grandName", StrUtil.nullToEmpty(gr.getNxGoodsName()));
            out.add(m);
        }
        return out;
    }

    private NxGoodsEntity findAnyL2UnderGrand(int grandId) {
        return nxGoodsService.getOne(new LambdaQueryWrapper<NxGoodsEntity>()
                .eq(NxGoodsEntity::getNxGoodsFatherId, grandId)
                .eq(NxGoodsEntity::getNxGoodsLevel, 2)
                .last("LIMIT 1"));
    }

    private int nextNxGoodsSortUnderFather(int fatherId, int level) {
        List<NxGoodsEntity> kids = nxGoodsService.list(new LambdaQueryWrapper<NxGoodsEntity>()
                .eq(NxGoodsEntity::getNxGoodsFatherId, fatherId)
                .eq(NxGoodsEntity::getNxGoodsLevel, level));
        int max = 0;
        if (kids != null) {
            for (NxGoodsEntity e : kids) {
                int s = e.getNxGoodsSort() == null ? 0 : e.getNxGoodsSort();
                if (s > max) {
                    max = s;
                }
            }
        }
        return max + 1;
    }

    /** 同一品名父下四级 SKU（level=3）的 sons_sort，取最大值 +1。 */
    private int nextNxGoodsSonsSortUnderFather(int fatherId) {
        List<NxGoodsEntity> kids = nxGoodsService.list(new LambdaQueryWrapper<NxGoodsEntity>()
                .eq(NxGoodsEntity::getNxGoodsFatherId, fatherId)
                .eq(NxGoodsEntity::getNxGoodsLevel, 3));
        int max = 0;
        if (kids != null) {
            for (NxGoodsEntity e : kids) {
                int s = e.getNxGoodsSonsSort() == null ? 0 : e.getNxGoodsSonsSort();
                if (s > max) {
                    max = s;
                }
            }
        }
        return max + 1;
    }

    private NxGoodsEntity insertLevel2GoodsNameNode(int grandId, int ggId, ParsedExpand ex) {
        NxGoodsEntity tmpl = findAnyL2UnderGrand(grandId);
        Integer nxGrandCol = tmpl != null && tmpl.getNxGoodsGrandId() != null ? tmpl.getNxGoodsGrandId() : grandId;
        Integer nxGgCol = tmpl != null && tmpl.getNxGoodsGreatGrandId() != null ? tmpl.getNxGoodsGreatGrandId() : ggId;

        NxGoodsEntity row = new NxGoodsEntity();
        row.setNxGoodsName(ex.level3Name());
        row.setNxGoodsLevel(2);
        row.setNxGoodsFatherId(grandId);
        row.setNxGoodsGrandId(nxGrandCol);
        row.setNxGoodsGreatGrandId(nxGgCol);
        row.setNxGoodsSort(nextNxGoodsSortUnderFather(grandId, 2));
        row.setNxGoodsIsHidden(0);
        row.setNxGoodsIsOldestSon(0);
        row.setNxGoodsPinyin(hanziToPinyin(ex.level3Name()));
        row.setNxGoodsPy(getHeadStringByString(ex.level3Name(), false, null));
        if (!nxGoodsService.save(row)) {
            throw new IllegalStateException("品名父节点（nx_goods level=2）保存失败");
        }
        return row;
    }

    /**
     * 在「已匹配到的目录 SKU」所属的品名父（{@code nx_goods_level=2}）下新增一条 SKU（level=3），
     * 用于用户输入与目录名不完全一致时仍挂在同一三级品名下（如 素鸡卷/斤 与 素鸡/根 并存）。
     *
     * @param gbDistributerId 写入 {@code nx_from_gb_distributer_id}；{@code nx_goods_status} 固定为 {@link #NX_GOODS_STATUS_AI_EXPAND}
     */
    private NxGoodsEntity insertSiblingSkuUnderMatchedFather(NxGoodsEntity matchedSkuInput, String displayName, String standardName,
                                                             int gbDistributerId) {
        NxGoodsEntity matchedSku = matchedSkuInput.getNxGoodsId() != null
                ? nxGoodsService.getById(matchedSkuInput.getNxGoodsId()) : null;
        if (matchedSku == null) {
            matchedSku = matchedSkuInput;
        }
        Integer fatherId = matchedSku.getNxGoodsFatherId();
        if (fatherId == null) {
            throw new IllegalArgumentException("匹配商品缺少品名父节点 id");
        }
        NxGoodsEntity father = nxGoodsService.getById(fatherId);
        if (father == null) {
            father = nxGoodsService.queryObject(fatherId);
        }
        if (father == null || !Integer.valueOf(2).equals(father.getNxGoodsLevel())) {
            throw new IllegalArgumentException("匹配商品的父节点不是目录品名分类（nx_goods_level=2）");
        }
        long dup = nxGoodsService.count(new LambdaQueryWrapper<NxGoodsEntity>()
                .eq(NxGoodsEntity::getNxGoodsFatherId, fatherId)
                .eq(NxGoodsEntity::getNxGoodsLevel, 3)
                .eq(NxGoodsEntity::getNxGoodsName, displayName)
                .eq(NxGoodsEntity::getNxGoodsStandardname, standardName)
                .and(w -> w.isNull(NxGoodsEntity::getNxGoodsIsHidden).or().eq(NxGoodsEntity::getNxGoodsIsHidden, 0)));
        if (dup > 0) {
            throw new IllegalStateException("该品名下已存在相同名称与规格的目录 SKU；若即目录商品本身，请选择「使用目录匹配到的商品」");
        }
        Integer grandId = firstNonNull(father.getNxGoodsGrandId(), matchedSku.getNxGoodsGrandId());
        Integer ggId = firstNonNull(father.getNxGoodsGreatGrandId(), matchedSku.getNxGoodsGreatGrandId());
        if (grandId == null || ggId == null) {
            List<NxGoodsEntity> peer = nxGoodsService.list(new LambdaQueryWrapper<NxGoodsEntity>()
                    .eq(NxGoodsEntity::getNxGoodsFatherId, fatherId)
                    .eq(NxGoodsEntity::getNxGoodsLevel, 3)
                    .last("LIMIT 5"));
            if (peer != null) {
                for (NxGoodsEntity p : peer) {
                    if (p == null) {
                        continue;
                    }
                    if (grandId == null) {
                        grandId = p.getNxGoodsGrandId();
                    }
                    if (ggId == null) {
                        ggId = p.getNxGoodsGreatGrandId();
                    }
                    if (grandId != null && ggId != null) {
                        break;
                    }
                }
            }
        }
        if (grandId == null || ggId == null) {
            throw new IllegalStateException("无法解析目录大类（nx_goods_grand_id / nx_goods_great_grand_id），请检查品名父节点数据");
        }
        NxGoodsEntity row = new NxGoodsEntity();
        row.setNxGoodsName(displayName);
        row.setNxGoodsStandardname(standardName);
        row.setNxGoodsLevel(3);
        row.setNxGoodsFatherId(fatherId);
        row.setNxGoodsGrandId(grandId);
        row.setNxGoodsGreatGrandId(ggId);
        row.setNxGoodsSort(nextNxGoodsSortUnderFather(fatherId, 3));
        row.setNxGoodsSonsSort(nextNxGoodsSonsSortUnderFather(fatherId));
        row.setNxGoodsIsHidden(0);
        /* 仅新增四级 SKU（同三级下已有规格）：根规格唯一条目为旧数据，本条不可再标「最老子」 —— 必须为 0。 */
        row.setNxGoodsIsOldestSon(0);
        row.setNxGoodsPinyin(hanziToPinyin(displayName));
        row.setNxGoodsPy(getHeadStringByString(displayName, false, null));
        row.setNxFromGbDistributerId(gbDistributerId);
        row.setNxGoodsStatus(NX_GOODS_STATUS_AI_EXPAND);
        if (!nxGoodsService.save(row)) {
            throw new IllegalStateException("新目录 SKU 保存失败");
        }
        return row;
    }

    private static Integer firstNonNull(Integer a, Integer b) {
        return a != null ? a : b;
    }

    private NxGoodsEntity insertLevel3SkuUnderFather(NxGoodsEntity father, int grandId, int ggId, ParsedExpand ex) {
        NxGoodsEntity row = new NxGoodsEntity();
        row.setNxGoodsName(ex.level4DisplayName());
        row.setNxGoodsStandardname(ex.level4StandardName());
        row.setNxGoodsLevel(3);
        row.setNxGoodsFatherId(father.getNxGoodsId());
        row.setNxGoodsGrandId(grandId);
        row.setNxGoodsGreatGrandId(ggId);
        row.setNxGoodsSort(nextNxGoodsSortUnderFather(father.getNxGoodsId(), 3));
        row.setNxGoodsIsHidden(0);
        /* 扩充目录场景：与同请求内新建的「三级」品名父一起插入的首条「四级」SKU —— 该三级下仅此一条时为根规格。 */
        row.setNxGoodsIsOldestSon(1);
        row.setNxGoodsPinyin(hanziToPinyin(ex.level4DisplayName()));
        row.setNxGoodsPy(getHeadStringByString(ex.level4DisplayName(), false, null));
        if (StrUtil.isNotBlank(ex.level4Detail())) {
            row.setNxGoodsDetail(ex.level4Detail());
        }
        if (!nxGoodsService.save(row)) {
            nxGoodsService.removeById(father.getNxGoodsId());
            throw new IllegalStateException("SKU（nx_goods level=3）保存失败");
        }
        return row;
    }

    @Override
    public R confirm(GbAiGoodsAddConfirmRequest req) {
        log.info("{} confirm step=begin", L);
        if (req == null || StrUtil.isBlank(req.getSessionId())) {
            log.warn("{} confirm step=validate_fail reason=missing_sessionId", L);
            return errorFlow("缺少 sessionId");
        }
        GoodsAddSessionSnapshot snap = sessionStore.get(req.getSessionId().trim());
        if (snap == null) {
            log.warn("{} confirm step=session_miss sessionId={}", L, req.getSessionId());
            return errorFlow("会话已过期，请重新分析");
        }
        log.info("{} confirm step=session_hit sessionId={} disId={} depId={} whitelistSize={} snapGoods={}",
                L, snap.sessionId(), snap.distributerId(), snap.depId(), snap.allowedNxGoodsIdsOrdered().size(),
                logPreview(snap.goodsName(), 30));
        String confirmType = StrUtil.trimToEmpty(req.getConfirmType()).toUpperCase();
        if (!"NX_CATALOG".equals(confirmType) && !"TEMP".equals(confirmType)) {
            log.warn("{} confirm step=validate_fail confirmType={}", L, req.getConfirmType());
            return errorFlow("confirmType 必须为 NX_CATALOG 或 TEMP");
        }

        try {
            if ("NX_CATALOG".equals(confirmType)) {
                if (req.getNxGoodsId() == null || !snap.allowedNxGoodsIdsOrdered().contains(req.getNxGoodsId())) {
                    log.warn("{} confirm step=reject_nx nxGoodsId={} whitelist={}", L, req.getNxGoodsId(), snap.allowedNxGoodsIdsOrdered());
                    return errorFlow("无效的 nxGoodsId 或不在本轮候选白名单内");
                }
                String nxIntent = StrUtil.blankToDefault(StrUtil.trimToEmpty(req.getNxCatalogIntent()), NX_INTENT_USE_MATCHED)
                        .toUpperCase(Locale.ROOT);
                if (!NX_INTENT_USE_MATCHED.equals(nxIntent) && !NX_INTENT_ADD_SIBLING_SKU.equals(nxIntent)) {
                    log.warn("{} confirm step=validate_fail nxCatalogIntent={}", L, req.getNxCatalogIntent());
                    return errorFlow("nxCatalogIntent 无效，请使用 USE_MATCHED 或 ADD_SIBLING_SKU");
                }
                if (NX_INTENT_ADD_SIBLING_SKU.equals(nxIntent)) {
                    NxGoodsEntity matchedSku = nxGoodsService.queryObject(req.getNxGoodsId());
                    if (!isCatalogSku(matchedSku)) {
                        return errorFlow("当前匹配项不是可扩展的目录 SKU");
                    }
                    String newSkuName = StrUtil.trimToEmpty(StrUtil.blankToDefault(req.getGoodsName(), snap.goodsName()));
                    String newSkuSpec = StrUtil.trimToEmpty(StrUtil.blankToDefault(req.getGoodsSpec(), snap.goodsSpec()));
                    if (StrUtil.isBlank(newSkuName) || StrUtil.isBlank(newSkuSpec)) {
                        return errorFlow("新增同品类 SKU 时请提供商品名称与规格（可与会话中一致）");
                    }
                    log.info("{} confirm step=persist nx_catalog_sibling refNxGoodsId={} newName={} newSpec={}", L,
                            req.getNxGoodsId(), logPreview(newSkuName, 40), newSkuSpec);
                    NxGoodsEntity newSku;
                    try {
                        newSku = insertSiblingSkuUnderMatchedFather(matchedSku, newSkuName, newSkuSpec, snap.distributerId());
                    } catch (IllegalStateException | IllegalArgumentException ex) {
                        log.warn("{} confirm step=sibling_nx_fail msg={}", L, ex.getMessage());
                        return errorFlow(ex.getMessage());
                    }
                    try {
                        GbDistributerGoodsEntity g = gbDistributerGoodsService.createDistributerGoodsFromNxGoods(
                                snap.distributerId(), snap.depId(), newSku.getNxGoodsId());
                        GbDepartmentDisGoodsEntity depDis = tryCreateDepDisGoodsForJjLikeImport(snap, g, newSkuSpec);
                        Map<String, Object> persisted = persistSummary(g,
                                depDis != null ? depDis.getGbDepartmentDisGoodsId() : null);
                        persisted.put("addedSiblingNxSku", Boolean.TRUE);
                        persisted.put("nxGoodsFatherId", newSku.getNxGoodsFatherId());
                        persisted.put("refNxGoodsId", req.getNxGoodsId());
                        log.info("{} confirm step=exit SUCCESS sibling nxGoodsId={} gbDistributerGoodsId={}", L,
                                newSku.getNxGoodsId(), persisted.get("gbDistributerGoodsId"));
                        return successPayload("已在同品类下新增目录 SKU 并添加。", persisted, depDis, g);
                    } catch (Exception e) {
                        log.error("{} confirm step=sibling_dis_goods_fail rollback_nx nxGoodsId={}", L,
                                newSku.getNxGoodsId(), e);
                        nxGoodsService.removeById(newSku.getNxGoodsId());
                        return errorFlow("创建批发商或部门商品失败：" + e.getMessage());
                    }
                }
                log.info("{} confirm step=persist nx_catalog_use_matched nxGoodsId={} disId={} depId={}", L,
                        req.getNxGoodsId(), snap.distributerId(), snap.depId());
                GbDistributerGoodsEntity existingForNx = findExistingDistributerGoodsByNx(
                        snap.distributerId(), req.getNxGoodsId());
                if (existingForNx != null) {
                    GbDepartmentDisGoodsEntity depDisExist = tryCreateDepDisGoodsForJjLikeImport(snap, existingForNx, snap.goodsSpec());
                    Map<String, Object> persisted = persistSummary(existingForNx,
                            depDisExist != null ? depDisExist.getGbDepartmentDisGoodsId() : null);
                    log.info("{} confirm step=reuse_existing_dis_goods gbDistributerGoodsId={} nxGoodsId={}", L,
                            existingForNx.getGbDistributerGoodsId(), req.getNxGoodsId());
                    return successPayload("该目录 SKU 已在您的批发商商品中。", persisted, depDisExist, existingForNx);
                }
                GbDistributerGoodsEntity g = gbDistributerGoodsService.createDistributerGoodsFromNxGoods(
                        snap.distributerId(), snap.depId(), req.getNxGoodsId());
                GbDepartmentDisGoodsEntity depDis = tryCreateDepDisGoodsForJjLikeImport(snap, g, snap.goodsSpec());
                Map<String, Object> persisted = persistSummary(g,
                        depDis != null ? depDis.getGbDepartmentDisGoodsId() : null);
                log.info("{} confirm step=exit SUCCESS gbDistributerGoodsId={} gbDgNxGoodsId={}", L,
                        persisted.get("gbDistributerGoodsId"), persisted.get("gbDgNxGoodsId"));
                return successPayload("已添加目录商品。", persisted, depDis, g);
            }

            String gName = StrUtil.blankToDefault(StrUtil.trimToEmpty(req.getGoodsName()), snap.goodsName());
            String gSpec = StrUtil.blankToDefault(StrUtil.trimToEmpty(req.getGoodsSpec()), snap.goodsSpec());
            String detail = resolveTempConfirmDetail(req, snap);
            log.info("{} confirm step=persist temp_goods", L);
            return persistTempGoodsFromSnap(snap, gName, gSpec, detail);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            log.warn("{} confirm step=fail_biz msg={}", L, ex.getMessage());
            return errorFlow(ex.getMessage());
        } catch (Exception ex) {
            log.error("{} confirm step=fail_unknown", L, ex);
            return errorFlow("落库失败，请稍后重试");
        }
    }

    private static Map<String, Object> persistSummary(GbDistributerGoodsEntity g) {
        return persistSummary(g, null);
    }

    private static Map<String, Object> persistSummary(GbDistributerGoodsEntity g, Integer gbDepartmentDisGoodsId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gbDistributerGoodsId", g.getGbDistributerGoodsId());
        m.put("gbDgGoodsName", g.getGbDgGoodsName());
        m.put("gbDgGoodsStandardname", g.getGbDgGoodsStandardname());
        m.put("gbDgNxGoodsId", g.getGbDgNxGoodsId());
        if (gbDepartmentDisGoodsId != null) {
            m.put("gbDepartmentDisGoodsId", gbDepartmentDisGoodsId);
        }
        return m;
    }

    /**
     * 与 {@code GbDistributerGoodsController#createDepartmentOrderFromNxGoodsImport} 中在
     * {@code createDistributerGoodsFromNxGoods} 之后一步一致：写入 {@code gb_department_dis_goods}，便于部门侧列表/订货看到商品。
     */
    private GbDepartmentDisGoodsEntity tryCreateDepDisGoodsForJjLikeImport(GoodsAddSessionSnapshot snap, GbDistributerGoodsEntity gbDisGoods,
                                                                         String orderStandard) {
        try {
            GbDepartmentOrdersEntity stub = new GbDepartmentOrdersEntity();
            stub.setGbDoDepartmentId(snap.depId());
            Integer depFather = snap.depFatherId() != null ? snap.depFatherId() : snap.depId();
            stub.setGbDoDepartmentFatherId(depFather);
            stub.setGbDoStandard(StrUtil.blankToDefault(orderStandard, gbDisGoods.getGbDgGoodsStandardname()));
            GbDepartmentDisGoodsEntity depDis = gbDepartmentDisGoodsService.createDepDisGoodsForJjOrder(stub, gbDisGoods);
            log.info("{} dep_dis_goods step=ok gbDepartmentDisGoodsId={} gbDistributerGoodsId={}", L,
                    depDis.getGbDepartmentDisGoodsId(), gbDisGoods.getGbDistributerGoodsId());
            return depDis;
        } catch (Exception e) {
            log.warn("{} dep_dis_goods step=fail gbDistributerGoodsId={} msg={}", L,
                    gbDisGoods.getGbDistributerGoodsId(), e.getMessage(), e);
            return null;
        }
    }

    private R successPayload(String msg, Map<String, Object> persistedGoods) {
        return successPayload(msg, persistedGoods, null, null);
    }

    private R successPayload(String msg, Map<String, Object> persistedGoods, GbDepartmentDisGoodsEntity depDis) {
        return successPayload(msg, persistedGoods, depDis, null);
    }

    private R successPayload(String msg, Map<String, Object> persistedGoods, GbDepartmentDisGoodsEntity depDis,
                             GbDistributerGoodsEntity gbDistributerGoodsForData) {
        log.info("{} confirm step=response_builder flowState=SUCCESS persisted={} hasDepDis={} hasGbDgMap={}", L,
                persistedGoods, depDis != null, gbDistributerGoodsForData != null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", null);
        data.put("flowState", "SUCCESS");
        data.put("assistantMessage", msg);
        data.put("matchSummary", null);
        data.put("candidates", null);
        data.put("tempPreview", null);
        data.put("gbDistributerGoods", gbDistributerGoodsForData != null ? toGbDistributerGoodsApiMap(gbDistributerGoodsForData) : null);
        data.put("gbDepartmentDisGoods", depDis != null ? toGbDepartmentDisGoodsApiMap(depDis) : null);
        data.put("persistedGoods", persistedGoods);
        R r = R.ok(msg);
        r.put("flowState", "SUCCESS");
        r.put("data", data);
        return r;
    }

    private Map<String, Object> dataNoMatch(String sessionId, String goodsName, String goodsSpec,
                                            String goodsFurtherDescription, String assistant) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("flowState", "NO_MATCH");
        data.put("assistantMessage", StrUtil.blankToDefault(assistant, "未找到合适的目录商品，可补充说明后再试或添加为临时商品。"));
        data.put("matchSummary", null);
        data.put("candidates", null);
        data.put("tempPreview", buildTempPreview(goodsName, goodsSpec, goodsFurtherDescription));
        data.put("persistedGoods", null);
        return data;
    }

    private String resolveTempConfirmDetail(GbAiGoodsAddConfirmRequest req, GoodsAddSessionSnapshot snap) {
        String fromReq = truncate(StrUtil.trimToEmpty(req.getGoodsFurtherDescription()), MAX_USER_PURPOSE);
        if (StrUtil.isNotBlank(fromReq)) {
            return fromReq;
        }
        return StrUtil.blankToDefault(snap.goodsFurtherDescription(), "");
    }

    private static Map<String, Object> buildTempPreview(String goodsName, String goodsSpec, String goodsFurtherDescription) {
        Map<String, Object> tp = new LinkedHashMap<>(4);
        tp.put("goodsName", goodsName);
        tp.put("goodsSpec", goodsSpec);
        String note = StrUtil.trimToEmpty(goodsFurtherDescription);
        tp.put("goodsFurtherDescription", StrUtil.isBlank(note) ? null : note);
        return tp;
    }

    /** 与 {@link #ANALYZE_MODE_DIRECT_TEMP} 等效：仅落库临时品，不调用 DeepSeek。 */
    private static boolean isAnalyzeModeDirectTemp(String analyzeModeUpper) {
        return ANALYZE_MODE_DIRECT_TEMP.equals(analyzeModeUpper)
                || "ADD_TEMP".equals(analyzeModeUpper)
                || "TEMP_ONLY".equals(analyzeModeUpper)
                || "TEMP_GOODS".equals(analyzeModeUpper);
    }

    private R errorFlow(String msg) {
        log.warn("{} errorFlow msg={}", L, msg);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", null);
        data.put("flowState", "ERROR");
        data.put("assistantMessage", msg);
        data.put("matchSummary", null);
        data.put("candidates", null);
        data.put("tempPreview", null);
        data.put("persistedGoods", null);
        // 与 docs 约定一致：业务失败仍可用 HTTP 200，由 flowState + code 区分
        return R.ok().put("code", 400).put("msg", msg).put("flowState", "ERROR").put("data", data);
    }

    /** 库内一级 `nx_goods_level=0` 或二级 `=1`（未隐藏），按 sort、id 排序。 */
    private List<NxGoodsEntity> listVisibleCatalogByLevel(int level) {
        LambdaQueryWrapper<NxGoodsEntity> w = new LambdaQueryWrapper<>();
        w.eq(NxGoodsEntity::getNxGoodsLevel, level)
                .and(x -> x.isNull(NxGoodsEntity::getNxGoodsIsHidden).or().eq(NxGoodsEntity::getNxGoodsIsHidden, 0))
                .orderByAsc(NxGoodsEntity::getNxGoodsSort)
                .orderByAsc(NxGoodsEntity::getNxGoodsId);
        List<NxGoodsEntity> list = nxGoodsService.list(w);
        return list != null ? list : List.of();
    }

    private static String buildL0L1MarkdownTable(List<NxGoodsEntity> l0Rows, List<NxGoodsEntity> l1Rows,
                                                   Map<Integer, String> l0NameById) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 一级分类表（nx_goods_level=0）\n");
        sb.append("| greatGrandNxGoodsId | 名称 |\n|---|---|\n");
        for (NxGoodsEntity e : l0Rows) {
            if (e.getNxGoodsId() == null) {
                continue;
            }
            sb.append("| ").append(e.getNxGoodsId()).append(" | ").append(escapePipe(e.getNxGoodsName())).append(" |\n");
        }
        sb.append("\n## 二级分类表（nx_goods_level=1，parentGreatGrandNxGoodsId 为一级 id）\n");
        sb.append("| grandNxGoodsId | 名称 | parentGreatGrandNxGoodsId | 一级名称 |\n|---|---|---|---|\n");
        for (NxGoodsEntity e : l1Rows) {
            if (e.getNxGoodsId() == null || e.getNxGoodsFatherId() == null) {
                continue;
            }
            String pName = l0NameById.getOrDefault(e.getNxGoodsFatherId(), "");
            sb.append("| ").append(e.getNxGoodsId()).append(" | ")
                    .append(escapePipe(e.getNxGoodsName())).append(" | ")
                    .append(e.getNxGoodsFatherId()).append(" | ")
                    .append(escapePipe(pName)).append(" |\n");
        }
        return sb.toString();
    }

    private List<Integer> resolveValidatedGrandIds(ParsedBranch branch) {
        List<Integer> out = new ArrayList<>();
        if (branch.decision() == BranchDecision.SINGLE) {
            NxGoodsEntity gg = nxGoodsService.queryObject(branch.greatGrandNxGoodsId());
            NxGoodsEntity gr = nxGoodsService.queryObject(branch.grandNxGoodsId());
            if (isCatalogL0(gg) && isCatalogL1UnderGreatGrand(gr, branch.greatGrandNxGoodsId())) {
                out.add(branch.grandNxGoodsId());
            }
            return out;
        }
        if (branch.decision() == BranchDecision.AMBIGUOUS) {
            NxGoodsEntity gg = nxGoodsService.queryObject(branch.greatGrandNxGoodsId());
            if (!isCatalogL0(gg)) {
                return List.of();
            }
            for (Integer gid : branch.ambiguousGrandNxGoodsIds()) {
                if (gid == null || out.size() >= MAX_BRANCH_AMBIGUOUS) {
                    continue;
                }
                NxGoodsEntity gr = nxGoodsService.queryObject(gid);
                if (isCatalogL1UnderGreatGrand(gr, branch.greatGrandNxGoodsId())) {
                    out.add(gid);
                }
            }
        }
        return out;
    }

    private static boolean isCatalogL0(NxGoodsEntity e) {
        return e != null && e.getNxGoodsId() != null
                && Integer.valueOf(0).equals(e.getNxGoodsLevel())
                && visibleNotHidden(e);
    }

    private static boolean isCatalogL1UnderGreatGrand(NxGoodsEntity e, int greatGrandId) {
        return e != null && e.getNxGoodsId() != null
                && Integer.valueOf(1).equals(e.getNxGoodsLevel())
                && e.getNxGoodsFatherId() != null && e.getNxGoodsFatherId() == greatGrandId
                && visibleNotHidden(e);
    }

    private static boolean visibleNotHidden(NxGoodsEntity e) {
        Integer h = e.getNxGoodsIsHidden();
        return h == null || h == 0;
    }

    private List<NxGoodsEntity> loadMergedSkuCandidates(int ggId, List<Integer> grandIds, String goodsName,
                                                        String goodsFurtherDescription) {
        LinkedHashMap<Integer, NxGoodsEntity> acc = new LinkedHashMap<>();
        String hint = (StrUtil.trimToEmpty(goodsName) + " " + StrUtil.trimToEmpty(goodsFurtherDescription)).trim();
        for (Integer grand : grandIds) {
            if (grand == null) {
                continue;
            }
            List<NxGoodsEntity> chunk = listSkusForGreatGrandAndGrand(ggId, grand);
            chunk = trimSkuListByHint(chunk, hint, MAX_SKU_PER_GRAND);
            for (NxGoodsEntity e : chunk) {
                if (e.getNxGoodsId() == null) {
                    continue;
                }
                acc.putIfAbsent(e.getNxGoodsId(), e);
                if (acc.size() >= MAX_SKU_TOTAL_FOR_TABLE) {
                    return new ArrayList<>(acc.values());
                }
            }
        }
        return new ArrayList<>(acc.values());
    }

    private List<NxGoodsEntity> listSkusForGreatGrandAndGrand(int ggId, int grandId) {
        LambdaQueryWrapper<NxGoodsEntity> w = new LambdaQueryWrapper<>();
        w.eq(NxGoodsEntity::getNxGoodsGreatGrandId, ggId)
                .eq(NxGoodsEntity::getNxGoodsGrandId, grandId)
                .eq(NxGoodsEntity::getNxGoodsLevel, 3)
                .and(x -> x.isNull(NxGoodsEntity::getNxGoodsIsHidden).or().eq(NxGoodsEntity::getNxGoodsIsHidden, 0))
                .orderByAsc(NxGoodsEntity::getNxGoodsSort)
                .orderByAsc(NxGoodsEntity::getNxGoodsId);
        List<NxGoodsEntity> list = nxGoodsService.list(w);
        return list != null ? list : List.of();
    }

    private static List<NxGoodsEntity> trimSkuListByHint(List<NxGoodsEntity> full, String hint, int cap) {
        if (full == null || full.isEmpty()) {
            return List.of();
        }
        if (full.size() <= cap) {
            return new ArrayList<>(full);
        }
        String h = StrUtil.trimToEmpty(hint);
        if (h.length() >= 2) {
            List<NxGoodsEntity> hit = full.stream()
                    .filter(e -> rowMatchesHint(e, h))
                    .limit(cap)
                    .collect(Collectors.toList());
            if (!hit.isEmpty()) {
                return hit;
            }
        }
        return full.stream().limit(cap).collect(Collectors.toList());
    }

    private static boolean rowMatchesHint(NxGoodsEntity e, String hint) {
        String n = StrUtil.nullToEmpty(e.getNxGoodsName());
        String s = StrUtil.nullToEmpty(e.getNxGoodsStandardname());
        if (StrUtil.contains(n, hint) || StrUtil.contains(s, hint)) {
            return true;
        }
        for (String t : hint.split("[\\s,，、]+")) {
            if (t.length() >= 2 && (StrUtil.contains(n, t) || StrUtil.contains(s, t))) {
                return true;
            }
        }
        return false;
    }

    private static String buildSkuMarkdownTable(List<NxGoodsEntity> skus, Map<Integer, String> idToName) {
        StringBuilder sb = new StringBuilder();
        sb.append("| nxGoodsId | 名称 | 目录规格 | 分类路径 |\n|---|---|---|---|\n");
        for (NxGoodsEntity e : skus) {
            sb.append("| ").append(e.getNxGoodsId()).append(" | ")
                    .append(escapePipe(e.getNxGoodsName())).append(" | ")
                    .append(escapePipe(StrUtil.nullToEmpty(e.getNxGoodsStandardname()))).append(" | ")
                    .append(escapePipe(buildPath(e, idToName))).append(" |\n");
        }
        return sb.toString();
    }

    private static String escapePipe(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("|", "\\|").replace("\n", " ");
    }

    /**
     * 模型在候选表上返回 NONE 时：仅当名称与候选 SKU 的**文本重叠分**足够高时才自动 MATCH_SINGLE / MATCH_CHOICE；
     * 否则返回 {@code null}，由上层将 {@code NO_MATCH} 升为 {@code BRANCH_CONFIRM}，避免把「果丹皮」与话梅等无关 SKU 凑成多选。
     */
    private record SkuFallbackUi(
            String flowState,
            String assistant,
            Map<String, Object> matchSummary,
            List<Map<String, Object>> candidatesOut,
            List<Integer> sessionAllowedIds,
            Map<Integer, Map<String, Object>> sessionCandidateMaps
    ) {}

    private record ScoredSku(NxGoodsEntity entity, int score) {}

    private SkuFallbackUi buildFallbackSkuWhenLlmNone(
            List<NxGoodsEntity> skuCandidates,
            Map<Integer, Map<String, Object>> candidateMaps,
            Map<Integer, String> idToNameSku,
            String goodsName,
            String goodsFurtherDescription,
            String llmSummary) {
        if (skuCandidates == null || skuCandidates.isEmpty()) {
            return null;
        }
        List<ScoredSku> scored = new ArrayList<>();
        for (NxGoodsEntity e : skuCandidates) {
            scored.add(new ScoredSku(e, skuTextOverlapScore(e, goodsName, goodsFurtherDescription)));
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        int best = scored.get(0).score;
        log.info("{} sku_text_fallback bestScore={} minRequired={} topNxGoodsId={} topName={}", L, best, MIN_SKU_TEXT_FALLBACK_SCORE,
                scored.get(0).entity.getNxGoodsId(), logPreview(scored.get(0).entity.getNxGoodsName(), 40));

        if (best < MIN_SKU_TEXT_FALLBACK_SCORE) {
            log.info("{} sku_text_fallback skip reason=score_below_min (prefer_branch_confirm)", L);
            return null;
        }

        int threshold = Math.max(1, (best * 2) / 3);
        LinkedHashMap<Integer, NxGoodsEntity> aboveMap = new LinkedHashMap<>();
        for (ScoredSku s : scored) {
            if (s.score < threshold) {
                continue;
            }
            if (s.entity.getNxGoodsId() != null) {
                aboveMap.putIfAbsent(s.entity.getNxGoodsId(), s.entity);
            }
            if (aboveMap.size() >= MAX_CHOICE_RETURN) {
                break;
            }
        }
        List<NxGoodsEntity> above = new ArrayList<>(aboveMap.values());
        if (above.isEmpty()) {
            log.info("{} sku_text_fallback skip reason=no_row_above_threshold best={}", L, best);
            return null;
        }

        if (above.size() == 1) {
            NxGoodsEntity e = above.get(0);
            Map<String, Object> ms = candidateMaps.get(e.getNxGoodsId());
            Map<Integer, Map<String, Object>> sm = new LinkedHashMap<>();
            sm.put(e.getNxGoodsId(), ms);
            String show = StrUtil.nullToEmpty(e.getNxGoodsName());
            String msg = best >= 120
                    ? "按名称与目录「" + show + "」最接近，请确认后添加。"
                    : "与所填名称不完全一致，已推荐相对最接近的目录项「" + show + "」，请确认或改用临时商品。";
            return new SkuFallbackUi("MATCH_SINGLE", msg, ms, null, List.of(e.getNxGoodsId()), sm);
        }

        return buildSkuFallbackMatchChoice(above, candidateMaps, idToNameSku,
                StrUtil.blankToDefault(llmSummary,
                        "模型未锁定一项；已按名称相近度列出多条，请从中选择或添加临时商品。"));
    }

    private SkuFallbackUi buildSkuFallbackMatchChoice(
            List<NxGoodsEntity> entities,
            Map<Integer, Map<String, Object>> candidateMaps,
            Map<Integer, String> idToNameSku,
            String userMsg) {
        Map<Integer, Map<String, Object>> sm = new LinkedHashMap<>();
        List<Integer> allow = new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (NxGoodsEntity e : entities) {
            Integer id = e.getNxGoodsId();
            if (id == null) {
                continue;
            }
            Map<String, Object> row = candidateMaps.get(id);
            if (row == null) {
                row = toCandidateMap(e, idToNameSku);
            }
            sm.put(id, row);
            allow.add(id);
            out.add(row);
        }
        return new SkuFallbackUi("MATCH_CHOICE", userMsg, null, out, allow, sm);
    }

    private static int skuTextOverlapScore(NxGoodsEntity e, String goodsName, String goodsFurtherDescription) {
        String n = collapseText(StrUtil.nullToEmpty(e.getNxGoodsName()));
        String std = collapseText(StrUtil.nullToEmpty(e.getNxGoodsStandardname()));
        String hay = n + " " + std;
        String g = collapseText(StrUtil.trimToEmpty(goodsName));
        String u = collapseText(StrUtil.trimToEmpty(goodsFurtherDescription));
        int score = 0;
        if (g.length() >= 2 && hay.contains(g)) {
            score += 220;
        }
        if (g.length() == 1 && hay.contains(g)) {
            score += 80;
        }
        int glen = g.length();
        for (int len = Math.min(6, glen); len >= 2; len--) {
            for (int i = 0; i + len <= glen; i++) {
                String sub = g.substring(i, i + len);
                if (hay.contains(sub)) {
                    score += 18 + len * 4;
                }
            }
        }
        for (int i = 0; i < glen; i++) {
            String ch = g.substring(i, i + 1);
            if (!ch.isBlank() && hay.contains(ch)) {
                score += 10;
            }
        }
        if (u.length() >= 2) {
            if (hay.contains(u)) {
                score += 80;
            }
            for (String t : u.split("[\\s,，、]+")) {
                String ct = collapseText(t);
                if (ct.length() >= 2 && hay.contains(ct)) {
                    score += 35;
                }
            }
        }
        return score;
    }

    private static String collapseText(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String logPreview(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private Map<Integer, String> loadAncestorNames(Iterable<NxGoodsEntity> entities) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (NxGoodsEntity e : entities) {
            if (e.getNxGoodsFatherId() != null) {
                ids.add(e.getNxGoodsFatherId());
            }
            if (e.getNxGoodsGrandId() != null) {
                ids.add(e.getNxGoodsGrandId());
            }
            if (e.getNxGoodsGreatGrandId() != null) {
                ids.add(e.getNxGoodsGreatGrandId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<NxGoodsEntity> rows = nxGoodsService.listByIds(ids);
        Map<Integer, String> map = new HashMap<>();
        for (NxGoodsEntity r : rows) {
            if (r.getNxGoodsId() != null) {
                map.put(r.getNxGoodsId(), StrUtil.nullToEmpty(r.getNxGoodsName()));
            }
        }
        return map;
    }

    private static String buildPath(NxGoodsEntity e, Map<Integer, String> idToName) {
        String gg = idToName.getOrDefault(e.getNxGoodsGreatGrandId(), "");
        String gr = idToName.getOrDefault(e.getNxGoodsGrandId(), "");
        String f = idToName.getOrDefault(e.getNxGoodsFatherId(), "");
        return String.join(" > ", List.of(gg, gr, f).stream().filter(StrUtil::isNotBlank).collect(Collectors.toList()));
    }

    /** analyze 在 MATCH_SINGLE / MATCH_CHOICE 时返回，供前端展示两个确认按钮及对应 {@code nxCatalogIntent}。 */
    private static List<Map<String, Object>> nxCatalogConfirmIntentsPayload() {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("intent", NX_INTENT_USE_MATCHED);
        a.put("label", "选择该商品"); //使用目录匹配到的商品
        out.add(a);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("intent", NX_INTENT_ADD_SIBLING_SKU);
        b.put("label", "在同品类下新增"); //保留我输入的名称和规格，在同品类下新增一条目录 SKU
        out.add(b);
        return out;
    }

    private static Map<String, Object> toCandidateMap(NxGoodsEntity e, Map<Integer, String> idToName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nxGoodsId", e.getNxGoodsId());
        m.put("displayName", e.getNxGoodsName());
        m.put("standardName", StrUtil.nullToEmpty(e.getNxGoodsStandardname()));
        m.put("nxGoodsFile", e.getNxGoodsFile());
        m.put("nxGoodsFileBig", e.getNxGoodsFileBig());
        m.put("nxGoodsStandardAmount", e.getNxGoodsStandardAmount());
        m.put("nxGoodsStandardWeight", e.getNxGoodsStandardWeight());
        m.put("path", buildPath(e, idToName));
        return m;
    }

    /** 批发商 + 农鑫：名称检索词 LIKE 与同父兄弟 SKU 合并，截断至多 {@link #MAX_DIS_CATALOG_PREFETCH} 条。 */
    private List<NxGoodsEntity> collectDisCatalogMergedHits(String dbSearchToken, int disId, int depId) {
        String token = StrUtil.trim(dbSearchToken);
        if (StrUtil.isBlank(token)) {
            return List.of();
        }
        Map<Integer, NxGoodsEntity> merged = new LinkedHashMap<>();
        Map<String, Object> nxMap = nxDepQuickSearchBase(disId, depId);
        putNxQuickSearchStrKeys(nxMap, token);
        List<NxGoodsEntity> equalNx = nxGoodsService.queryDisGoodsEqualSearchStrWithDepOrders(nxMap);
        List<NxGoodsEntity> likeNx = nxGoodsService.queryDisGoodsQuickSearchStrWithDepOrders(nxMap);
        if (equalNx != null) {
            for (NxGoodsEntity e : equalNx) {
                if (isCatalogSku(e) && e.getNxGoodsId() != null) {
                    merged.put(e.getNxGoodsId(), e);
                }
            }
        }
        if (likeNx != null) {
            for (NxGoodsEntity e : likeNx) {
                if (isCatalogSku(e) && e.getNxGoodsId() != null) {
                    merged.putIfAbsent(e.getNxGoodsId(), e);
                }
            }
        }
        Map<String, Object> gbMap = new HashMap<>(4);
        gbMap.put("disId", disId);
        gbMap.put("searchStr", token);
        List<GbDistributerGoodsEntity> gbHits = gbDistributerGoodsService.queryGbDisGoodsQuickSearchStr(gbMap);
        if (gbHits != null) {
            for (GbDistributerGoodsEntity g : gbHits) {
                if (g == null || g.getGbDgNxGoodsId() == null) {
                    continue;
                }
                NxGoodsEntity nx = nxGoodsService.queryObject(g.getGbDgNxGoodsId());
                if (isCatalogSku(nx)) {
                    nx.setGbDistributerGoodsEntity(g);
                    merged.put(nx.getNxGoodsId(), nx);
                }
            }
        }
        if (merged.isEmpty()) {
            return List.of();
        }
        Set<Integer> fatherIds = new LinkedHashSet<>();
        for (NxGoodsEntity e : merged.values()) {
            if (e.getNxGoodsFatherId() != null) {
                fatherIds.add(e.getNxGoodsFatherId());
            }
        }
        for (Integer fid : fatherIds) {
            LambdaQueryWrapper<NxGoodsEntity> w = new LambdaQueryWrapper<>();
            w.eq(NxGoodsEntity::getNxGoodsFatherId, fid).eq(NxGoodsEntity::getNxGoodsLevel, 3)
                    .and(q -> q.isNull(NxGoodsEntity::getNxGoodsIsHidden).or().eq(NxGoodsEntity::getNxGoodsIsHidden, 0));
            List<NxGoodsEntity> siblings = nxGoodsService.list(w);
            if (siblings != null) {
                for (NxGoodsEntity s : siblings) {
                    if (s != null && s.getNxGoodsId() != null && isCatalogSku(s)) {
                        merged.putIfAbsent(s.getNxGoodsId(), s);
                    }
                }
            }
        }
        List<NxGoodsEntity> ordered = new ArrayList<>(merged.values());
        ordered.sort(Comparator
                .comparing((NxGoodsEntity e) -> e.getNxGoodsFatherId() != null ? e.getNxGoodsFatherId() : 0)
                .thenComparing(e -> e.getNxGoodsSort() != null ? e.getNxGoodsSort() : 0)
                .thenComparing(e -> e.getNxGoodsSonsSort() != null ? e.getNxGoodsSonsSort() : 0)
                .thenComparing(e -> e.getNxGoodsId() != null ? e.getNxGoodsId() : 0));
        if (ordered.size() > MAX_DIS_CATALOG_PREFETCH) {
            log.warn("{} dis_catalog_prefetch truncated to max={}", L, MAX_DIS_CATALOG_PREFETCH);
            return new ArrayList<>(ordered.subList(0, MAX_DIS_CATALOG_PREFETCH));
        }
        return ordered;
    }

    /** 首轮名称与 DeepSeek「近义词」仍无候选时再走一级目录两步。 */
    private R tryGoodsNameAliasesLlmDisPrefetch(GbAiGoodsAddAnalyzeRequest req, String sessionId, int disId, int depId,
                                              String goodsName, String goodsSpec, String goodsFurtherDescription,
                                              String userNameTokenSkipped) {
        String skillAlias = loadSkillFile("ai-skill-goods-name-near-alias.md");
        String userAlias = buildUserBlock(goodsName, goodsSpec, goodsFurtherDescription)
                + "\n【说明】按用户当前名称检索批发商与农鑫目录后**尚未得到有效候选 SKU**；请只输出 aliases JSON，不要有其它文字。\n";
        List<Map<String, String>> msgsAlias = List.of(
                Map.of("role", "system", "content", skillAlias + "\n\n请只输出一个 JSON，不要 Markdown 围栏。"),
                Map.of("role", "user", "content", userAlias)
        );
        log.info("{} analyze step=llm_name_alias_request systemChars={} tokenChars={}", L,
                skillAlias.length(), userAlias.length());
        String rawAlias = deepSeekCompletionClient.complete(msgsAlias, "goods-add-name-alias", goodsAddTemperature,
                GOODS_NAME_ALIAS_LLM_MAX_TOKENS);
        log.info("{} analyze step=llm_name_alias_response rawChars={} rawPreview={}", L,
                rawAlias != null ? rawAlias.length() : 0, logPreview(rawAlias, 420));
        ParsedAliases parsed = GoodsNameNearAliasParser.parse(rawAlias, MAX_ALIAS_QUERY_TERMS);
        if (!parsed.structuredOk() || parsed.aliases().isEmpty()) {
            log.info("{} analyze step=name_alias_empty_or_parse_miss", L);
            return null;
        }
        String normUser = nxSkuSpecNorm(userNameTokenSkipped).toLowerCase(Locale.ROOT);
        Map<Integer, NxGoodsEntity> union = new LinkedHashMap<>();
        List<String> usedTerms = new ArrayList<>();
        for (String term : parsed.aliases()) {
            String t = StrUtil.trim(term);
            if (StrUtil.isBlank(t)) {
                continue;
            }
            if (normUser.equals(nxSkuSpecNorm(t).toLowerCase(Locale.ROOT))) {
                continue;
            }
            usedTerms.add(t);
            for (NxGoodsEntity one : collectDisCatalogMergedHits(t, disId, depId)) {
                if (one != null && one.getNxGoodsId() != null) {
                    union.putIfAbsent(one.getNxGoodsId(), one);
                }
            }
        }
        if (union.isEmpty()) {
            log.info("{} analyze step=name_alias_terms_no_hit terms={}", L, usedTerms);
            return null;
        }
        List<NxGoodsEntity> orderedUnion = new ArrayList<>(union.values());
        orderedUnion.sort(Comparator
                .comparing((NxGoodsEntity e) -> e.getNxGoodsFatherId() != null ? e.getNxGoodsFatherId() : 0)
                .thenComparing(e -> e.getNxGoodsSort() != null ? e.getNxGoodsSort() : 0)
                .thenComparing(e -> e.getNxGoodsSonsSort() != null ? e.getNxGoodsSonsSort() : 0)
                .thenComparing(e -> e.getNxGoodsId() != null ? e.getNxGoodsId() : 0));
        if (orderedUnion.size() > MAX_DIS_CATALOG_PREFETCH) {
            orderedUnion = new ArrayList<>(orderedUnion.subList(0, MAX_DIS_CATALOG_PREFETCH));
        }
        String hint = DIS_PREFETCH_HINT_ALIAS_BRIDGE;
        if (StrUtil.isNotBlank(parsed.userFacingSummary())) {
            hint = hint + " " + StrUtil.trim(parsed.userFacingSummary());
        }
        log.info("{} analyze step=name_alias_prefetch_hit terms={} skuCount={}", L, usedTerms, orderedUnion.size());
        List<String> aliasUi = usedTerms.isEmpty() ? new ArrayList<>(parsed.aliases()) : new ArrayList<>(usedTerms);
        return finishDisCatalogPrefetchFromOrdered(req, sessionId, disId, depId,
                orderedUnion, goodsName, goodsSpec, goodsFurtherDescription, false,
                hint, aliasUi);
    }

    /** 预检索：先试名称+规格完全一致；再批发商 + 农鑫名称 LIKE；均无则 DeepSeek「近义词」再查库；仍无再走一级两步。 */
    private R tryDisCatalogPrefetchAnalyze(GbAiGoodsAddAnalyzeRequest req, String sessionId, int disId, int depId,
                                           String goodsName, String goodsSpec, String goodsFurtherDescription) {
        String token = StrUtil.trim(goodsName);
        if (token.length() < 1) {
            return null;
        }
        R exactFirst = tryExactGoodsNameSpecDisPrefetchFirst(req, sessionId, disId, depId,
                goodsName, goodsSpec, goodsFurtherDescription);
        if (exactFirst != null) {
            return exactFirst;
        }
        List<NxGoodsEntity> primary = collectDisCatalogMergedHits(token, disId, depId);
        if (!primary.isEmpty()) {
            return finishDisCatalogPrefetchFromOrdered(req, sessionId, disId, depId,
                    primary, goodsName, goodsSpec, goodsFurtherDescription, false, null, null);
        }
        R aliasR = tryGoodsNameAliasesLlmDisPrefetch(req, sessionId, disId, depId, goodsName, goodsSpec,
                goodsFurtherDescription, token);
        if (aliasR != null) {
            return aliasR;
        }
        return null;
    }

    /**
     * 优先：本批发商商品 / 农鑫四级 SKU 与「名称 + 规格」字面完全一致时，只返回该一条，不再走模糊检索与同父兄弟展开。
     */
    private R tryExactGoodsNameSpecDisPrefetchFirst(GbAiGoodsAddAnalyzeRequest req, String sessionId, int disId, int depId,
                                                    String goodsName, String goodsSpec, String goodsFurtherDescription) {
        String nt = StrUtil.trim(goodsName);
        String st = StrUtil.trim(goodsSpec);
        if (StrUtil.isBlank(nt) || StrUtil.isBlank(st)) {
            return null;
        }
        LambdaQueryWrapper<GbDistributerGoodsEntity> gwb = new LambdaQueryWrapper<>();
        gwb.eq(GbDistributerGoodsEntity::getGbDgDistributerId, disId)
                .eq(GbDistributerGoodsEntity::getGbDgGoodsName, nt)
                .eq(GbDistributerGoodsEntity::getGbDgGoodsStandardname, st)
                .last("LIMIT 1");
        GbDistributerGoodsEntity gbHit = gbDistributerGoodsService.getOne(gwb, false);
        if (gbHit != null && gbHit.getGbDgNxGoodsId() != null) {
            NxGoodsEntity nx = nxGoodsService.getById(gbHit.getGbDgNxGoodsId());
            if (isCatalogSku(nx)) {
                nx.setGbDistributerGoodsEntity(gbHit);
                log.info("{} analyze step=dis_exact_prefetch path=gb_distributer nxGoodsId={}", L, nx.getNxGoodsId());
                return finishDisCatalogPrefetchFromOrdered(req, sessionId, disId, depId,
                        List.of(nx), goodsName, goodsSpec, goodsFurtherDescription, true, DIS_PREFETCH_HINT_EXACT_MATCH, null);
            }
        }
        /* 临时品等：无农鑫 SKU 或 nx 非目录 SKU，仍为「名称+规格」完全命中批发商商品 —— 直接返回，不再查目录/LIKE */
        if (gbHit != null) {
            GbDistributerGoodsEntity reloaded = gbHit.getGbDistributerGoodsId() != null
                    ? gbDistributerGoodsService.getById(gbHit.getGbDistributerGoodsId()) : null;
            if (reloaded != null) {
                gbHit = reloaded;
            }
            GbDepartmentDisGoodsEntity dep = ensureDepDisGoodsForDirectTemp(
                    req, sessionId, nt, st, goodsFurtherDescription, gbHit, st);
            log.info("{} analyze step=dis_exact_prefetch path=local_gb_name_spec_exact gbDistributerGoodsId={} nxGoodsId={}",
                    L, gbHit.getGbDistributerGoodsId(), gbHit.getGbDgNxGoodsId());
            return directTempDuplicatePayload(sessionId, nt, st, goodsFurtherDescription, gbHit, dep,
                    "与您已有批发商商品名称、规格完全一致，已直接返回该商品，未继续检索目录。",
                    ANALYZE_MODE_AI);
        }
        NxGoodsEntity nxOnly = findNxSkuLevel3ExactByNameAndSpec(nt, st);
        if (nxOnly != null) {
            log.info("{} analyze step=dis_exact_prefetch path=nx_catalog nxGoodsId={}", L, nxOnly.getNxGoodsId());
            return finishDisCatalogPrefetchFromOrdered(req, sessionId, disId, depId,
                    List.of(nxOnly), goodsName, goodsSpec, goodsFurtherDescription, true, DIS_PREFETCH_HINT_EXACT_MATCH, null);
        }
        return null;
    }

    private static String nxSkuSpecNorm(String v) {
        return v == null ? "" : StrUtil.trim(v);
    }

    /** 农鑫四级 SKU：名称 + 规格（标准名）字符串与用户输入完全一致。 */
    private NxGoodsEntity findNxSkuLevel3ExactByNameAndSpec(String goodsNameTrim, String goodsSpecTrim) {
        String specKey = nxSkuSpecNorm(goodsSpecTrim);
        List<NxGoodsEntity> cand = nxGoodsService.list(new LambdaQueryWrapper<NxGoodsEntity>()
                .eq(NxGoodsEntity::getNxGoodsLevel, 3)
                .eq(NxGoodsEntity::getNxGoodsName, goodsNameTrim)
                .last("LIMIT 400"));
        if (cand == null) {
            return null;
        }
        for (NxGoodsEntity e : cand) {
            if (!isCatalogSku(e)) {
                continue;
            }
            if (nxSkuSpecNorm(e.getNxGoodsStandardname()).equals(specKey)) {
                return e;
            }
        }
        return null;
    }

    /**
     * @param assistantMessageOverride      非空时作为主文案（完全一致短路或近义词桥接）
     * @param disCatalogPrefetchUsedAliases 非空时写入 data，表示本次预检索实际使用的近义检索词（首轮名称检索无此项）
     */
    private R finishDisCatalogPrefetchFromOrdered(GbAiGoodsAddAnalyzeRequest req, String sessionId, int disId, int depId,
                                                  List<NxGoodsEntity> ordered,
                                                  String goodsName, String goodsSpec, String goodsFurtherDescription,
                                                  boolean exactMatchShortcut,
                                                  String assistantMessageOverride,
                                                  List<String> disCatalogPrefetchUsedAliases) {
        Map<Integer, String> idToName = loadAncestorNames(ordered);
        List<Integer> whitelist = new ArrayList<>();
        Map<Integer, Map<String, Object>> sessionCandidateMaps = new LinkedHashMap<>();
        List<Map<String, Object>> choiceRows = new ArrayList<>();
        for (NxGoodsEntity e : ordered) {
            Map<String, Object> cand = toCandidateMap(e, idToName);
            enrichCandidateWithDisImport(cand, e, disId);
            choiceRows.add(cand);
            whitelist.add(e.getNxGoodsId());
            sessionCandidateMaps.put(e.getNxGoodsId(), cand);
        }
        long cntAlready = choiceRows.stream()
                .filter(m -> DIS_IMPORT_ALREADY.equals(m.get("disImportStatus")))
                .count();
        long cntNot = choiceRows.size() - cntAlready;
        final String prefetchFlowState;
        final String catalogHitComposition;
        final String prefetchAssistant;
        if (StrUtil.isNotBlank(assistantMessageOverride)) {
            prefetchFlowState = cntAlready > 0 && cntNot > 0 ? FLOW_DIS_CATALOG_MIXED
                    : (cntAlready > 0 ? FLOW_DIS_CATALOG_GB_ONLY : FLOW_DIS_CATALOG_NX_ONLY);
            catalogHitComposition = cntAlready > 0 && cntNot > 0 ? "MIXED"
                    : (cntAlready > 0 ? "GB_ONLY" : "NX_ONLY");
            prefetchAssistant = assistantMessageOverride;
        } else if (cntAlready > 0 && cntNot > 0) {
            prefetchFlowState = FLOW_DIS_CATALOG_MIXED;
            catalogHitComposition = "MIXED";
            prefetchAssistant = DIS_PREFETCH_HINT_MIXED;
        } else if (cntAlready > 0) {
            prefetchFlowState = FLOW_DIS_CATALOG_GB_ONLY;
            catalogHitComposition = "GB_ONLY";
            prefetchAssistant = DIS_PREFETCH_HINT_GB_ONLY;
        } else {
            prefetchFlowState = FLOW_DIS_CATALOG_NX_ONLY;
            catalogHitComposition = "NX_ONLY";
            prefetchAssistant = DIS_PREFETCH_HINT_NX_ONLY;
        }
        GoodsAddSessionSnapshot snap = new GoodsAddSessionSnapshot(
                sessionId, disId, req.getDepartmentId(), depId, req.getDepFatherId(),
                goodsName, goodsSpec, goodsFurtherDescription, whitelist, sessionCandidateMaps,
                null, List.of(), List.of(), null, System.currentTimeMillis());
        sessionStore.put(snap);
        log.info("{} analyze step=dis_catalog_prefetch_hit sessionId={} exactShortcut={} flowPref={} composition={} alreadyRows={} notRows={}",
                L, sessionId, exactMatchShortcut, prefetchFlowState, catalogHitComposition, cntAlready, cntNot);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("flowState", prefetchFlowState);
        data.put("catalogHitComposition", catalogHitComposition);
        data.put("assistantMessage", prefetchAssistant);
        data.put("matchSummary", null);
        data.put("candidates", choiceRows);
        data.put("branchOptions", null);
        data.put("tempPreview", buildTempPreview(goodsName, goodsSpec, goodsFurtherDescription));
        data.put("persistedGoods", null);
        data.put("nxCatalogConfirmIntents", nxCatalogConfirmIntentsForDisPrefetch(prefetchFlowState));
        data.put("catalogLevel1Options", null);
        data.put("analyzeModeHint", "以上都不是时，请在同一 sessionId 下传 skipCatalogPrefetch=true 再调本接口，将进入 AI 对照一级/二级目录与 SKU；或改选手动选目录（analyzeMode=MANUAL_CATALOG）。");
        data.put("analyzeMode", ANALYZE_MODE_AI);
        data.put("disCatalogChoices", choiceRows);
        if (disCatalogPrefetchUsedAliases != null && !disCatalogPrefetchUsedAliases.isEmpty()) {
            data.put("disCatalogPrefetchUsedAliases", disCatalogPrefetchUsedAliases);
        }
        R r = R.ok();
        r.put("flowState", prefetchFlowState);
        r.put("data", data);
        return r;
    }

    /**
     * 预检索分页：按三类 {@code DIS_CATALOG_*}，仅展示与该类相关的 confirm 文案（前端也可仍按 {@code flowState} 裁剪按钮）。
     */
    private static List<Map<String, Object>> nxCatalogConfirmIntentsForDisPrefetch(String disPrefetchFlowState) {
        if (FLOW_DIS_CATALOG_GB_ONLY.equals(disPrefetchFlowState)) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("intent", NX_INTENT_ADD_SIBLING_SKU);
            one.put("label", "在同品类下新增"); //保留我输入的名称和规格，在同品类下新增一条目录 SKU
            return List.of(one);
        }
        if (FLOW_DIS_CATALOG_NX_ONLY.equals(disPrefetchFlowState)) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("intent", NX_INTENT_USE_MATCHED);
            one.put("label", "下载商品"); //使用该目录规格并加入批发商商品
            return List.of(one);
        }
        return nxCatalogConfirmIntentsPayload();
    }


    private GbDistributerGoodsEntity findExistingDistributerGoodsByNx(int disId, int nxGoodsId) {
        LambdaQueryWrapper<GbDistributerGoodsEntity> w = new LambdaQueryWrapper<>();
        w.eq(GbDistributerGoodsEntity::getGbDgDistributerId, disId)
                .eq(GbDistributerGoodsEntity::getGbDgNxGoodsId, nxGoodsId)
                .last("LIMIT 1");
        return gbDistributerGoodsService.getOne(w, false);
    }

    private GbDistributerGoodsEntity resolvedGbOnSku(NxGoodsEntity e, int disId) {
        if (e.getGbDistributerGoodsEntity() != null && e.getGbDistributerGoodsEntity().getGbDistributerGoodsId() != null) {
            return e.getGbDistributerGoodsEntity();
        }
        return findExistingDistributerGoodsByNx(disId, e.getNxGoodsId());
    }

    /**
     * 与预检索列表字段一致：标明该目录 SKU 是否已对应本批发商商品（未入库为 {@link #DIS_IMPORT_NOT}），供小程序「下载到本店」等 UI。
     */
    private void enrichCandidateWithDisImport(Map<String, Object> cand, NxGoodsEntity e, int disId) {
        GbDistributerGoodsEntity gb = resolvedGbOnSku(e, disId);
        String importStatus = gb != null && gb.getGbDistributerGoodsId() != null ? DIS_IMPORT_ALREADY : DIS_IMPORT_NOT;
        Integer gbId = gb != null ? gb.getGbDistributerGoodsId() : null;
        cand.put("disImportStatus", importStatus);
        cand.put("gbDistributerGoodsId", gbId);
        cand.put("useConfirmApi", DIS_IMPORT_NOT.equals(importStatus));
    }

    private static Map<String, Object> nxDepQuickSearchBase(int disId, int depId) {
        Map<String, Object> m = new HashMap<>();
        m.put("gbDisId", disId);
        m.put("gbDepId", depId);
        m.put("isHidden", 0);
        return m;
    }

    private static void putNxQuickSearchStrKeys(Map<String, Object> nxMap, String searchStrTrimmed) {
        boolean han = searchStrTrimmed.matches(".*[\\u4E00-\\u9FFF].*");
        if (han) {
            nxMap.put("searchStr", searchStrTrimmed);
            nxMap.put("searchPinyin", hanziToPinyin(searchStrTrimmed));
        } else {
            nxMap.put("searchStr", searchStrTrimmed);
            nxMap.put("searchPinyin", searchStrTrimmed);
        }
    }

    /** 与目录对齐：存在、三级 SKU、未隐藏。 */
    private static boolean isCatalogSku(NxGoodsEntity e) {
        if (e == null || e.getNxGoodsId() == null) {
            return false;
        }
        if (!Integer.valueOf(3).equals(e.getNxGoodsLevel())) {
            return false;
        }
        Integer hid = e.getNxGoodsIsHidden();
        return hid == null || hid == 0;
    }

    private static String buildUserBlock(String goodsName, String goodsSpec, String goodsFurtherDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户输入】\n");
        sb.append("商品名称：").append(goodsName).append("\n");
        sb.append("用户规格：").append(goodsSpec).append("\n");
        if (StrUtil.isNotBlank(goodsFurtherDescription)) {
            sb.append("说明：").append(goodsFurtherDescription).append("\n");
        }
        sb.append("\n请给出 JSON 决策。");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private String loadSkillFile(String filename) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            log.warn("{} skill not found: {}", L, filename);
        } catch (IOException e) {
            log.error("{} skill load failed: {}", L, e.getMessage());
        }
        return "";
    }
}
