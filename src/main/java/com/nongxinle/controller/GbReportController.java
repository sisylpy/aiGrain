package com.nongxinle.controller;

import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDistributerEntity;
import com.nongxinle.entity.GbReportEntity;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDistributerService;
import com.nongxinle.service.GbReportService;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 报表Controller
 */
@RestController
@RequestMapping("gbreport")
public class GbReportController {

    @Autowired
    private GbReportService gbReportService;
    @Autowired
    private GbDistributerService gbDistributerService;
    @Autowired
    private GbDepartmentService gbDepartmentService;

    /**
     * 获取用户营业报表
     */
    @RequestMapping(value = "/getDisUserReportsBusiness/{userId}")
    @ResponseBody
    public R getDisUserReportsBusiness(@PathVariable Integer userId) {

        Map<String, Object> map1 = new java.util.HashMap<>();
        map1.put("userId", userId);
        String typesStr = "disBusiness,subDepBusiness";
        map1.put("types", Arrays.asList(typesStr.split(",")));
        System.out.println("mapapapappaBUsinesss" + map1);

        List<GbReportEntity> reportEntities = gbReportService.queryReportList(map1);
        List<Map<String, Object>> resultList = new ArrayList<>();

        for (GbReportEntity report : reportEntities) {
            String gbRepType = report.getGbRepType();

            Map<String, Object> map = new java.util.HashMap<>();
            map.put("startDate", report.getGbRepStartDate());
            map.put("stopDate", report.getGbRepStopDate());

            if ("disBusiness".equals(gbRepType)) {
                map.put("disId", report.getGbRepIds());
                Map<String, Object> stringObjectMap = bbbDisBusiness(map);
                GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(report.getGbRepIds());
                stringObjectMap.put("name", gbDistributerEntity != null ? gbDistributerEntity.getGbDistributerName() : "");
                stringObjectMap.put("report", report);
                stringObjectMap.put("type", "门店营业统计");
                resultList.add(stringObjectMap);
            }
            if ("subDepBusiness".equals(gbRepType)) {
                map.put("depId", report.getGbRepIds());
                Map<String, Object> stringObjectMap = bbbSubDepBusiness(map);
                GbDepartmentEntity departmentEntity = gbDepartmentService.getById(report.getGbRepIds());
                stringObjectMap.put("name", departmentEntity != null ? departmentEntity.getGbDepartmentName() : "");
                stringObjectMap.put("report", report);
                stringObjectMap.put("type", "部门营业统计");
                resultList.add(stringObjectMap);
            }
        }
        return R.ok().put("data", resultList);
    }

    /**
     * 门店营业统计（简化版）
     */
    private Map<String, Object> bbbDisBusiness(Map<String, Object> map) {
        Map<String, Object> result = new java.util.HashMap<>();
        // TODO: 实现完整的门店营业统计逻辑
        result.put("disId", map.get("disId"));
        result.put("startDate", map.get("startDate"));
        result.put("stopDate", map.get("stopDate"));
        return result;
    }

    /**
     * 部门营业统计（简化版）
     */
    private Map<String, Object> bbbSubDepBusiness(Map<String, Object> map) {
        Map<String, Object> result = new java.util.HashMap<>();
        // TODO: 实现完整的部门营业统计逻辑
        result.put("depId", map.get("depId"));
        result.put("startDate", map.get("startDate"));
        result.put("stopDate", map.get("stopDate"));
        return result;
    }

}
