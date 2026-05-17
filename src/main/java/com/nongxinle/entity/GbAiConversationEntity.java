package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;


/**
 * AI 会话主表。多智能体 Run（{@code /ai/runs}）与普通聊天共用本表：
 * {@code gb_ai_conversation_user_id} 与请求 {@code userId} 对齐后即可作为 {@code conversationId} 的安全边界。
 */
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
	 * 绑定的顾问主键 {@code gb_ai_advisor_id}；非顾问会话为空。
	 */
	private Long gbAiConversationAdvisorId;
	/**
	 * 会话线程类别：{@code ADVISOR}=顾问专属长期会话；与普通整数 {@link #gbAiConversationType} 无关。
	 */
	private String gbAiConversationThreadKind;
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

	/** 0 正常，1 归档（历史列表筛选用） */
	private Integer gbAiConversationArchived;

	private Long gbAiConversationLastRunId;

	private Long gbAiConversationLastMessageId;

	private String gbAiConversationLastIntent;

	private String gbAiConversationLastPath;
}
