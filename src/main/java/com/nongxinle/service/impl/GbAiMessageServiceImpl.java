package com.nongxinle.service.impl;

import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.mapper.GbAiMessageMapper;
import com.nongxinle.service.GbAiMessageService;
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
public class GbAiMessageServiceImpl extends ServiceImpl<GbAiMessageMapper, GbAiMessageEntity> implements GbAiMessageService {

	@Override
	public List<GbAiMessageEntity> queryMessageListByParams(Map<String, Object> params) {
		return baseMapper.queryMessageListByParams(params);
	}
}
