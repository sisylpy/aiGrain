package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;


@Data
@TableName("gb_ai_conversation")
@EqualsAndHashCode(callSuper = false)
public class GbAiConversationEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 *  对话ID
	 */
	@TableId(type = IdType.AUTO)
	private Long gbAiConversationId;
	/**
	 *  部门ID
	 */
	private Long gbAiConversationDepartmentId;
	/**
	 *  分配者ID
	 */
	private Long gbAiConversationDistributerId;
	/**
	 * 统计范围：0=单店(父部门子树) 1=集团(dis 下全部门)；见 {@link com.nongxinle.ai.scope.AiConversationScopeMode}
	 */
	private Integer gbAiConversationScopeMode;
	/**
	 *  用户ID
	 */
	private Long gbAiConversationUserId;
	/**
	 *  会话标题
	 */
	private String gbAiConversationTitle;
	/**
	 *  创建时间
	 */
	private Date gbAiConversationCreateTime;
	/**
	 *  更新时间
	 */
	private Date gbAiConversationUpdateTime;
	/**
	 *  状态 (0=进行中,1=已结束)
	 */
	private Integer gbAiConversationStatus;
	/**
	 *  对话类型 (0=普通聊天, 1=促销活动/销售额, 2=公众号相关)
	 */
	private Integer gbAiConversationType;
}
