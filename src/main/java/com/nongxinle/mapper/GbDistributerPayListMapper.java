package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDistributerPayListEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 批发商结算记录Mapper接口
 */
@Mapper
public interface GbDistributerPayListMapper extends BaseMapper<GbDistributerPayListEntity> {

    int queryDisPayListCount(Map<String, Object> params);

    int queryDisRecordSecondsTotal(Map<String, Object> params);

    List<GbDistributerPayListEntity> queryPayListListByParams(Map<String, Object> params);
}
