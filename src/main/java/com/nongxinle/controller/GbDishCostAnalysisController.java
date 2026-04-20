package com.nongxinle.controller;

import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.utils.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 菜品成本 / 出库分析：{@code reportKind=salesDish} 以销售菜品为主；{@code reportKind=outboundQty} 以出库数量为主。
 */
@Slf4j
@RestController
@RequestMapping("gbDishCostAnalysis")
@RequiredArgsConstructor
public class GbDishCostAnalysisController {

    private final GbDishCostAnalysisService gbDishCostAnalysisService;

    /**
     * @param reportKind {@code salesDish}（默认）| {@code outboundQty}；大小写不敏感
     */
    @RequestMapping(value = "/report", method = RequestMethod.POST)
    @ResponseBody
    public R report(String startDate, String stopDate, Integer disId, String searchDepId,
            Integer depFatherId, String reportKind) {
        try {
            Map<String, Object> data = gbDishCostAnalysisService.buildReport(
                    startDate, stopDate, disId, searchDepId, depFatherId, reportKind);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }
}
