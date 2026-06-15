package com.nongxinle.controller;

import com.nongxinle.ai.storeannouncement.StoreAnnouncementException;
import com.nongxinle.ai.storeannouncement.dto.StoreAnnouncementPublishRequest;
import com.nongxinle.service.StoreAnnouncementService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("store-announcements")
@Tag(name = "店内公告栏")
@RequiredArgsConstructor
public class GbStoreAnnouncementController {

    private final StoreAnnouncementService storeAnnouncementService;

    @PostMapping("/from-work-record/{recordId}")
    @Operation(summary = "从工作日志发布到店内公告栏")
    public R publishFromWorkRecord(
            @PathVariable Long recordId,
            @RequestBody(required = false) StoreAnnouncementPublishRequest body) {
        try {
            StoreAnnouncementPublishRequest req = body != null ? body : new StoreAnnouncementPublishRequest();
            return R.ok().put("data", storeAnnouncementService.publishFromWorkRecord(recordId, req));
        } catch (StoreAnnouncementException ex) {
            return R.error(400, ex.getMessage()).put("errorCode", ex.getErrorCode());
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PostMapping("/from-pin/{pinId}")
    @Operation(summary = "从图钉发布到店内公告栏")
    public R publishFromPin(
            @PathVariable Long pinId,
            @RequestBody(required = false) StoreAnnouncementPublishRequest body) {
        try {
            StoreAnnouncementPublishRequest req = body != null ? body : new StoreAnnouncementPublishRequest();
            return R.ok().put("data", storeAnnouncementService.publishFromPin(pinId, req));
        } catch (StoreAnnouncementException ex) {
            return R.error(400, ex.getMessage()).put("errorCode", ex.getErrorCode());
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PostMapping("/from-note/{noteId}")
    @Operation(summary = "从工作笔记发布到店内公告栏")
    public R publishFromNote(
            @PathVariable Long noteId,
            @RequestBody(required = false) StoreAnnouncementPublishRequest body) {
        try {
            StoreAnnouncementPublishRequest req = body != null ? body : new StoreAnnouncementPublishRequest();
            return R.ok().put("data", storeAnnouncementService.publishFromNote(noteId, req));
        } catch (StoreAnnouncementException ex) {
            return R.error(400, ex.getMessage()).put("errorCode", ex.getErrorCode());
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping
    @Operation(summary = "按门店列出已发布公告（publishedAt 倒序）")
    public R listPublished(
            @Parameter(description = "用户 ID") @RequestParam Long userId,
            @Parameter(description = "门店锚点部门 ID") @RequestParam Long departmentId,
            @Parameter(description = "分销商 ID") @RequestParam(required = false) Long distributerId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize) {
        try {
            return R.ok()
                    .put(
                            "data",
                            storeAnnouncementService.listPublished(
                                    userId, departmentId, distributerId, page, pageSize));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "公告详情")
    public R getById(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam Long departmentId,
            @RequestParam(required = false) Long distributerId) {
        try {
            return R.ok()
                    .put(
                            "data",
                            storeAnnouncementService.getById(id, userId, departmentId, distributerId));
        } catch (StoreAnnouncementException ex) {
            return R.error(400, ex.getMessage()).put("errorCode", ex.getErrorCode());
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除公告（软删，仅发布人）")
    public R delete(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam Long departmentId,
            @RequestParam(required = false) Long distributerId) {
        try {
            return R.ok()
                    .put(
                            "data",
                            storeAnnouncementService.deleteAnnouncement(
                                    id, userId, departmentId, distributerId));
        } catch (StoreAnnouncementException ex) {
            return R.error(400, ex.getMessage()).put("errorCode", ex.getErrorCode());
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }
}
