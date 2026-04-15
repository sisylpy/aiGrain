package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDistributerEntity;

/**
 * 批发商Service接口
 */
public interface GbDistributerService extends IService<GbDistributerEntity> {

    /**
     * 查询批发商基础信息（不含部门列表，性能更好）
     */
    GbDistributerEntity queryDistributerBaseInfo(Integer disId);

    /**
     * 查询批发商完整信息（含所有部门列表）
     */
    GbDistributerEntity queryDistributerWithAllDepartments(Integer disId);

    /**
     * 注册单个门店批发商
     */
    Integer saveSingleMendianDistributerGb(GbDistributerEntity distributer);

    /**
     * 查询批发商信息（带所有部门）
     */
    GbDistributerEntity queryDistributerInfo(Integer gbDepartmentDisId);

}
