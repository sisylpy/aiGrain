package com.nongxinle.service.impl;

import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.mapper.GbAiConversationMapper;
import com.nongxinle.service.GbAiConversationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @author lpy
 * @date 2026-04-11
 */
@Service
public class GbAiConversationServiceImpl extends ServiceImpl<GbAiConversationMapper, GbAiConversationEntity> implements GbAiConversationService {

	@Override
	public List<GbAiConversationEntity> queryConversationListByParams(Map<String, Object> params) {
		return baseMapper.queryConversationListByParams(params);
	}
}
