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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
                    + " 需 ai.harness.replay-enabled=true；建议仅 local / 内网。")
    public R replay(@RequestBody AiHarnessReplayRequest body) {
        if (!replayEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "harness replay disabled");
        }
        try {
            return R.ok().put("replay", replayService.replay(body));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
