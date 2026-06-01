package com.nongxinle.service;

import com.nongxinle.ai.dashboard.renderer.RenderContext;

import java.util.Map;

/**
 * AI 经营建议服务 - P1 空实现，后续基于所有模块 facts 生成建议
 */
public interface AiDashboardAdviceService {

    /**
     * 生成 AI 经营建议
     * @param ctx 渲染上下文
     * @param moduleFactMap 各模块的 key 事实（供 AI 分析）
     * @return 建议对象，P1 返回 null
     */
    Map<String, Object> generateAdvice(RenderContext ctx, java.util.Map<String, Object> moduleFactMap);
}
