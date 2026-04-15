package com.nongxinle.service;

import com.nongxinle.entity.GbAiConversationEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @author lpy
 * @date 2026-04-11
 */
public interface GbAiConversationService extends IService<GbAiConversationEntity> {

	List<GbAiConversationEntity> queryConversationListByParams(Map<String, Object> params);
	
}
