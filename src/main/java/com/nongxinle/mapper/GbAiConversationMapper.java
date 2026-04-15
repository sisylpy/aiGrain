package com.nongxinle.mapper;

import com.nongxinle.entity.GbAiConversationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @author lpy
 * @date 2026-04-11
 */
@Mapper
public interface GbAiConversationMapper extends BaseMapper<GbAiConversationEntity> {
	
	List<GbAiConversationEntity> queryConversationListByParams(Map<String, Object> params);
	
}
