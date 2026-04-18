package com.nongxinle.service;

import com.nongxinle.entity.GbDepartmentGoodsStockEntity;

import java.util.List;
import java.util.Map;

/**
 * 部门商品库存「查询 / 统计 / 看板」编排层。
 * <p>与 {@link GbDepartmentGoodsStockService} 分工：后者以 MyBatis-Plus 与 Mapper 单表/声明式查询为主；
 * 本接口承载多步组装、按时段汇总、树形结果等<strong>只读业务</strong>。</p>
 * <p>已提供能力：</p>
 * <ul>
 *   <li>{@link #queryMendianStockTypePeriod} — 门店时段库存汇总看板</li>
 *   <li>{@link #queryDayStockByGreatId} — 按大类 + 日/周/月维度的商品与批次明细</li>
 *   <li>{@link #queryDepGoodsBusiness} — 部门商品下库存批次及每批次的 reduce 明细</li>
 * </ul>
 */
public interface GbDepartmentGoodsStockQueryService {

    /**
     * 门店库存按时间段分类统计（订货端看板）。
     */
    Map<String, Object> queryMendianStockTypePeriod(Integer disId, Integer whichDay, String searchDepIds, String searchDepId, Integer type);

    /**
     * 按大类 ID 查询某日（段）商品库存明细（含商品列表与简化批次行）。
     * <p>有有效 {@code greatId} 时不使用 {@code disId}；部门筛选见 {@code depId} / {@code searchDepId}。</p>
     */
    Map<String, Object> queryDayStockByGreatId(Integer disId, String searchDepId, Integer depId, String greatId, Integer which, Integer type);

    /**
     * 部门商品关联库存批次 + 每批次下全部库存减少（reduce）记录。
     * <p>先查指定日期范围内剩余大于 0 的批次，再查「当天、剩余为 0」的批次并合并去重，最后按批次 id 批量加载 reduce。</p>
     */
    List<GbDepartmentGoodsStockEntity> queryDepGoodsBusiness(Integer depGoodsId, String startDate, String stopDate);
}
