package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.mapper.GbAiDailyRevenueMapper;
import com.nongxinle.service.GbAiDailyRevenueExcelService;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.utils.GbDateTimeUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日营业额 Service 实现
 *
 * @author lpy
 * @date 2026-04-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbAiDailyRevenueServiceImpl extends ServiceImpl<GbAiDailyRevenueMapper, GbAiDailyRevenueEntity>
        implements GbAiDailyRevenueService {

    private final GbAiDailyRevenueExcelService dailyRevenueExcelService;

    @Override
    public List<GbAiDailyRevenueEntity> queryDailyRevenueListByParams(Map<String, Object> params) {
        return baseMapper.queryDailyRevenueListByParams(params);
    }

    @Override
    public Map<String, Object> getStatsByDepartmentId(Long departmentId) {
        return baseMapper.selectStatsByDepartmentId(departmentId);
    }

    @Override
    public Map<String, Object> buildListPayload(Long departmentId, String startDate, String endDate) {
        Map<String, Object> params = new HashMap<>();
        params.put("departmentId", departmentId);
        params.put("startDate", startDate);
        params.put("endDate", endDate);
        List<GbAiDailyRevenueEntity> dailyList = queryDailyRevenueListByParams(params);
        if (dailyList == null || dailyList.isEmpty()) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> chartData = new ArrayList<>();
        for (GbAiDailyRevenueEntity item : dailyList) {
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", GbDateTimeUtils.formatDay(item.getGbAiDailyRevenueRecordDate()));
            BigDecimal dineIn = item.getGbAiDailyRevenueDineInRevenue() != null
                    ? item.getGbAiDailyRevenueDineInRevenue() : BigDecimal.ZERO;
            dayData.put("dineIn", dineIn);
            BigDecimal takeout = item.getGbAiDailyRevenueTakeoutRevenue() != null
                    ? item.getGbAiDailyRevenueTakeoutRevenue() : BigDecimal.ZERO;
            dayData.put("takeout", takeout);
            chartData.add(dayData);
        }
        result.put("chartData", chartData);
        result.put("dailyList", dailyList);
        return result;
    }

    @Override
    public void fillInsertDefaults(GbAiDailyRevenueEntity dailyRevenue) {
        if (dailyRevenue.getGbAiDailyRevenueRecordDate() == null) {
            dailyRevenue.setGbAiDailyRevenueRecordDate(new Date());
        }
        try {
            Date recordDate = dailyRevenue.getGbAiDailyRevenueRecordDate();
            if (recordDate != null) {
                dailyRevenue.setGbAiDailyRevenueWeekday(GbDateTimeUtils.weekdayForAiDailyRevenue(recordDate));
            } else {
                dailyRevenue.setGbAiDailyRevenueWeekday(1);
            }
        } catch (Exception e) {
            dailyRevenue.setGbAiDailyRevenueWeekday(1);
        }
        if (dailyRevenue.getGbAiDailyRevenueHoliday() == null) {
            dailyRevenue.setGbAiDailyRevenueHoliday("");
        }
        dailyRevenue.setGbAiDailyRevenueCreateTime(new Date());
        dailyRevenue.setGbAiDailyRevenueUpdateTime(new Date());
    }

    @Override
    public void fillUpdateWeekday(GbAiDailyRevenueEntity dailyRevenue) {
        try {
            Date recordDate = dailyRevenue.getGbAiDailyRevenueRecordDate();
            if (recordDate != null) {
                dailyRevenue.setGbAiDailyRevenueWeekday(GbDateTimeUtils.weekdayForAiDailyRevenue(recordDate));
            }
        } catch (Exception e) {
            // 保持原 weekday
        }
        dailyRevenue.setGbAiDailyRevenueUpdateTime(new Date());
    }

    @Override
    public void saveOrUpsertByDepartmentAndDate(GbAiDailyRevenueEntity dailyRevenue) {
        if (dailyRevenue.getGbAiDailyRevenueDepartmentId() == null) {
            throw new IllegalArgumentException("部门ID不能为空");
        }
        if (dailyRevenue.getGbAiDailyRevenueRecordDate() == null) {
            dailyRevenue.setGbAiDailyRevenueRecordDate(new Date());
        }
        Date recordDate = dailyRevenue.getGbAiDailyRevenueRecordDate();
        Date dayStart = GbDateTimeUtils.startOfDay(recordDate);
        Date dayEnd = GbDateTimeUtils.endOfDay(recordDate);
        GbAiDailyRevenueEntity existing = getOne(
                new LambdaQueryWrapper<GbAiDailyRevenueEntity>()
                        .eq(GbAiDailyRevenueEntity::getGbAiDailyRevenueDepartmentId, dailyRevenue.getGbAiDailyRevenueDepartmentId())
                        .ge(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, dayStart)
                        .le(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, dayEnd)
                        .last("LIMIT 1"), false);
        if (existing != null) {
            if (dailyRevenue.getGbAiDailyRevenueDistributerId() != null) {
                existing.setGbAiDailyRevenueDistributerId(dailyRevenue.getGbAiDailyRevenueDistributerId());
            }
            copyMutableDailyRevenueFields(dailyRevenue, existing);
            fillUpdateWeekday(existing);
            updateById(existing);
        } else {
            fillInsertDefaults(dailyRevenue);
            save(dailyRevenue);
        }
    }

    @Override
    public void upsertDineInRevenueOnly(Long departmentId, Long distributerId, Date recordDate, BigDecimal dineInRevenue) {
        if (departmentId == null) {
            throw new IllegalArgumentException("部门ID不能为空");
        }
        if (recordDate == null) {
            throw new IllegalArgumentException("记录日期不能为空");
        }
        Date dayStart = GbDateTimeUtils.startOfDay(recordDate);
        Date dayEnd = GbDateTimeUtils.endOfDay(recordDate);
        GbAiDailyRevenueEntity existing = getOne(
                new LambdaQueryWrapper<GbAiDailyRevenueEntity>()
                        .eq(GbAiDailyRevenueEntity::getGbAiDailyRevenueDepartmentId, departmentId)
                        .ge(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, dayStart)
                        .le(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, dayEnd)
                        .last("LIMIT 1"), false);
        BigDecimal dineIn = dineInRevenue != null ? dineInRevenue : BigDecimal.ZERO;
        if (existing != null) {
            existing.setGbAiDailyRevenueDineInRevenue(dineIn);
            if (distributerId != null) {
                existing.setGbAiDailyRevenueDistributerId(distributerId);
            }
            fillUpdateWeekday(existing);
            updateById(existing);
        } else {
            GbAiDailyRevenueEntity row = new GbAiDailyRevenueEntity();
            row.setGbAiDailyRevenueDepartmentId(departmentId);
            row.setGbAiDailyRevenueDistributerId(distributerId);
            row.setGbAiDailyRevenueRecordDate(dayStart);
            row.setGbAiDailyRevenueDineInRevenue(dineIn);
            fillInsertDefaults(row);
            save(row);
        }
    }

    private static void copyMutableDailyRevenueFields(GbAiDailyRevenueEntity from, GbAiDailyRevenueEntity to) {
        to.setGbAiDailyRevenueDineInRevenue(from.getGbAiDailyRevenueDineInRevenue());
        to.setGbAiDailyRevenueDineInOrders(from.getGbAiDailyRevenueDineInOrders());
        to.setGbAiDailyRevenueDineInCustomers(from.getGbAiDailyRevenueDineInCustomers());
        to.setGbAiDailyRevenueTakeoutRevenue(from.getGbAiDailyRevenueTakeoutRevenue());
        to.setGbAiDailyRevenueTakeoutOrders(from.getGbAiDailyRevenueTakeoutOrders());
        to.setGbAiDailyRevenuePlatformFee(from.getGbAiDailyRevenuePlatformFee());
        to.setGbAiDailyRevenueWeekday(from.getGbAiDailyRevenueWeekday());
        to.setGbAiDailyRevenueHoliday(from.getGbAiDailyRevenueHoliday());
        to.setGbAiDailyRevenueNotes(from.getGbAiDailyRevenueNotes());
    }

    @Override
    public Map<String, Object> importDailyRevenueFromExcel(MultipartFile file, Long departmentId, Long distributerId)
            throws IOException {
        dailyRevenueExcelService.assertSpreadsheetUpload(file);
        List<GbAiDailyRevenueEntity> revenueList = dailyRevenueExcelService.parseDailyRevenueExcel(file, departmentId, distributerId);
        if (revenueList.isEmpty()) {
            throw new IllegalArgumentException("Excel文件中没有有效的日营业额数据");
        }
        if (log.isDebugEnabled()) {
            for (int i = 0; i < revenueList.size(); i++) {
                GbAiDailyRevenueEntity revenue = revenueList.get(i);
                log.debug("upload-excel row {} deptId={} date={} dineIn={}",
                        i, revenue.getGbAiDailyRevenueDepartmentId(),
                        revenue.getGbAiDailyRevenueRecordDate(), revenue.getGbAiDailyRevenueDineInRevenue());
            }
        }
        List<String> emptyDateRecords = new ArrayList<>();
        for (GbAiDailyRevenueEntity revenue : revenueList) {
            if (revenue.getGbAiDailyRevenueRecordDate() == null) {
                emptyDateRecords.add("部门ID=" + revenue.getGbAiDailyRevenueDepartmentId()
                        + ", 堂食营业额=" + revenue.getGbAiDailyRevenueDineInRevenue());
            }
        }
        if (!emptyDateRecords.isEmpty()) {
            throw new IllegalArgumentException("Excel文件中存在日期为空的记录，无法处理。请检查以下数据：" + emptyDateRecords);
        }

        Date minDate = null;
        Date maxDate = null;
        for (GbAiDailyRevenueEntity r : revenueList) {
            Date rd = r.getGbAiDailyRevenueRecordDate();
            if (minDate == null || rd.before(minDate)) {
                minDate = rd;
            }
            if (maxDate == null || rd.after(maxDate)) {
                maxDate = rd;
            }
        }
        Map<String, GbAiDailyRevenueEntity> existingByDay = new HashMap<>();
        if (minDate != null && maxDate != null) {
            List<GbAiDailyRevenueEntity> existingRows = list(
                    new LambdaQueryWrapper<GbAiDailyRevenueEntity>()
                            .eq(GbAiDailyRevenueEntity::getGbAiDailyRevenueDepartmentId, departmentId)
                            .ge(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, GbDateTimeUtils.startOfDay(minDate))
                            .le(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, GbDateTimeUtils.endOfDay(maxDate)));
            for (GbAiDailyRevenueEntity ex : existingRows) {
                String dk = GbDateTimeUtils.formatDay(ex.getGbAiDailyRevenueRecordDate());
                if (dk != null) {
                    existingByDay.putIfAbsent(dk, ex);
                }
            }
        }

        Date now = new Date();
        int inserted = 0;
        int updated = 0;
        int errors = 0;
        List<String> errorMessages = new ArrayList<>();

        for (GbAiDailyRevenueEntity revenue : revenueList) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("upload-excel process deptId={} date={} dineIn={} takeout={}",
                            revenue.getGbAiDailyRevenueDepartmentId(), revenue.getGbAiDailyRevenueRecordDate(),
                            revenue.getGbAiDailyRevenueDineInRevenue(), revenue.getGbAiDailyRevenueTakeoutRevenue());
                }
                if (revenue.getGbAiDailyRevenueCreateTime() == null) {
                    revenue.setGbAiDailyRevenueCreateTime(now);
                }
                revenue.setGbAiDailyRevenueUpdateTime(now);
                String dateStr = GbDateTimeUtils.formatDay(revenue.getGbAiDailyRevenueRecordDate());
                GbAiDailyRevenueEntity existing = existingByDay.get(dateStr);
                if (existing != null) {
                    copyMutableDailyRevenueFields(revenue, existing);
                    existing.setGbAiDailyRevenueUpdateTime(now);
                    updateById(existing);
                    updated++;
                } else {
                    save(revenue);
                    inserted++;
                    if (dateStr != null) {
                        existingByDay.put(dateStr, revenue);
                    }
                }
            } catch (Exception e) {
                errors++;
                errorMessages.add("处理日期 " + revenue.getGbAiDailyRevenueRecordDate() + " 的数据时出错: " + e.getMessage());
                log.warn("upload-excel row failed date={}", revenue.getGbAiDailyRevenueRecordDate(), e);
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("total", revenueList.size());
        out.put("inserted", inserted);
        out.put("updated", updated);
        out.put("errors", errors);
        out.put("errorMessages", errorMessages);
        return out;
    }
}
