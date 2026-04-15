package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDistributerStandardEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 批发商商品规格Mapper接口
 */
@Mapper
public interface GbDistributerStandardMapper extends BaseMapper<GbDistributerStandardEntity> {

    GbDistributerStandardEntity queryObject(Integer gbDistributerStandardId);

    List<GbDistributerStandardEntity> queryList(Map<String, Object> map);

    int queryTotal(Map<String, Object> map);

    List<GbDistributerStandardEntity> queryDisStandardByDisGoodsIdGb(@Param("disGoodsId") Integer disGoodsId);

    List<GbDistributerStandardEntity> queryDisStandardByParams(Map<String, Object> mapNx);
}
