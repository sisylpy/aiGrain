package com.nongxinle.controller;

import com.nongxinle.dto.GbAiGoodsAddAnalyzeRequest;
import com.nongxinle.dto.GbAiGoodsAddConfirmRequest;
import com.nongxinle.service.GbAiGoodsAddService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 添加商品：目录匹配分析与确认落库（与 docs/ai-add-goods-frontend-api.md 一致）。
 * <p>实际 URL 前缀受 {@code server.servlet.context-path} 影响，例如 {@code /api/ai/goods-add/analyze}。
 */
@Slf4j
@RestController
@RequestMapping("/ai/goods-add")
@Tag(name = "AI添加商品")
@RequiredArgsConstructor
public class GbAiGoodsAddController {

    private static final String L = "[AI-GOODS-ADD]";

    private final GbAiGoodsAddService gbAiGoodsAddService;

    @PostMapping("/analyze")
    @Operation(summary = "分析/匹配目录商品")
    public R analyze(@RequestBody GbAiGoodsAddAnalyzeRequest request) {
        log.info("{} http=analyze step=entry sessionId={} analyzeMode={} confirmedGrandNxGoodsId={} skipCatalogPrefetch={} manualGg={} manualGrand={} manualFather={} disId={} depId={} depFatherId={} departmentId={} goodsName={} goodsSpec={} furtherDescChars={}",
                L,
                request != null ? request.getSessionId() : null,
                request != null ? request.getAnalyzeMode() : null,
                request != null ? request.getConfirmedGrandNxGoodsId() : null,
                request != null ? request.getSkipCatalogPrefetch() : null,
                request != null ? request.getManualGreatGrandNxGoodsId() : null,
                request != null ? request.getManualGrandNxGoodsId() : null,
                request != null ? request.getManualFatherNxGoodsId() : null,
                request != null ? request.getDistributerId() : null,
                request != null ? request.getDepId() : null,
                request != null ? request.getDepFatherId() : null,
                request != null ? request.getDepartmentId() : null,
                request != null ? abbrev(request.getGoodsName(), 40) : null,
                request != null ? abbrev(request.getGoodsSpec(), 20) : null,
                request != null && request.getGoodsFurtherDescription() != null ? request.getGoodsFurtherDescription().length() : 0);
        R r = gbAiGoodsAddService.analyze(request);
        log.info("{} http=analyze step=exit code={} flowState={}", L, r.get("code"), r.get("flowState"));
        return r;
    }

    @PostMapping("/confirm")
    @Operation(summary = "确认添加（目录或临时）")
    public R confirm(@RequestBody GbAiGoodsAddConfirmRequest request) {
        log.info("{} http=confirm step=entry sessionId={} confirmType={} nxCatalogIntent={} nxGoodsId={} goodsName={} goodsSpec={} furtherDescChars={}",
                L,
                request != null ? request.getSessionId() : null,
                request != null ? request.getConfirmType() : null,
                request != null ? request.getNxCatalogIntent() : null,
                request != null ? request.getNxGoodsId() : null,
                request != null ? abbrev(request.getGoodsName(), 40) : null,
                request != null ? abbrev(request.getGoodsSpec(), 20) : null,
                request != null && request.getGoodsFurtherDescription() != null ? request.getGoodsFurtherDescription().length() : 0);
        R r = gbAiGoodsAddService.confirm(request);
        log.info("{} http=confirm step=exit code={} flowState={}", L, r.get("code"), r.get("flowState"));
        return r;
    }

    private static String abbrev(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }
}
