package com.nongxinle.service;

import java.util.Map;

/**
 * 部门订货提醒（以历史订单习惯为主，库存与损耗为辅）。
 */
public interface GbDepartmentReorderReminderService {

    /**
     * 分页返回带有订货习惯与辅助提示的部门商品列表。
     *
     * @param depId      部门 id（gb_department.gb_department_id）
     * @param page       页码，从 1 开始
     * @param limit      每页条数
     * @param windowDays 统计订货习惯的回溯天数，默认 56
     * @param minTimes   窗口内至少订货次数才参与候选，默认 2
     */
    Map<String, Object> depReorderReminderPage(Integer depId, Integer page, Integer limit,
            Integer windowDays, Integer minTimes);

    /**
     * 订货/到货频率事实（Markdown），与 {@link #depReorderReminderPage} 同源：基于 {@code gb_department_orders}
     * 已收货订单与 {@code gb_DO_arrive_date}；不按「今日是否提醒」过滤，供 AI 回答采购/订货节奏类问题。
     *
     * @param depId      部门 ID
     * @param windowDays 回溯天数，null 用默认 56
     * @param minTimes   窗口内最少已收货订单笔数，null 用默认 2
     * @param maxItems   最多输出多少个部门商品，null 用默认 25
     */
    String buildAiReorderHabitFactsMarkdown(Integer depId, Integer windowDays, Integer minTimes, Integer maxItems);
}
