package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.NxJrdhUserEntity;
import com.nongxinle.mapper.NxJrdhUserMapper;
import com.nongxinle.service.NxJrdhUserService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 今日达用户Service实现
 */
@Service
public class NxJrdhUserServiceImpl extends ServiceImpl<NxJrdhUserMapper, NxJrdhUserEntity> implements NxJrdhUserService {

    @Override
    public NxJrdhUserEntity queryWhichUserByOpenId(String openId) {
        return baseMapper.queryWhichUserByOpenId(openId);
    }

    @Override
    public NxJrdhUserEntity queryJrdhUserByAdmin(Map<String, Object> map) {
        return baseMapper.queryJrdhUserByAdmin(map);
    }

    @Override
    public NxJrdhUserEntity queryJrdhUserByParams(Map<String, Object> map) {
        return baseMapper.queryJrdhUserByParams(map);
    }

}
