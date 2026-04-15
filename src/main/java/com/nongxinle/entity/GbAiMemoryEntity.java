package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;


@Data
@TableName("gb_ai_memory")
@EqualsAndHashCode(callSuper = false)
public class GbAiMemoryEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 *  记忆ID
	 */
	@TableId(type = IdType.AUTO)
	private Long gbAiMemoryId;
	/**
	 *  部门ID
	 */
	private Long gbAiMemoryDepartmentId;
	/**
	 *  部门用户ID
	 */
	private Long gbAiMemoryUserId;
	/**
	 *  记忆内容摘要
	 */
	private String gbAiMemorySummary;
	/**
	 *  记忆详细内容
	 */
	private String gbAiMemoryContent;
	/**
	 *  来源对话ID
	 */
	private Long gbAiMemoryConversationId;
	/**
	 *  重要性评分 (1-10)
	 */
	private Integer gbAiMemoryImportance;
	/**
	 *  标签
	 */
	private String gbAiMemoryTags;
	/**
	 *  创建时间
	 */
	private Date gbAiMemoryCreateTime;
	/**
	 *  更新时间
	 */
	private Date gbAiMemoryUpdateTime;
	/**
	 *  最后使用时间
	 */
	private Date gbAiMemoryLastUsedTime;
	/**
	 *  使用次数
	 */
	private Integer gbAiMemoryUseCount;
	/**
	 *  状态 (0=活跃,1=归档,2=删除)
	 */
	private Integer gbAiMemoryStatus;
	/**
	 *  记忆类型 (0=普通记忆, 1=促销活动/销售额, 2=公众号相关)
	 */
	private Integer gbAiMemoryType;
	/**
	 *  记忆标题
	 */
	private String gbAiMemoryTitle;
}
