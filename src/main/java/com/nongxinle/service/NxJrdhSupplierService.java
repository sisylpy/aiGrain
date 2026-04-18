package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.NxJrdhSupplierEntity;

import java.util.List;
import java.util.Map;

/**
 * 今日达供应商Service接口
 */
public interface NxJrdhSupplierService extends IService<NxJrdhSupplierEntity> {

    /**
     * 根据批发商ID和用户ID查询供应商
     */
    NxJrdhSupplierEntity querySellUserSupplier(Map<String, Object> map);

    /**
     * 根据用户ID查询供应商列表
     * @param userId 用户ID
     * @return 供应商列表
     */
    List<NxJrdhSupplierEntity> querySupplierByUserId(Integer userId);

    /**
     * 根据用户ID查询供应商及其批发商信息（老项目兼容方法）
     * @param map 查询参数
     * @return 供应商列表
     */
    List<NxJrdhSupplierEntity> queryJrdhSupplerWithDisByUserId(Map<String, Object> map);

    List<NxJrdhSupplierEntity> queryJrdhSupplerByParams(Map<String, Object> map3);
}
