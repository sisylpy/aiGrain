package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepartmentUserEntity;

import java.util.List;
import java.util.Map;

/**
 * 订货部门用户Service接口
 */
public interface GbDepartmentUserService extends IService<GbDepartmentUserEntity> {

    /**
     * 根据微信OpenId查询部门用户
     */
    GbDepartmentUserEntity queryDepUserByOpenId(String openId);

    /**
     * 根据微信OpenId和管理员类型查询部门用户
     */
    GbDepartmentUserEntity queryDepUsersByOpenIdAndAdmin(Map<String, Object> map);

    /**
     * 根据部门ID查询所有用户
     */
    List<GbDepartmentUserEntity> queryAllUsersByDepId(Integer depId);

    /**
     * 根据用户角色类型（admin）查询用户列表
     * @param adminType 用户角色类型，见 {@link com.nongxinle.utils.GbConstants.DepartmentUserRole}
     * @return 用户列表
     */
    List<GbDepartmentUserEntity> queryUsersByAdminType(Integer adminType);

}
