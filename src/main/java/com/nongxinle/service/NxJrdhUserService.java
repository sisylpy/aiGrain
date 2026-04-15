package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.NxJrdhUserEntity;

import java.util.Map;

/**
 * 今日达用户Service接口
 */
public interface NxJrdhUserService extends IService<NxJrdhUserEntity> {

    /**
     * 根据微信OpenId查询用户
     */
    NxJrdhUserEntity queryWhichUserByOpenId(String openId);

    /**
     * 根据管理员参数查询用户
     */
    NxJrdhUserEntity queryJrdhUserByAdmin(Map<String, Object> map);

    /**
     * 根据参数查询今日达用户（openId和admin）
     */
    NxJrdhUserEntity queryJrdhUserByParams(Map<String, Object> map);

    /**
     * 根据ID查询用户（老项目兼容方法）
     * @param userId 用户ID
     * @return 用户实体
     */
    default NxJrdhUserEntity queryObject(Integer userId) {
        return getById(userId);
    }

}
