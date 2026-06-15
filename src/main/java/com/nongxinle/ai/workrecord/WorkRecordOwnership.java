package com.nongxinle.ai.workrecord;

import com.nongxinle.entity.GbWorkRecordEntity;

import java.util.Objects;

/** 店长私人工作记录归属校验：与门店范围无关，必须匹配记录人本人。 */
public final class WorkRecordOwnership {

    public static final String RETRY_NOT_ALLOWED =
            "正在处理或当前状态不可重试";

    private WorkRecordOwnership() {
    }

    public static void assertOwnedRecord(
            GbWorkRecordEntity record,
            Long userId,
            WorkRecordScopeGuard.ResolvedScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("scope required");
        }
        assertOwnedRecord(record, userId, scope.departmentId(), scope.distributerId());
    }

    public static void assertOwnedRecord(
            GbWorkRecordEntity record, Long userId, Long scopeDepartmentId, Long scopeDistributerId) {
        if (record == null) {
            throw new IllegalArgumentException("record not found");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (record.getGbWrStatus() != null && record.getGbWrStatus() == 1) {
            throw new IllegalArgumentException("record not found");
        }
        if (!Objects.equals(userId, record.getGbWrRecorderUserId())) {
            throw new IllegalArgumentException("record does not belong to current user");
        }
        if (!Objects.equals(scopeDepartmentId, record.getGbWrDepartmentId())) {
            throw new IllegalArgumentException("record store scope mismatch");
        }
        if (!Objects.equals(scopeDistributerId, record.getGbWrDistributerId())) {
            throw new IllegalArgumentException("record distributer scope mismatch");
        }
    }
}
