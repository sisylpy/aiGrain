package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 批发商商品Mapper接口
 */
@Mapper
public interface GbDistributerGoodsMapper extends BaseMapper<GbDistributerGoodsEntity> {
    
    List<GbDistributerGoodsEntity> queryDisGoodsByParams(Map<String, Object> map);

    /**
     * 批发商商品快速搜索
     */
    List<GbDistributerGoodsEntity> queryGbDisGoodsQuickSearchStr(Map<String, Object> map);
}
