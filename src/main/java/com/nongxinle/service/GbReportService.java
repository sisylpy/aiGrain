package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbReportEntity;

import java.util.List;
import java.util.Map;

/**
 * 报表Service接口
 */
public interface GbReportService extends IService<GbReportEntity> {

    List<GbReportEntity> queryReportList(Map<String, Object> map);

}
