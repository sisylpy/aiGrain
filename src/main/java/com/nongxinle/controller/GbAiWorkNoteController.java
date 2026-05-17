package com.nongxinle.controller;

import com.nongxinle.ai.workspace.dto.WorkNoteCreateRequest;
import com.nongxinle.ai.workspace.dto.WorkNoteUpdateRequest;
import com.nongxinle.service.GbAiWorkNoteService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 工作笔记（WorkNote）MVP：会话内 CRUD。
 */
@RestController
@RequestMapping("ai/work-notes")
@Tag(name = "AI 工作区-笔记")
@RequiredArgsConstructor
public class GbAiWorkNoteController {

    private final GbAiWorkNoteService gbAiWorkNoteService;

    @PostMapping
    @Operation(summary = "创建工作笔记")
    public R create(@RequestBody WorkNoteCreateRequest body) {
        try {
            return R.ok().put("data", gbAiWorkNoteService.createNote(body));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping
    @Operation(summary = "按会话列出笔记（不含完整快照）")
    public R list(
            @Parameter(description = "会话 ID") @RequestParam Long conversationId,
            @Parameter(description = "用户 ID") @RequestParam Long userId) {
        try {
            return R.ok().put("data", gbAiWorkNoteService.listNotes(conversationId, userId));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "笔记详情（含 sourceTextSnapshot）")
    public R detail(
            @PathVariable long id,
            @Parameter(description = "用户 ID") @RequestParam Long userId) {
        try {
            return R.ok().put("data", gbAiWorkNoteService.getNoteDetail(id, userId));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新笔记标题或正文")
    public R update(@PathVariable long id, @RequestBody WorkNoteUpdateRequest body) {
        try {
            return R.ok().put("data", gbAiWorkNoteService.updateNote(id, body));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删除笔记")
    public R delete(
            @PathVariable long id,
            @Parameter(description = "用户 ID") @RequestParam Long userId) {
        try {
            gbAiWorkNoteService.softDeleteNote(id, userId);
            return R.ok();
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }
}
