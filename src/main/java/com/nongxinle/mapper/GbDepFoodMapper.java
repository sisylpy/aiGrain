package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDepFoodEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface GbDepFoodMapper extends BaseMapper<GbDepFoodEntity> {

    GbDepFoodEntity queryObject(Integer gbDepFoodId);

    List<GbDepFoodEntity> queryList(Map<String, Object> map);

    int queryTotal(Map<String, Object> map);

    void save(GbDepFoodEntity gbDepFood);

    void update(GbDepFoodEntity gbDepFood);

    void delete(Integer gbDepFoodId);

    void deleteBatch(Integer[] gbDepFoodIds);

    List<GbDepFoodEntity> queryDepFoodByParams(Map<String, Object> map);

    List<GbDepFoodEntity> queryDepAllFood(Map<String, Object> map);

    List<GbDepFoodEntity> queryDepFoodsByDepFatherId(Integer depFatherId);
}
