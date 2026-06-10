package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepartmentEntity;

import java.util.List;
import java.util.Map;

/**
 * 订货部门Service接口
 */
public interface GbDepartmentService extends IService<GbDepartmentEntity> {


    GbDepartmentEntity saveNewDepartmentGb(GbDepartmentEntity department);

    /**
     * 查询子部门
     * @param depFatherId 父部门ID
     * @return 子部门列表
     */
    List<GbDepartmentEntity> querySubDepartments(Integer depFatherId);

    /**
     * 根据分销商ID查询分组部门（门店）
     * @param map 包含disId和depType的Map
     * @return 部门列表
     */
    List<GbDepartmentEntity> queryGroupDepsByDisId(Map<String, Object> map);

    GbDepartmentEntity queryDepInfo(Map<String, Object> mapDep);

    GbDepartmentEntity saveNewDepartmentGbWithDepGoods(GbDepartmentEntity department, Integer  cankaoDepFatherId);


}
