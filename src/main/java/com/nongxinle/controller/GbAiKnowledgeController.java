package com.nongxinle.controller;

import com.nongxinle.entity.GbAiKnowledgeEntity;
import com.nongxinle.service.GbAiKnowledgeService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI知识库 Controller
 *
 * @author lpy
 * @date 2026-04-11
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/knowledge")
@Tag(name = "AI知识库", description = "餐饮专家知识库接口")
public class GbAiKnowledgeController {

    private final GbAiKnowledgeService knowledgeService;

    /**
     * 获取所有分类
     */
    @GetMapping("/categories")
    @Operation(summary = "获取所有分类", description = "获取知识库所有可用的分类")
    public R getCategories() {
        List<String> categories = knowledgeService.getAllCategories();
        return R.ok().put("data", categories);
    }

    /**
     * 获取摘要列表（阶段一用）
     */
    @GetMapping("/summary")
    @Operation(summary = "获取摘要列表", description = "获取知识摘要列表，不含详细内容，用于快速推荐")
    public R getSummaryList(
            @Parameter(description = "分类")
            @RequestParam(required = false) String category,
            @Parameter(description = "对话类型: 0=通用, 1=促销, 2=公众号")
            @RequestParam(required = false) Integer type,
            @Parameter(description = "标签")
            @RequestParam(required = false) String tags) {
        List<GbAiKnowledgeEntity> list = knowledgeService.getSummaryList(category, type, tags);
        return R.ok().put("data", list);
    }

    /**
     * 获取详细内容（阶段二用）
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "获取详细内容", description = "获取知识的完整内容")
    public R getDetail(
            @Parameter(description = "知识ID")
            @PathVariable Integer id) {
        GbAiKnowledgeEntity knowledge = knowledgeService.getDetail(id);
        if (knowledge != null) {
            return R.ok().put("data", knowledge);
        }
        return R.error("知识不存在");
    }

    /**
     * 根据标签推荐知识
     */
    @GetMapping("/recommend")
    @Operation(summary = "推荐知识", description = "根据标签推荐知识")
    public R recommend(
            @Parameter(description = "标签")
            @RequestParam(required = false) String tags,
            @Parameter(description = "返回数量")
            @RequestParam(required = false, defaultValue = "5") Integer limit) {
        List<GbAiKnowledgeEntity> list = knowledgeService.recommendByTags(tags, limit);
        return R.ok().put("data", list);
    }

    /**
     * 记录知识使用
     */
    @PostMapping("/usage/{id}")
    @Operation(summary = "记录使用", description = "记录知识被使用，增加使用次数")
    public R recordUsage(
            @Parameter(description = "知识ID")
            @PathVariable Integer id) {
        knowledgeService.recordUsage(id);
        return R.ok();
    }

    /**
     * 查询知识列表（默认排除已删除的）
     */
    @GetMapping("/list")
    @Operation(summary = "知识列表", description = "查询知识列表")
    public R list(
            @Parameter(description = "分类")
            @RequestParam(required = false) String category,
            @Parameter(description = "类型")
            @RequestParam(required = false) Integer type,
            @Parameter(description = "状态: 0=草稿, 1=启用, 2=下架/删除")
            @RequestParam(required = false) Integer status) {
        List<GbAiKnowledgeEntity> list = knowledgeService.lambdaQuery()
                // 默认排除已删除的（状态=2）
                .ne(status == null, GbAiKnowledgeEntity::getGbAiKnowledgeStatus, 2)
                .eq(category != null, GbAiKnowledgeEntity::getGbAiKnowledgeCategory, category)
                .eq(type != null, GbAiKnowledgeEntity::getGbAiKnowledgeType, type)
                .eq(status != null, GbAiKnowledgeEntity::getGbAiKnowledgeStatus, status)
                .orderByDesc(GbAiKnowledgeEntity::getGbAiKnowledgeCreateTime)
                .list();
        return R.ok().put("data", list);
    }

    /**
     * 新增知识
     */
    @PostMapping("/save")
    @Operation(summary = "新增知识", description = "新增一条知识")
    public R save(@RequestBody GbAiKnowledgeEntity knowledge) {
        return knowledgeService.saveKnowledge(knowledge);
    }

    /**
     * 更新知识
     */
    @PostMapping("/update")
    @Operation(summary = "更新知识", description = "更新知识内容")
    public R update(@RequestBody GbAiKnowledgeEntity knowledge) {
        return knowledgeService.updateKnowledge(knowledge);
    }

    /**
     * 删除知识（软删除 - 改为下架状态）
     */
    @PostMapping("/delete/{id}")
    @Operation(summary = "删除知识", description = "删除知识（软删除）")
    public R delete(@PathVariable Integer id) {
        return knowledgeService.deleteKnowledge(id);
    }

    /**
     * 下架知识（软删除）
     */
    @PostMapping("/offline/{id}")
    @Operation(summary = "下架知识", description = "下架知识（软删除，状态改为2）")
    public R offline(@PathVariable Integer id) {
        return knowledgeService.softDeleteKnowledge(id);
    }

    /**
     * 彻底删除知识（物理删除）
     */
    @DeleteMapping("/remove/{id}")
    @Operation(summary = "彻底删除", description = "彻底从数据库删除，不可恢复")
    public R remove(@PathVariable Integer id) {
        return knowledgeService.removeKnowledge(id);
    }

}
