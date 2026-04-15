package com.nongxinle.mapper;

import com.nongxinle.entity.GbAiMemoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @author lpy
 * @date 2026-04-11
 */
@Mapper
public interface GbAiMemoryMapper extends BaseMapper<GbAiMemoryEntity> {
	
	List<GbAiMemoryEntity> queryMemoryListByParams(Map<String, Object> params);
	
}
