package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.service.GbDepartmentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.*;
import static com.nongxinle.utils.PinYin4jUtils.getHeadStringByString;

/**
 * 订货部门Service实现
 */
@Service
public class GbDepartmentServiceImpl extends ServiceImpl<GbDepartmentMapper, GbDepartmentEntity> implements GbDepartmentService {

    @Override
    public GbDepartmentEntity queryDepInfoGb(Integer depId) {
        return baseMapper.selectById(depId);
    }

    @Override
    public GbDepartmentEntity saveNewDepartmentGb(GbDepartmentEntity department) {
        department.setGbDepartmentSettleFullTime(formatWhatFullTime(0));
        department.setGbDepartmentDepSettleId(-1);
        department.setGbDepartmentSettleDate(formatWhatDay(0));
        department.setGbDepartmentSettleWeek(getWeekOfYear(0).toString());
        department.setGbDepartmentSettleMonth(formatWhatMonth(0));
        department.setGbDepartmentSettleYear(formatWhatYear(0));
        department.setGbDepartmentSettleTimes("0");
        department.setGbDepartmentDepSettleId(-1);
        //1 save dep
        save(department);
        // 2 save subDeps
        List<GbDepartmentEntity> gbDepartmentEntityList = department.getGbDepartmentEntityList();
        if (gbDepartmentEntityList != null && gbDepartmentEntityList.size() > 0) {
            for (GbDepartmentEntity subDeps : gbDepartmentEntityList) {
                subDeps.setGbDepartmentSettleFullTime(formatFullTime());
                subDeps.setGbDepartmentSettleDate(formatWhatDay(0));
                subDeps.setGbDepartmentSettleMonth(formatWhatMonth(0));
                subDeps.setGbDepartmentSettleWeek(getWeekOfYear(0).toString());
                subDeps.setGbDepartmentSettleYear(formatWhatYear(0));
                subDeps.setGbDepartmentSettleTimes("0");
                subDeps.setGbDepartmentSubAmount(0);
                subDeps.setGbDepartmentIsGroupDep(0);
                subDeps.setGbDepartmentAttrName(subDeps.getGbDepartmentName());
                subDeps.setGbDepartmentPrintName("ApplyHalfPanel");
                String gbDepartmentName = subDeps.getGbDepartmentName();
                String headPinyin = getHeadStringByString(gbDepartmentName, false, null);
                subDeps.setGbDepartmentNamePy(headPinyin);
                subDeps.setGbDepartmentType(department.getGbDepartmentType());
                subDeps.setGbDepartmentDisId(department.getGbDepartmentDisId());
                subDeps.setGbDepartmentFatherId(department.getGbDepartmentId());
                subDeps.setGbDepartmentDepSettleId(-1);
                subDeps.setGbDepartmentLevel(1);
                save(subDeps);
            }
        }

        return department;
    }

    @Override
    public List<GbDepartmentEntity> querySubDepartments(Integer depFatherId) {
        return baseMapper.querySubDepartments(depFatherId);
    }

    @Override
    public List<GbDepartmentEntity> queryGroupDepsByDisId(Map<String, Object> map) {
        return baseMapper.queryGroupDepsByDisId(map);
    }

}
