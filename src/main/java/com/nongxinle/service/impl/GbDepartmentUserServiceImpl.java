package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.mapper.GbDepartmentUserMapper;
import com.nongxinle.service.GbDepartmentUserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 订货部门用户Service实现
 */
@Service
public class GbDepartmentUserServiceImpl extends ServiceImpl<GbDepartmentUserMapper, GbDepartmentUserEntity> implements GbDepartmentUserService {

    @Override
    public GbDepartmentUserEntity queryDepUserByOpenId(String openId) {
        return baseMapper.queryDepUserByOpenId(openId);
    }

    @Override
    public GbDepartmentUserEntity queryDepUsersByOpenIdAndAdmin(Map<String, Object> map) {
        String openId = (String) map.get("openId");
        Integer admin = (Integer) map.get("admin");
        LambdaQueryWrapper<GbDepartmentUserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GbDepartmentUserEntity::getGbDuWxOpenId, openId)
               .eq(GbDepartmentUserEntity::getGbDuAdmin, admin);
        return getOne(wrapper);
    }

    @Override
    public List<GbDepartmentUserEntity> queryAllUsersByDepId(Integer depId) {
        return baseMapper.queryAllUsersByDepId(depId);
    }

    @Override
    public List<GbDepartmentUserEntity> queryUsersByAdminType(Integer adminType) {
        LambdaQueryWrapper<GbDepartmentUserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GbDepartmentUserEntity::getGbDuAdmin, adminType);
        return list(wrapper);
    }

}
