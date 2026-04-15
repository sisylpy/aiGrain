package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;


@Data
@TableName("gb_ai_message")
@EqualsAndHashCode(callSuper = false)
public class GbAiMessageEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 *  消息ID
	 */
	@TableId(type = IdType.AUTO)
	private Long gbAiMessageId;
	/**
	 *  消息类型 (0=普通消息, 1=促销活动/销售额, 2=公众号相关)
	 */
	private Integer gbAiMessageType;
	/**
	 *  对话ID
	 */
	private Long gbAiMessageConversationId;
	/**
	 *  部门用户ID
	 */
	private Long gbAiMessageUserId;
	/**
	 *  角色 (user/assistant/system)
	 */
	private String gbAiMessageRole;
	/**
	 *  消息内容
	 */
	private String gbAiMessageContent;
	/**
	 *  token数量
	 */
	private Integer gbAiMessageTokenCount;
	/**
	 *  是否提取到记忆
	 */
	private Integer gbAiMessageMemoryExtracted;
	/**
	 *  创建时间
	 */
	private Date gbAiMessageCreateTime;
}
