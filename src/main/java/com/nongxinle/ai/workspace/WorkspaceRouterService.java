package com.nongxinle.ai.workspace;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 第一阶段：规则路由；后续替换为 LLM 结构化路由（架构文档 §10）。
 */
@Component
public class WorkspaceRouterService {

    public void route(AiRunState state) {
        String q = state.getNormalizedUserInput();
        if (!StringUtils.hasText(q)) {
            state.setWorkspaceMode(AiWorkspaceMode.BUSINESS_CHAT);
            return;
        }
        if (q.contains("报表") || q.contains("月报") || q.contains("导出") || q.contains("Excel") || q.contains("PDF")) {
            state.setWorkspaceMode(AiWorkspaceMode.REPORT_GENERATION);
        } else if (q.contains("优惠") || q.contains("套餐") || q.contains("营销") || q.contains("券")) {
            state.setWorkspaceMode(AiWorkspaceMode.MARKETING_GROWTH);
        } else if (q.contains("制度") || q.contains("SOP") || q.contains("知识")) {
            state.setWorkspaceMode(AiWorkspaceMode.KNOWLEDGE_QA);
        } else if (q.contains("任务") || q.contains("督办") || q.contains("整改")) {
            state.setWorkspaceMode(AiWorkspaceMode.TASK_MANAGEMENT);
        } else {
            state.setWorkspaceMode(AiWorkspaceMode.BUSINESS_CHAT);
        }
    }
}
