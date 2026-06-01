package com.nongxinle.controller;

import com.nongxinle.ai.harness.replay.AiHarnessReplayRequest;
import com.nongxinle.ai.harness.replay.AiHarnessReplayService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Harness Replay：仅跑解析链路 + 断言，不接 Graph / DeepSeek。
 */
@RestController
@RequestMapping("ai/harness")
@Tag(name = "AI Harness（回归）")
@RequiredArgsConstructor
public class AiHarnessReplayController {

    private final AiHarnessReplayService replayService;

    /** 默认关闭，勿对公网放开。 */
    @Value("${ai.harness.replay-enabled:false}")
    private boolean replayEnabled;

    @PostMapping(value = "replay", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replay 多轮解析上下文",
            description = "在同一 conversation 内依次解析 messages，写入 turn memory（与生产同源），返回每轮的 resolvedQueryContextSummary 与失败分类。"
                    + " 需 ai.harness.replay-enabled=true；建议仅 local / 内网。"
                    + " {@code userId} / {@code distributerId} / {@code departmentId} / {@code scopeMode} 可写在 JSON body，"
                    + " 也可作为 query param 传入（body 缺省时以 query 补齐）。"
                    + " {@code messages} 可省略：当 {@code caseId} 为内置固定问句 case（如 {@code BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT}）时由服务端补全；"
                    + " 否则须显式传 {@code messages}。")
    public R replay(
            @RequestBody(required = false) AiHarnessReplayRequest body,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long distributerId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String scopeMode) {
        if (!replayEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "harness replay disabled");
        }
        AiHarnessReplayRequest req = mergeReplayRequest(body, userId, distributerId, departmentId, scopeMode);
        try {
            return R.ok().put("replay", replayService.replay(req));
        } catch (IllegalArgumentException ex) {
            // 例如 userId / messages required 等均由 {@link AiHarnessReplayService} 在补齐内置问句后再校验
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    /**
     * body 为主；query param 仅在 body 对应字段为空时补齐（便于 curl 把 userId 写在 URL 上）。
     */
    private static AiHarnessReplayRequest mergeReplayRequest(
            AiHarnessReplayRequest body,
            Long userId,
            Long distributerId,
            Long departmentId,
            String scopeMode) {
        AiHarnessReplayRequest req = body != null ? body : new AiHarnessReplayRequest();
        if (req.getUserId() == null && userId != null) {
            req.setUserId(userId);
        }
        if (req.getDistributerId() == null && distributerId != null) {
            req.setDistributerId(distributerId);
        }
        if (req.getDepartmentId() == null && departmentId != null) {
            req.setDepartmentId(departmentId);
        }
        if (!StringUtils.hasText(req.getScopeMode()) && StringUtils.hasText(scopeMode)) {
            req.setScopeMode(scopeMode.trim());
        }
        return req;
    }
}
