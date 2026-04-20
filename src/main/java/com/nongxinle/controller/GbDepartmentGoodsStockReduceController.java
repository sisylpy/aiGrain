package com.nongxinle.controller;

import com.nongxinle.dto.GbDepGoodsStockAdjustResult;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepartmentGoodsStockReduceCommandService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceCostQueryService;
import com.nongxinle.service.GbDepartmentGoodsStockReducePurFenxiService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceWithDayDataService;
import com.nongxinle.utils.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 部门库存损耗相关接口。
 * <p>历史完整实现参考 {@code reference/GbDepartmentGoodsStockReduceController.legacy.txt}。</p>
 */
@Slf4j
@RestController
@RequestMapping("gbdepartmentgoodsstockreduce")
@RequiredArgsConstructor
public class GbDepartmentGoodsStockReduceController {

    private final GbDepartmentGoodsStockReduceCommandService gbDepartmentGoodsStockReduceCommandService;
    private final GbDepartmentGoodsStockReduceWithDayDataService gbDepartmentGoodsStockReduceWithDayDataService;
    private final GbDepartmentGoodsStockReducePurFenxiService gbDepartmentGoodsStockReducePurFenxiService;
    private final GbDepartmentGoodsStockReduceCostQueryService gbDepartmentGoodsStockReduceCostQueryService;

    @RequestMapping(value = "/getGoodsReduceWithDayData", method = RequestMethod.POST)
    @ResponseBody
    public R getGoodsReduceWithDayData(String startDate, String stopDate, Integer disGoodsId,
                                       String searchDepId) {
        try {
            Map<String, Object> mapResult = gbDepartmentGoodsStockReduceWithDayDataService.buildReduceWithDayData(
                    startDate, stopDate, disGoodsId, searchDepId);
            return R.ok().put("data", mapResult);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }

    /**
     * 商品成本汇总（生产 / 损耗 / 损失），数据来自 {@code gb_department_goods_stock_reduce}，不再使用日报表。
     */
    @RequestMapping(value = "/getGbGoodsCostStatistics", method = RequestMethod.POST)
    @ResponseBody
    public R getGbGoodsCostStatistics(String startDate, String stopDate,
            Integer disId, Integer greatId, String searchDepId) {
        try {
            Map<String, Object> mapR = gbDepartmentGoodsStockReduceCostQueryService.buildGoodsCostStatistics(
                    startDate, stopDate, disId, greatId, searchDepId);
            return R.ok().put("data", mapR);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }

    /**
     * 按日期与类型分页查询商品成本（reduce 聚合），不再使用日报表。
     */
    @RequestMapping(value = "/getGoodsCostBySearchDate", method = RequestMethod.POST)
    @ResponseBody
    public R getGoodsCostBySearchDate(String startDate, String stopDate, Integer disId,
            String type, String searchDepId, Integer page,
            Integer limit, Integer greatId) {
        Map<String, Object> result = gbDepartmentGoodsStockReduceCostQueryService.buildGoodsCostPage(
                startDate, stopDate, disId, type, searchDepId, page, limit, greatId);
        return R.ok().put("data", result);
    }

    @RequestMapping(value = "/deleteReduceItem/{id}")
    public R deleteReduceItem(@PathVariable Integer id) {
        GbDepGoodsStockAdjustResult result = gbDepartmentGoodsStockReduceCommandService.removeReduceItem(id);
        if (!result.isOk()) {
            return R.error(result.getCode(), result.getMessage());
        }
        return R.ok().put("data", result.getData().get("data"));
    }

    @RequestMapping(value = "/getGbPurGoodsFenxi", method = RequestMethod.POST)
    @ResponseBody
    public R getGbPurGoodsFenxi(Integer disGoodsId, String startDate, String stopDate, Integer supplierId, Integer purUserId) {
        try {
            GbDistributerGoodsEntity entity = gbDepartmentGoodsStockReducePurFenxiService.buildPurGoodsFenxi(
                    disGoodsId, startDate, stopDate, supplierId, purUserId);
            return R.ok().put("data", entity);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }
}
