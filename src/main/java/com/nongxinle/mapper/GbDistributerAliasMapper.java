package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDistributerAliasEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 批发商商品别名Mapper接口
 */
@Mapper
public interface GbDistributerAliasMapper extends BaseMapper<GbDistributerAliasEntity> {

    List<GbDistributerAliasEntity> queryDisAliasByDisGoodsId(Integer disGoodsId);
}
