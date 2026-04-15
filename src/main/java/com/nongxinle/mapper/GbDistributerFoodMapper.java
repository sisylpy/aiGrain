package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDistributerFoodEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface GbDistributerFoodMapper extends BaseMapper<GbDistributerFoodEntity> {

    GbDistributerFoodEntity queryObject(Integer gbDistributerFoodId);

    List<GbDistributerFoodEntity> queryList(Map<String, Object> map);

    int queryTotal(Map<String, Object> map);

    void save(GbDistributerFoodEntity gbDistributerFood);

    void update(GbDistributerFoodEntity gbDistributerFood);

    void delete(Integer gbDistributerFoodId);

    void deleteBatch(Integer[] gbDistributerFoodIds);

    List<GbDistributerFoodEntity> queryFoodByParams(Map<String, Object> map);

    List<GbDistributerFoodEntity> queryDisAllFood(Map<String, Object> map);
}