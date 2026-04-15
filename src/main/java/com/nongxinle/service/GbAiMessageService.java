package com.nongxinle.service;

import com.nongxinle.entity.GbAiMessageEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @author lpy
 * @date 2026-04-11
 */
public interface GbAiMessageService extends IService<GbAiMessageEntity> {

	List<GbAiMessageEntity> queryMessageListByParams(Map<String, Object> params);
	
}
