package com.nongxinle.service;

import java.util.List;
import java.util.Map;

/**
 * 添加商品分析会话：白名单 id 与上下文，供 confirm 校验（进程内实现，重启丢失）。
 */
public interface GbAiGoodsAddSessionStore {

    void put(GoodsAddSessionSnapshot snapshot);

    GoodsAddSessionSnapshot get(String sessionId);

    record GoodsAddSessionSnapshot(
            String sessionId,
            Integer distributerId,
            Long departmentId,
            Integer depId,
            Integer depFatherId,
            String goodsName,
            String goodsSpec,
            /** 与 {@link com.nongxinle.dto.GbAiGoodsAddAnalyzeRequest#getGoodsFurtherDescription()} 对齐，可为空 */
            String goodsFurtherDescription,
            List<Integer> allowedNxGoodsIdsOrdered,
            Map<Integer, Map<String, Object>> candidateByNxGoodsId,
            /** 最近一次 L1L2 通过后的一级 id，供「确认二级 / 扩充目录」校验 */
            Integer pendingGreatGrandId,
            /** 最近一次 L1L2 通过的二级 id 列表（与 branchOptions 一致） */
            List<Integer> pendingGrandIds,
            /** `BRANCH_CONFIRM` 时给前端展示的一二级选项；其它情况可为空列表 */
            List<Map<String, Object>> branchOptions,
            /**
             * 目录浏览模式：{@code null} 表示 AI 匹配会话；{@code "MANUAL"} 表示用户逐级自选分类。
             */
            String catalogBrowseMode,
            long storedAtEpochMs
    ) {}
}
