package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface GbDistributerFoodGoodsMapper extends BaseMapper<GbDistributerFoodGoodsEntity> {

    GbDistributerFoodGoodsEntity queryObject(Integer gbDistributerFoodGoodsId);

    List<GbDistributerFoodGoodsEntity> queryList(Map<String, Object> map);

    int queryTotal(Map<String, Object> map);

    void save(GbDistributerFoodGoodsEntity gbDistributerFoodGoods);

    void update(GbDistributerFoodGoodsEntity gbDistributerFoodGoods);

    void delete(Integer gbDistributerFoodGoodsId);

    void deleteBatch(Integer[] gbDistributerFoodGoodsIds);

    List<GbDistributerFoodGoodsEntity> queryFoodGoodsByParams(Map<String, Object> map);

    List<GbDistributerFoodGoodsEntity> queryFoodGoodsByFoodId(Integer gbDfgFoodId);
}
