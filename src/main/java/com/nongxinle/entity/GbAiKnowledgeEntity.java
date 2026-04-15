package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * AI知识库表 gb_ai_knowledge
 *
 * @author lpy
 * @date 2026-04-11
 */
@Data
@TableName("gb_ai_knowledge")
@EqualsAndHashCode(callSuper = false)
public class GbAiKnowledgeEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "gb_ai_knowledge_id", type = IdType.AUTO)
    private Integer gbAiKnowledgeId;

    /** UUID唯一标识 */
    @TableField("gb_ai_knowledge_uuid")
    private String gbAiKnowledgeUuid;

    /** 对话类型: 0=通用, 1=促销, 2=公众号 */
    @TableField("gb_ai_knowledge_type")
    private Integer gbAiKnowledgeType;

    /** 知识大类: coupon/menu/decorate/staff/cost/traffic/customer/festival/supply/legal */
    @TableField("gb_ai_knowledge_category")
    private String gbAiKnowledgeCategory;

    /** 知识标题 */
    @TableField("gb_ai_knowledge_title")
    private String gbAiKnowledgeTitle;

    /** 摘要（1-3句话） */
    @TableField("gb_ai_knowledge_summary")
    private String gbAiKnowledgeSummary;

    /** 详细内容（支持富文本/Markdown） */
    @TableField("gb_ai_knowledge_content")
    private String gbAiKnowledgeContent;

    /** 原文来源URL */
    @TableField("gb_ai_knowledge_source_url")
    private String gbAiKnowledgeSourceUrl;

    /** 标签列表: 母亲节,满减,节日营销,低成本 */
    @TableField("gb_ai_knowledge_tags")
    private String gbAiKnowledgeTags;

    /** 专家/作者名称 */
    @TableField("gb_ai_knowledge_author")
    private String gbAiKnowledgeAuthor;

    /** 来源: 钱多多/刘一刀/王装修 */
    @TableField("gb_ai_knowledge_origin")
    private String gbAiKnowledgeOrigin;

    /** 效果评分 1-5 */
    @TableField("gb_ai_knowledge_effect_rating")
    private Integer gbAiKnowledgeEffectRating;

    /** 应用案例数 */
    @TableField("gb_ai_knowledge_effect_cases")
    private Integer gbAiKnowledgeEffectCases;

    /** 效果说明 */
    @TableField("gb_ai_knowledge_effect_note")
    private String gbAiKnowledgeEffectNote;

    /** 适用餐厅类型 */
    @TableField("gb_ai_knowledge_suitable_restaurant")
    private String gbAiKnowledgeSuitableRestaurant;

    /** 适用预算范围 */
    @TableField("gb_ai_knowledge_suitable_budget")
    private String gbAiKnowledgeSuitableBudget;

    /** 适用季节 */
    @TableField("gb_ai_knowledge_suitable_season")
    private String gbAiKnowledgeSuitableSeason;

    /** 查看次数 */
    @TableField("gb_ai_knowledge_view_count")
    private Integer gbAiKnowledgeViewCount;

    /** 被推荐/使用次数 */
    @TableField("gb_ai_knowledge_use_count")
    private Integer gbAiKnowledgeUseCount;

    /** 状态: 0=草稿, 1=启用, 2=下架 */
    @TableField("gb_ai_knowledge_status")
    private Integer gbAiKnowledgeStatus;

    /** 发布时间 */
    @TableField("gb_ai_knowledge_publish_time")
    private Date gbAiKnowledgePublishTime;

    /** 创建时间 */
    @TableField("gb_ai_knowledge_create_time")
    private Date gbAiKnowledgeCreateTime;

    /** 更新时间 */
    @TableField("gb_ai_knowledge_update_time")
    private Date gbAiKnowledgeUpdateTime;

}
