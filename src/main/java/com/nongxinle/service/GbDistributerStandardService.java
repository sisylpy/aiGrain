package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDistributerStandardEntity;

import java.util.List;
import java.util.Map;

/**
 * 批发商商品规格Service接口
 */
public interface GbDistributerStandardService extends IService<GbDistributerStandardEntity> {

    /**
     * 查询单个规格（与老项目兼容）
     */
    GbDistributerStandardEntity queryObject(Integer gbDistributerStandardId);

    /**
     * 查询规格列表
     */
    List<GbDistributerStandardEntity> queryList(Map<String, Object> map);

    /**
     * 查询总数
     */
    int queryTotal(Map<String, Object> map);

    /**
     * 查询批发商商品的所有规格
     */
    List<GbDistributerStandardEntity> queryDisStandardByDisGoodsIdGb(Integer disGoodsId);

    /**
     * 按参数查询规格
     */
    List<GbDistributerStandardEntity> queryDisStandardByParams(Map<String, Object> mapNx);
}
