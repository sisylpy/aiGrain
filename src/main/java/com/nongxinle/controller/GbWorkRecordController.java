package com.nongxinle.controller;

import com.nongxinle.ai.workrecord.WorkRecordBusinessCardException;
import com.nongxinle.ai.workrecord.WorkRecordMutationException;
import com.nongxinle.ai.workrecord.WorkRecordSourceCardException;
import com.nongxinle.ai.workrecord.dto.WorkRecordCreateRequest;
import com.nongxinle.ai.workrecord.dto.WorkRecordFromBusinessCardRequest;
import com.nongxinle.ai.workrecord.dto.WorkRecordUpdateRequest;
import com.nongxinle.service.WorkRecordService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("ai/work-records")
@Tag(name = "店长工作记录")
@RequiredArgsConstructor
public class GbWorkRecordController {

    private final WorkRecordService workRecordService;

    @GetMapping("/conversation")
    @Operation(summary = "获取或创建长期 WORK_RECORD 会话")
    public R bootstrapConversation(
            @Parameter(description = "用户 ID") @RequestParam Long userId,
            @Parameter(description = "门店锚点部门 ID") @RequestParam Long departmentId,
            @Parameter(description = "分销商 ID") @RequestParam(required = false) Long distributerId) {
        try {
            return R.ok().put("data", workRecordService.bootstrapConversation(userId, departmentId, distributerId));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "新增工作记录（同步 AI 整理+分类）")
    public R create(@RequestBody WorkRecordCreateRequest body) {
        try {
            return R.ok().put("data", workRecordService.createRecord(body));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PostMapping("/from-business-card")
    @Operation(summary = "从业务卡片记到工作记录（同步 AI 整理+分类）")
    public R createFromBusinessCard(@RequestBody WorkRecordFromBusinessCardRequest body) {
        try {
            return R.ok().put("data", workRecordService.createFromBusinessCard(body));
        } catch (WorkRecordBusinessCardException ex) {
            return R.error(400, ex.getMessage()).put("errorCode", ex.getErrorCode());
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping("/{id}/source-card")
    @Operation(summary = "读取工作记录对应的业务来源卡片（只读，不调用 LLM）")
    public R getSourceCard(
            @PathVariable Long id,
            @Parameter(description = "用户 ID") @RequestParam Long userId) {
        try {
            return R.ok().put("data", workRecordService.getSourceCard(id, userId));
        } catch (WorkRecordSourceCardException ex) {
            return R.error(400, ex.getMessage()).put("errorCode", ex.getErrorCode());
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑工作记录整理正文（仅 polishedContent）")
    public R update(
            @PathVariable Long id,
            @Parameter(description = "用户 ID") @RequestParam Long userId,
            @Parameter(description = "整理正文（form 提交）") @RequestParam(required = false) String content,
            @RequestBody(required = false) WorkRecordUpdateRequest body) {
        try {
            WorkRecordUpdateRequest request = resolveUpdateRequest(content, body);
            return R.ok().put("data", workRecordService.updatePolishedContent(id, userId, request));
        } catch (WorkRecordMutationException ex) {
            return R.error(400, ex.getMessage()).put("errorCode", ex.getErrorCode());
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    private static WorkRecordUpdateRequest resolveUpdateRequest(
            String formContent, WorkRecordUpdateRequest body) {
        WorkRecordUpdateRequest request = new WorkRecordUpdateRequest();
        if (StringUtils.hasText(formContent)) {
            request.setContent(formContent);
            return request;
        }
        if (body != null && StringUtils.hasText(body.getContent())) {
            request.setContent(body.getContent());
            return request;
        }
        return request;
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除工作记录（物理删除 gb_work_record）")
    public R delete(
            @PathVariable Long id,
            @Parameter(description = "用户 ID") @RequestParam Long userId) {
        try {
            return R.ok().put("data", workRecordService.deleteRecord(id, userId));
        } catch (WorkRecordMutationException ex) {
            return R.error(400, ex.getMessage()).put("errorCode", ex.getErrorCode());
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PostMapping("/{id}/retry-ai")
    @Operation(summary = "重新执行 AI 整理（仅 FAILED 记录）")
    public R retryAi(
            @PathVariable Long id,
            @Parameter(description = "用户 ID") @RequestParam Long userId) {
        try {
            return R.ok().put("data", workRecordService.retryAiProcessing(id, userId));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping
    @Operation(summary = "查询工作记录列表")
    public R list(
            @Parameter(description = "用户 ID") @RequestParam Long userId,
            @Parameter(description = "门店锚点部门 ID") @RequestParam Long departmentId,
            @Parameter(description = "分销商 ID") @RequestParam(required = false) Long distributerId,
            @Parameter(description = "分类 ID 筛选") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd")
                    Date startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd")
                    Date endDate,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "50") int pageSize) {
        try {
            return R.ok()
                    .put(
                            "data",
                            workRecordService.listRecords(
                                    userId,
                                    departmentId,
                                    distributerId,
                                    categoryId,
                                    startDate,
                                    endDate,
                                    page,
                                    pageSize));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping("/categories")
    @Operation(summary = "查询可用 ACTIVE 分类")
    public R categories(
            @Parameter(description = "用户 ID") @RequestParam Long userId,
            @Parameter(description = "分销商 ID") @RequestParam(required = false) Long distributerId) {
        try {
            return R.ok().put("data", workRecordService.listCategories(userId, distributerId));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }
}
