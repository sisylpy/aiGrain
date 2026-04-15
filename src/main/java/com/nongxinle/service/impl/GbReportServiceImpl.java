package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbReportEntity;
import com.nongxinle.mapper.GbReportMapper;
import com.nongxinle.service.GbReportService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 报表Service实现
 */
@Service
public class GbReportServiceImpl extends ServiceImpl<GbReportMapper, GbReportEntity> implements GbReportService {

    @Override
    public List<GbReportEntity> queryReportList(Map<String, Object> map) {
        Integer userId = (Integer) map.get("userId");
        List<String> types = (List<String>) map.get("types");

        LambdaQueryWrapper<GbReportEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GbReportEntity::getGbRepDisUserId, userId);
        if (types != null && !types.isEmpty()) {
            wrapper.in(GbReportEntity::getGbRepType, types);
        }
        return list(wrapper);
    }
}
