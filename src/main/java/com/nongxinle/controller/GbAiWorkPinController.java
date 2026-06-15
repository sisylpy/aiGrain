package com.nongxinle.controller;

import com.nongxinle.ai.workspace.dto.PromotePinToNoteRequest;
import com.nongxinle.ai.workspace.dto.WorkPinCreateRequest;
import com.nongxinle.service.GbAiWorkPinService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 会话内图钉（Pin）MVP：CRUD + 单 Pin 升级为 WorkNote。
 */
@RestController
@RequestMapping("ai/work-pins")
@Tag(name = "AI 工作区-图钉")
@RequiredArgsConstructor
public class GbAiWorkPinController {

    private final GbAiWorkPinService gbAiWorkPinService;

    @PostMapping
    @Operation(summary = "创建图钉")
    public R create(@RequestBody WorkPinCreateRequest body) {
        try {
            return R.ok().put("data", gbAiWorkPinService.createPin(body));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping
    @Operation(summary = "按会话列出图钉（不含完整快照）")
    public R list(
            @Parameter(description = "会话 ID") @RequestParam Long conversationId,
            @Parameter(description = "用户 ID") @RequestParam Long userId) {
        try {
            return R.ok().put("data", gbAiWorkPinService.listPins(conversationId, userId));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping("/mine")
    @Operation(summary = "当前用户全部图钉（跨会话，分页；不含完整快照）")
    public R listMine(
            @Parameter(description = "用户 ID") @RequestParam Long userId,
            @Parameter(description = "可选：按会话筛选") @RequestParam(required = false) Long conversationId,
            @Parameter(description = "可选：RUN / MESSAGE / SELECTION") @RequestParam(required = false)
                    String sourceType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return R.ok()
                    .put(
                            "data",
                            gbAiWorkPinService.listMyPins(
                                    userId, conversationId, sourceType, page, pageSize));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "图钉详情（含 sourceTextSnapshot）")
    public R detail(
            @PathVariable long id,
            @Parameter(description = "用户 ID") @RequestParam Long userId) {
        try {
            return R.ok().put("data", gbAiWorkPinService.getPinDetail(id, userId));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删除图钉")
    public R delete(
            @PathVariable long id,
            @Parameter(description = "用户 ID") @RequestParam Long userId) {
        try {
            gbAiWorkPinService.softDeletePin(id, userId);
            return R.ok();
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PostMapping("/{id}/promote-to-note")
    @Operation(summary = "将单个 Pin 升级为工作笔记（保留原 Pin）")
    public R promoteToNote(@PathVariable long id, @RequestBody PromotePinToNoteRequest body) {
        try {
            return R.ok().put("data", gbAiWorkPinService.promotePinToNote(id, body));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }
}
