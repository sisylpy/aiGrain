package com.nongxinle.mapper;

import com.nongxinle.entity.GbAiMessageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @author lpy
 * @date 2026-04-11
 */
@Mapper
public interface GbAiMessageMapper extends BaseMapper<GbAiMessageEntity> {
	
	List<GbAiMessageEntity> queryMessageListByParams(Map<String, Object> params);

	/**
	 * Run 范围消息幂等写入（依赖 uk_gb_ai_msg_conv_run_role）。
	 */
	int upsertRunScopedMessage(
			@Param("conversationId") Long conversationId,
			@Param("userId") Long userId,
			@Param("messageType") Integer messageType,
			@Param("role") String role,
			@Param("content") String content,
			@Param("runId") Long runId,
			@Param("status") String status,
			@Param("createTime") Date createTime,
			@Param("updateTime") Date updateTime);

	Long selectMessageIdByConversationRunAndRole(
			@Param("conversationId") Long conversationId,
			@Param("runId") Long runId,
			@Param("role") String role);
	
}
