package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepartmentGoodsDailyEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 部门商品日报Service接口
 */
public interface GbDepartmentGoodsDailyService extends IService<GbDepartmentGoodsDailyEntity> {

    GbDepartmentGoodsDailyEntity queryDepGoodsDailyItem(Map<String, Object> map);

    Integer queryDepGoodsDailyCount(Map<String, Object> map);


    Double queryDepGoodsDailyLossSubtotal(Map<String, Object> map);

    Double queryDepGoodsDailyWasteSubtotal(Map<String, Object> map);

    Double queryDepGoodsDailyProduceSubtotal(Map<String, Object> map);

}
