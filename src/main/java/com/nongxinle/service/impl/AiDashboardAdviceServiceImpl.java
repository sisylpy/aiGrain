package com.nongxinle.service.impl;

import com.nongxinle.ai.dashboard.renderer.RenderContext;
import com.nongxinle.service.AiDashboardAdviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AI 经营建议服务 - P1 空实现
 */
@Slf4j
@Service
public class AiDashboardAdviceServiceImpl implements AiDashboardAdviceService {

    @Override
    public Map<String, Object> generateAdvice(RenderContext ctx, Map<String, Object> moduleFactMap) {
        // P1: 暂不实现，固定返回 null
        return null;
    }
}
