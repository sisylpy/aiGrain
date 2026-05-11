package com.nongxinle.ai.harness.replay;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * POST /api/ai/harness/replay 请求体。
 */
@Data
public class AiHarnessReplayRequest {

    private Long userId;
    private Long departmentId;
    private Long distributerId;
    /** 可选，见 {@link com.nongxinle.ai.platform.dto.AiRunCreateRequest#getScopeMode()} */
    private String scopeMode;

    /**
     * 语义「今天」锚点，yyyy-MM-dd；不传则用当前 JVM 日期（断言不稳定）。
     * 内置用例 CASE1 文档以 2026-05-11 为锚点对齐表内区间。
     */
    private String frozenClockDate;

    /** 不传则仅用 messages replay、不做断言（或依赖 caseId 生成预期） */
    private String caseId;

    /** 自定义预期，长度应与 messages 相同；优先级高于 {@link #caseId} */
    private List<AiHarnessReplayExpectedRound> expectations = new ArrayList<>();

    private List<String> messages = new ArrayList<>();

    /**
     * true：对 visibleStoreRootIds / effectiveSqlDepartmentIds 做强校验（与环境部门树相关，易因库数据不同失败）。
     * false：跳过这两项（仍可验 intent/time/purchase）。
     */
    private boolean strictStoreSqlMatch = true;
}
