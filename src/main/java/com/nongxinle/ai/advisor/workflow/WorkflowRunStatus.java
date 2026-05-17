package com.nongxinle.ai.advisor.workflow;

import java.util.Locale;

/**
 * {@code gb_ai_workflow_run.gb_ai_wr_status} 取值（与 Harness 对接后扩展状态需迁移脚本）。
 */
public enum WorkflowRunStatus {

    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public String code() {
        return name();
    }

    public static boolean isDefined(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        try {
            WorkflowRunStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
