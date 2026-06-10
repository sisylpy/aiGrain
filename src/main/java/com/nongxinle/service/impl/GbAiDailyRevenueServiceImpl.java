package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbAiDailyRevenueMapper;
import com.nongxinle.service.GbAiDailyRevenueExcelService;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbDepFoodSalesExcelImportService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.support.GbAiDailyRevenueListSupport;
import com.nongxinle.utils.GbDateTimeUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

/**
 * 日营业额 Service 实现
 *
 * @author lpy
 * @date 2026-04-11
 */
@Slf4j
@Service
public class GbAiDailyRevenueServiceImpl extends ServiceImpl<GbAiDailyRevenueMapper, GbAiDailyRevenueEntity>
        implements GbAiDailyRevenueService {

    private final GbAiDailyRevenueExcelService dailyRevenueExcelService;
    private final GbDepFoodSalesExcelImportService gbDepFoodSalesExcelImportService;
    private final GbDepartmentService departmentService;
    private final GbDepFoodSalesService gbDepFoodSalesService;

    @Autowired
    public GbAiDailyRevenueServiceImpl(
            GbAiDailyRevenueExcelService dailyRevenueExcelService,
            @Lazy GbDepFoodSalesExcelImportService gbDepFoodSalesExcelImportService,
            GbDepartmentService departmentService,
            GbDepFoodSalesService gbDepFoodSalesService) {
        this.dailyRevenueExcelService = dailyRevenueExcelService;
        this.gbDepFoodSalesExcelImportService = gbDepFoodSalesExcelImportService;
        this.departmentService = departmentService;
        this.gbDepFoodSalesService = gbDepFoodSalesService;
    }
    @Override
    public List<GbAiDailyRevenueEntity> queryDailyRevenueListByParams(Map<String, Object> params) {
        return baseMapper.queryDailyRevenueListByParams(params);
    }

    @Override
    public Map<String, Object> getStatsByDepartmentId(Long departmentFatherId, String startDate, String endDate) {
        Map<String, Object> params = new HashMap<>();
        params.put("departmentId", departmentFatherId);
        params.put("departmentScopeIds", departmentScopeIdsForParent(departmentFatherId));
        if (startDate != null && !startDate.isBlank()) {
            params.put("startDate", startDate.trim());
        }
        if (endDate != null && !endDate.isBlank()) {
            params.put("endDate", endDate.trim());
        }
        return baseMapper.selectStatsByDepartmentId(params);
    }

    @Override
    public Map<String, Object> getGroupIncomeAggregateForDepartmentIds(List<Integer> departmentIds,
            String startDate, String endDate) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
            return Collections.emptyMap();
        }
        LocalDate s = LocalDate.parse(startDate.trim());
        LocalDate e = LocalDate.parse(endDate.trim());
        return baseMapper.selectGroupIncomeAggregateForDepartmentIds(departmentIds, s, e);
    }

    @Override
    public Map<String, Object> buildListPayload(Long departmentId, String startDate, String endDate) {
        return buildListPayload(departmentId, startDate, endDate, null, null);
    }

    @Override
    public Map<String, Object> buildListPayload(Long departmentId, String startDate, String endDate,
            Long subDepId, Long distributerId) {
        if (departmentId == null) {
            return null;
        }
        if (subDepId != null) {
            GbDepartmentEntity sub = departmentService.getById(subDepId.intValue());
            if (sub == null) {
                throw new IllegalArgumentException("子部门不存在: " + subDepId);
            }
            if (!departmentId.equals(sub.getGbDepartmentFatherId() == null ? null : sub.getGbDepartmentFatherId().longValue())) {
                throw new IllegalArgumentException("subDepId 与 departmentId 不是父子关系");
            }
        }

        List<Long> scopeIds = resolveListScopeDepartmentIds(departmentId, subDepId);
        LocalDate start = parseOptionalLocalDay(startDate);
        LocalDate end = parseOptionalLocalDay(endDate);

        Map<String, Object> revParams = new HashMap<>();
        revParams.put("departmentScopeIds", scopeIds);
        if (start != null) {
            revParams.put("startDate", GbDateTimeUtils.formatDay(start));
        }
        if (end != null) {
            revParams.put("endDate", GbDateTimeUtils.formatDay(end));
        }
        List<GbAiDailyRevenueEntity> revenueRaw = queryDailyRevenueListByParams(revParams);
        List<GbAiDailyRevenueEntity> revenueByDay = revenueRaw == null || revenueRaw.isEmpty()
                ? Collections.emptyList()
                : aggregateDailyRevenueByDateForParentView(revenueRaw, departmentId);

        List<Integer> intScopeIds = toIntegerScopeIds(scopeIds);
        List<GbDepFoodSalesEntity> foodRows;
        if (intScopeIds.isEmpty()) {
            foodRows = Collections.emptyList();
        } else {
            LambdaQueryWrapper<GbDepFoodSalesEntity> foodQ = new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                    .eq(GbDepFoodSalesEntity::getGbDfsDepFatherId, departmentId.intValue())
                    .in(GbDepFoodSalesEntity::getGbDfsDepId, intScopeIds);
            if (distributerId != null) {
                foodQ.eq(GbDepFoodSalesEntity::getGbDfsDistributerId, distributerId.intValue());
            }
            if (start != null) {
                foodQ.ge(GbDepFoodSalesEntity::getGbDfsFullDate, GbDateTimeUtils.formatDay(start));
            }
            if (end != null) {
                foodQ.le(GbDepFoodSalesEntity::getGbDfsFullDate, GbDateTimeUtils.formatDay(end));
            }
            foodRows = gbDepFoodSalesService.list(foodQ);
        }

        if ((revenueByDay == null || revenueByDay.isEmpty())
                && (foodRows == null || foodRows.isEmpty())) {
            return null;
        }

        if (start == null || end == null) {
            LocalDate[] inferred = inferDateRange(revenueByDay, foodRows, start, end);
            start = inferred[0];
            end = inferred[1];
        }

        Map<String, GbAiDailyRevenueEntity> revenueByDate =
                GbAiDailyRevenueListSupport.indexRevenueByDate(revenueByDay);
        Map<String, List<GbDepFoodSalesEntity>> foodByDate =
                GbAiDailyRevenueListSupport.groupFoodSalesByDate(foodRows);

        return GbAiDailyRevenueListSupport.buildPayload(
                departmentId, subDepId, distributerId, start, end, revenueByDate, foodByDate);
    }

    private List<Long> resolveListScopeDepartmentIds(Long depFatherId, Long subDepId) {
        if (subDepId != null) {
            return Collections.singletonList(subDepId);
        }
        return departmentScopeIdsForParent(depFatherId);
    }

    private static List<Integer> toIntegerScopeIds(List<Long> scopeIds) {
        List<Integer> out = new ArrayList<>();
        if (scopeIds == null) {
            return out;
        }
        for (Long id : scopeIds) {
            if (id != null && id > 0 && id <= Integer.MAX_VALUE) {
                out.add(id.intValue());
            }
        }
        return out;
    }

    private static LocalDate parseOptionalLocalDay(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return GbDateTimeUtils.parseLocalDay(text.trim());
    }

    private static LocalDate[] inferDateRange(
            List<GbAiDailyRevenueEntity> revenueByDay,
            List<GbDepFoodSalesEntity> foodRows,
            LocalDate startHint,
            LocalDate endHint) {
        LocalDate min = startHint;
        LocalDate max = endHint;
        if (revenueByDay != null) {
            for (GbAiDailyRevenueEntity r : revenueByDay) {
                if (r == null || r.getGbAiDailyRevenueRecordDate() == null) {
                    continue;
                }
                LocalDate d = GbDateTimeUtils.toLocalDate(r.getGbAiDailyRevenueRecordDate());
                min = min == null || d.isBefore(min) ? d : min;
                max = max == null || d.isAfter(max) ? d : max;
            }
        }
        if (foodRows != null) {
            for (GbDepFoodSalesEntity row : foodRows) {
                if (row == null || row.getGbDfsFullDate() == null) {
                    continue;
                }
                LocalDate d = GbDateTimeUtils.parseLocalDay(row.getGbDfsFullDate().trim());
                min = min == null || d.isBefore(min) ? d : min;
                max = max == null || d.isAfter(max) ? d : max;
            }
        }
        if (min == null) {
            min = max;
        }
        if (max == null) {
            max = min;
        }
        return new LocalDate[] { min, max };
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
    public void backfillParentDepartmentIdIfMissing(GbAiDailyRevenueEntity entity) {
        applyParentDepartmentIdFromDeptTable(entity);
    }

    /** 与日营收回填 SQL {@code NULLIF(NULLIF(father_id, 0), -1)} 一致 */
    private static Long normalizedParentDeptId(Integer fatherId) {
        if (fatherId == null || fatherId == 0 || fatherId == -1) {
            return null;
        }
        return fatherId.longValue();
    }

    private void applyParentDepartmentIdFromDeptTable(GbAiDailyRevenueEntity entity) {
        if (entity == null || entity.getGbAiDailyRevenueParentDepartmentId() != null) {
            return;
        }
        Long deptPk = entity.getGbAiDailyRevenueDepartmentId();
        if (deptPk == null) {
            return;
        }
        GbDepartmentEntity dept = departmentService.getById(deptPk.intValue());
        if (dept == null) {
            return;
        }
        entity.setGbAiDailyRevenueParentDepartmentId(normalizedParentDeptId(dept.getGbDepartmentFatherId()));
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
            backfillParentDepartmentIdIfMissing(existing);
            fillUpdateWeekday(existing);
            updateById(existing);
        } else {
            fillInsertDefaults(dailyRevenue);
            backfillParentDepartmentIdIfMissing(dailyRevenue);
            save(dailyRevenue);
        }
    }

    @Override
    public void saveOrUpsertByParentDepartmentAndDate(GbAiDailyRevenueEntity dailyRevenue) {
        if (dailyRevenue.getGbAiDailyRevenueParentDepartmentId() == null) {
            throw new IllegalArgumentException("父部门ID不能为空");
        }
        if (dailyRevenue.getGbAiDailyRevenueRecordDate() == null) {
            dailyRevenue.setGbAiDailyRevenueRecordDate(new Date());
        }
        // 子部门 ID 用 0 表示"父部门级记录"（数据库字段 NOT NULL，不可留空）
        dailyRevenue.setGbAiDailyRevenueDepartmentId(0L);
        Date recordDate = dailyRevenue.getGbAiDailyRevenueRecordDate();
        Date dayStart = GbDateTimeUtils.startOfDay(recordDate);
        Date dayEnd = GbDateTimeUtils.endOfDay(recordDate);
        GbAiDailyRevenueEntity existing = getOne(
                new LambdaQueryWrapper<GbAiDailyRevenueEntity>()
                        .eq(GbAiDailyRevenueEntity::getGbAiDailyRevenueParentDepartmentId, dailyRevenue.getGbAiDailyRevenueParentDepartmentId())
                        .eq(GbAiDailyRevenueEntity::getGbAiDailyRevenueDepartmentId, 0L)
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
    public void updateByDepartmentAndDate(GbAiDailyRevenueEntity dailyRevenue) {
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
        if (existing == null) {
            throw new IllegalArgumentException("未找到当天(" + GbDateTimeUtils.formatDay(recordDate) + ")该部门的日营业额记录，无法更新");
        }
        if (dailyRevenue.getGbAiDailyRevenueDistributerId() != null) {
            existing.setGbAiDailyRevenueDistributerId(dailyRevenue.getGbAiDailyRevenueDistributerId());
        }
        copyMutableDailyRevenueFields(dailyRevenue, existing);
        backfillParentDepartmentIdIfMissing(existing);
        fillUpdateWeekday(existing);
        updateById(existing);
    }

    @Override
    public void updateByParentDepartmentAndDate(GbAiDailyRevenueEntity dailyRevenue) {
        if (dailyRevenue.getGbAiDailyRevenueParentDepartmentId() == null) {
            throw new IllegalArgumentException("父部门ID不能为空");
        }
        if (dailyRevenue.getGbAiDailyRevenueRecordDate() == null) {
            dailyRevenue.setGbAiDailyRevenueRecordDate(new Date());
        }
        dailyRevenue.setGbAiDailyRevenueDepartmentId(0L);
        Date recordDate = dailyRevenue.getGbAiDailyRevenueRecordDate();
        Date dayStart = GbDateTimeUtils.startOfDay(recordDate);
        Date dayEnd = GbDateTimeUtils.endOfDay(recordDate);
        GbAiDailyRevenueEntity existing = getOne(
                new LambdaQueryWrapper<GbAiDailyRevenueEntity>()
                        .eq(GbAiDailyRevenueEntity::getGbAiDailyRevenueParentDepartmentId, dailyRevenue.getGbAiDailyRevenueParentDepartmentId())
                        .eq(GbAiDailyRevenueEntity::getGbAiDailyRevenueDepartmentId, 0L)
                        .ge(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, dayStart)
                        .le(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, dayEnd)
                        .last("LIMIT 1"), false);
        if (existing == null) {
            throw new IllegalArgumentException("未找到当天(" + GbDateTimeUtils.formatDay(recordDate) + ")该父部门的日营业额记录，无法更新");
        }
        if (dailyRevenue.getGbAiDailyRevenueDistributerId() != null) {
            existing.setGbAiDailyRevenueDistributerId(dailyRevenue.getGbAiDailyRevenueDistributerId());
        }
        copyMutableDailyRevenueFields(dailyRevenue, existing);
        fillUpdateWeekday(existing);
        updateById(existing);
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
            recomputeGrossRevenue(existing);
            if (distributerId != null) {
                existing.setGbAiDailyRevenueDistributerId(distributerId);
            }
            backfillParentDepartmentIdIfMissing(existing);
            fillUpdateWeekday(existing);
            updateById(existing);
        } else {
            GbAiDailyRevenueEntity row = new GbAiDailyRevenueEntity();
            row.setGbAiDailyRevenueDepartmentId(departmentId);
            row.setGbAiDailyRevenueDistributerId(distributerId);
            row.setGbAiDailyRevenueRecordDate(dayStart);
            row.setGbAiDailyRevenueDineInRevenue(dineIn);
            recomputeGrossRevenue(row);
            fillInsertDefaults(row);
            backfillParentDepartmentIdIfMissing(row);
            save(row);
        }
    }

    @Override
    public void mergeNonDineInDailyRevenueMetrics(Long departmentId, Long distributerId, Date recordDate,
            Integer dineInOrders, Integer dineInCustomers, BigDecimal takeoutRevenue,
            Integer takeoutOrders, BigDecimal platformFee, String notes) {
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
        if (existing == null) {
            GbAiDailyRevenueEntity row = new GbAiDailyRevenueEntity();
            row.setGbAiDailyRevenueDepartmentId(departmentId);
            row.setGbAiDailyRevenueDistributerId(distributerId);
            row.setGbAiDailyRevenueRecordDate(dayStart);
            row.setGbAiDailyRevenueDineInRevenue(BigDecimal.ZERO);
            applyNonNullNonDineInMetrics(row, dineInOrders, dineInCustomers, takeoutRevenue, takeoutOrders, platformFee, notes);
            recomputeGrossRevenue(row);
            fillInsertDefaults(row);
            backfillParentDepartmentIdIfMissing(row);
            save(row);
            return;
        }
        if (distributerId != null) {
            existing.setGbAiDailyRevenueDistributerId(distributerId);
        }
        applyNonNullNonDineInMetrics(existing, dineInOrders, dineInCustomers, takeoutRevenue, takeoutOrders, platformFee, notes);
        recomputeGrossRevenue(existing);
        backfillParentDepartmentIdIfMissing(existing);
        fillUpdateWeekday(existing);
        updateById(existing);
    }

    private static void applyNonNullNonDineInMetrics(GbAiDailyRevenueEntity e,
            Integer dineInOrders, Integer dineInCustomers, BigDecimal takeoutRevenue,
            Integer takeoutOrders, BigDecimal platformFee, String notes) {
        if (dineInOrders != null) {
            e.setGbAiDailyRevenueDineInOrders(dineInOrders);
        }
        if (dineInCustomers != null) {
            e.setGbAiDailyRevenueDineInCustomers(dineInCustomers);
        }
        if (takeoutRevenue != null) {
            e.setGbAiDailyRevenueTakeoutRevenue(takeoutRevenue);
        }
        if (takeoutOrders != null) {
            e.setGbAiDailyRevenueTakeoutOrders(takeoutOrders);
        }
        if (platformFee != null) {
            e.setGbAiDailyRevenuePlatformFee(platformFee);
        }
        if (notes != null) {
            e.setGbAiDailyRevenueNotes(notes);
        }
    }

    /**
     * 重新计算总营业额 = 堂食 + 外卖，null 视为 0。
     */
    private static void recomputeGrossRevenue(GbAiDailyRevenueEntity e) {
        BigDecimal dineIn = e.getGbAiDailyRevenueDineInRevenue() != null ? e.getGbAiDailyRevenueDineInRevenue() : BigDecimal.ZERO;
        BigDecimal takeout = e.getGbAiDailyRevenueTakeoutRevenue() != null ? e.getGbAiDailyRevenueTakeoutRevenue() : BigDecimal.ZERO;
        e.setGbAiDailyRevenueGrossRevenue(dineIn.add(takeout));
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
        if (from.getGbAiDailyRevenueParentDepartmentId() != null) {
            to.setGbAiDailyRevenueParentDepartmentId(from.getGbAiDailyRevenueParentDepartmentId());
        }
        recomputeGrossRevenue(to);
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
            List<Long> scope = departmentScopeIdsForParent(departmentId);
            List<GbAiDailyRevenueEntity> existingRows = list(
                    new LambdaQueryWrapper<GbAiDailyRevenueEntity>()
                            .in(GbAiDailyRevenueEntity::getGbAiDailyRevenueDepartmentId, scope)
                            .ge(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, GbDateTimeUtils.startOfDay(minDate))
                            .le(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, GbDateTimeUtils.endOfDay(maxDate)));
            for (GbAiDailyRevenueEntity ex : existingRows) {
                String dk = GbDateTimeUtils.formatDay(ex.getGbAiDailyRevenueRecordDate());
                if (dk != null) {
                    String k = ex.getGbAiDailyRevenueDepartmentId() + "|" + dk;
                    existingByDay.putIfAbsent(k, ex);
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
                String deptDayKey = revenue.getGbAiDailyRevenueDepartmentId() + "|" + dateStr;
                GbAiDailyRevenueEntity existing = existingByDay.get(deptDayKey);
                if (existing != null) {
                    copyMutableDailyRevenueFields(revenue, existing);
                    existing.setGbAiDailyRevenueUpdateTime(now);
                    backfillParentDepartmentIdIfMissing(existing);
                    updateById(existing);
                    updated++;
                } else {
                    backfillParentDepartmentIdIfMissing(revenue);
                    save(revenue);
                    inserted++;
                    if (dateStr != null) {
                        existingByDay.put(deptDayKey, revenue);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importCombinedDailyRevenueAndFoodFromExcel(MultipartFile file, Long departmentId, Long distributerId)
            throws IOException {
        dailyRevenueExcelService.assertSpreadsheetUpload(file);
        byte[] bytes = file.getBytes();
        log.info("importCombinedExcel start depFatherId={} distributerId={} bytes={}",
                departmentId, distributerId, bytes.length);
        int[] sheets = dailyRevenueExcelService.resolveCombinedTemplateFoodAndRevenueSheetIndexes(bytes);
        int discountSheet = dailyRevenueExcelService.resolveCombinedTemplateDiscountSheetIndex(bytes);
        log.info("importCombinedExcel resolved sheetIndexes food={} revenue={} discount={}",
                sheets[0], sheets[1], discountSheet);

        Map<String, Object> foodOut = gbDepFoodSalesExcelImportService.importFoodSalesFromExcelMultipart(
                file, departmentId.intValue(), distributerId.intValue(), sheets[0], true);

        Map<String, Object> discountOut = gbDepFoodSalesExcelImportService.importDiscountFoodSalesFromCombinedSheet(
                file, departmentId.intValue(), distributerId.intValue(), discountSheet, true);

        List<GbAiDailyRevenueEntity> supplement =
                dailyRevenueExcelService.parseCombinedTemplateRevenueSheet(bytes, sheets[1], departmentId, distributerId);
        Map<String, Object> revOut;
        if (supplement.isEmpty()) {
            revOut = new HashMap<>();
            revOut.put("total", 0);
            revOut.put("inserted", 0);
            revOut.put("updated", 0);
            revOut.put("errors", 0);
            revOut.put("errorMessages", new ArrayList<String>());
            revOut.put("skippedEmptySheet", true);
        } else {
            revOut = mergeDailyRevenueSupplementFromExcel(supplement, departmentId, distributerId);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("foodSales", foodOut);
        out.put("discountFoodSales", discountOut);
        out.put("dailyRevenueSupplement", revOut);
        return out;
    }

    private Map<String, Object> mergeDailyRevenueSupplementFromExcel(
            List<GbAiDailyRevenueEntity> revenueList, Long departmentId, Long distributerId) {
        List<String> emptyDateRecords = new ArrayList<>();
        for (GbAiDailyRevenueEntity revenue : revenueList) {
            if (revenue.getGbAiDailyRevenueRecordDate() == null) {
                emptyDateRecords.add("部门ID=" + revenue.getGbAiDailyRevenueDepartmentId());
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
        Map<String, GbAiDailyRevenueEntity> existingByDeptDay = new HashMap<>();
        if (minDate != null && maxDate != null) {
            List<Long> scope = departmentScopeIdsForParent(departmentId);
            List<GbAiDailyRevenueEntity> existingRows = list(
                    new LambdaQueryWrapper<GbAiDailyRevenueEntity>()
                            .in(GbAiDailyRevenueEntity::getGbAiDailyRevenueDepartmentId, scope)
                            .ge(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, GbDateTimeUtils.startOfDay(minDate))
                            .le(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, GbDateTimeUtils.endOfDay(maxDate)));
            for (GbAiDailyRevenueEntity ex : existingRows) {
                String dk = GbDateTimeUtils.formatDay(ex.getGbAiDailyRevenueRecordDate());
                if (dk != null) {
                    String k = ex.getGbAiDailyRevenueDepartmentId() + "|" + dk;
                    existingByDeptDay.putIfAbsent(k, ex);
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
                revenue.setGbAiDailyRevenueUpdateTime(now);
                String dateStr = GbDateTimeUtils.formatDay(revenue.getGbAiDailyRevenueRecordDate());
                String deptDayKey = revenue.getGbAiDailyRevenueDepartmentId() + "|" + dateStr;
                GbAiDailyRevenueEntity existing = existingByDeptDay.get(deptDayKey);
                if (existing != null) {
                    copySupplementMutableDailyRevenueFields(revenue, existing);
                    existing.setGbAiDailyRevenueUpdateTime(now);
                    if (distributerId != null) {
                        existing.setGbAiDailyRevenueDistributerId(distributerId);
                    }
                    backfillParentDepartmentIdIfMissing(existing);
                    fillUpdateWeekday(existing);
                    updateById(existing);
                    updated++;
                } else {
                    GbAiDailyRevenueEntity row = new GbAiDailyRevenueEntity();
                    row.setGbAiDailyRevenueDepartmentId(revenue.getGbAiDailyRevenueDepartmentId());
                    row.setGbAiDailyRevenueDistributerId(distributerId);
                    row.setGbAiDailyRevenueRecordDate(revenue.getGbAiDailyRevenueRecordDate());
                    row.setGbAiDailyRevenueDineInRevenue(BigDecimal.ZERO);
                    copySupplementMutableDailyRevenueFields(revenue, row);
                    fillInsertDefaults(row);
                    backfillParentDepartmentIdIfMissing(row);
                    save(row);
                    inserted++;
                    if (dateStr != null) {
                        existingByDeptDay.put(deptDayKey, row);
                    }
                }
            } catch (Exception e) {
                errors++;
                errorMessages.add("处理日期 " + revenue.getGbAiDailyRevenueRecordDate() + " 的数据时出错: " + e.getMessage());
                log.warn("merge supplement row failed date={}", revenue.getGbAiDailyRevenueRecordDate(), e);
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

    private static void copySupplementMutableDailyRevenueFields(GbAiDailyRevenueEntity from, GbAiDailyRevenueEntity to) {
        to.setGbAiDailyRevenueDineInOrders(from.getGbAiDailyRevenueDineInOrders());
        to.setGbAiDailyRevenueDineInCustomers(from.getGbAiDailyRevenueDineInCustomers());
        to.setGbAiDailyRevenueTakeoutRevenue(from.getGbAiDailyRevenueTakeoutRevenue());
        to.setGbAiDailyRevenueTakeoutOrders(from.getGbAiDailyRevenueTakeoutOrders());
        to.setGbAiDailyRevenuePlatformFee(from.getGbAiDailyRevenuePlatformFee());
        to.setGbAiDailyRevenueWeekday(from.getGbAiDailyRevenueWeekday());
        to.setGbAiDailyRevenueHoliday(from.getGbAiDailyRevenueHoliday());
        to.setGbAiDailyRevenueNotes(from.getGbAiDailyRevenueNotes());
        recomputeGrossRevenue(to);
    }

    @Override
    public List<Long> departmentScopeIdsForParent(Long parentId) {
        return internalDepartmentScopeIdsForParent(parentId);
    }

    @Override
    public Map<Long, List<Integer>> buildStoreRevenueQueryScopeByStoreRoot(List<Integer> storeRootDepartmentIds) {
        LinkedHashMap<Long, List<Integer>> m = new LinkedHashMap<>();
        if (storeRootDepartmentIds == null || storeRootDepartmentIds.isEmpty()) {
            return m;
        }
        for (Integer root : storeRootDepartmentIds) {
            if (root == null || root <= 0) {
                continue;
            }
            List<Integer> ids = new ArrayList<>();
            for (Long id : internalDepartmentScopeIdsForParent(root.longValue())) {
                if (id == null || id <= 0) {
                    continue;
                }
                if (id > Integer.MAX_VALUE) {
                    continue;
                }
                ids.add(id.intValue());
            }
            m.put(root.longValue(), ids);
        }
        return m;
    }

    @Override
    public List<Integer> expandStoreRootsToDailyRevenueScopeIds(List<Integer> storeRootDepartmentIds) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        for (List<Integer> part : buildStoreRevenueQueryScopeByStoreRoot(storeRootDepartmentIds).values()) {
            out.addAll(part);
        }
        return new ArrayList<>(out);
    }

    private List<Long> internalDepartmentScopeIdsForParent(Long parentId) {
        List<Long> ids = new ArrayList<>();
        if (parentId == null) {
            return ids;
        }
        ids.add(parentId);
        List<GbDepartmentEntity> subs = departmentService.querySubDepartments(parentId.intValue());
        if (subs != null) {
            for (GbDepartmentEntity s : subs) {
                if (s.getGbDepartmentId() != null) {
                    ids.add(s.getGbDepartmentId().longValue());
                }
            }
        }
        return ids;
    }

    /**
     * 同一父部门下按自然日汇总多行（父行堂食 + 子部门外卖等），用于列表/曲线图展示。
     */
    private List<GbAiDailyRevenueEntity> aggregateDailyRevenueByDateForParentView(
            List<GbAiDailyRevenueEntity> raw,
            Long parentDepartmentId) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        Map<String, List<GbAiDailyRevenueEntity>> byDay = new LinkedHashMap<>();
        for (GbAiDailyRevenueEntity r : raw) {
            String dk = GbDateTimeUtils.formatDay(r.getGbAiDailyRevenueRecordDate());
            if (dk == null) {
                continue;
            }
            byDay.computeIfAbsent(dk, k -> new ArrayList<>()).add(r);
        }
        List<GbAiDailyRevenueEntity> out = new ArrayList<>();
        for (Map.Entry<String, List<GbAiDailyRevenueEntity>> e : byDay.entrySet()) {
            out.add(mergeRevenueRowsForSameDay(e.getValue(), parentDepartmentId));
        }
        return out;
    }

    /**
     * 合并同一自然日的多行日营收（查询父+子后汇总）；用于 getDailyFoodSalesAndRevenue 与列表聚合。
     */
    public static GbAiDailyRevenueEntity mergeRevenueRowsForSameDay(List<GbAiDailyRevenueEntity> rows,
            Long canonicalDepartmentId) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        if (rows.size() == 1) {
            return rows.get(0);
        }
        GbAiDailyRevenueEntity m = new GbAiDailyRevenueEntity();
        GbAiDailyRevenueEntity first = rows.get(0);
        m.setGbAiDailyRevenueRecordDate(first.getGbAiDailyRevenueRecordDate());
        m.setGbAiDailyRevenueDepartmentId(canonicalDepartmentId);
        m.setGbAiDailyRevenueWeekday(first.getGbAiDailyRevenueWeekday());
        m.setGbAiDailyRevenueHoliday(first.getGbAiDailyRevenueHoliday() != null ? first.getGbAiDailyRevenueHoliday() : "");

        BigDecimal dineIn = BigDecimal.ZERO;
        BigDecimal takeout = BigDecimal.ZERO;
        BigDecimal platform = BigDecimal.ZERO;
        int orders = 0;
        int cust = 0;
        int tOrders = 0;
        List<String> noteParts = new ArrayList<>();
        Long disId = null;
        Long firstId = null;
        for (GbAiDailyRevenueEntity r : rows) {
            if (r.getGbAiDailyRevenueId() != null) {
                firstId = firstId == null ? r.getGbAiDailyRevenueId() : firstId;
            }
            if (r.getGbAiDailyRevenueDistributerId() != null) {
                disId = r.getGbAiDailyRevenueDistributerId();
            }
            if (r.getGbAiDailyRevenueDineInRevenue() != null) {
                dineIn = dineIn.add(r.getGbAiDailyRevenueDineInRevenue());
            }
            if (r.getGbAiDailyRevenueTakeoutRevenue() != null) {
                takeout = takeout.add(r.getGbAiDailyRevenueTakeoutRevenue());
            }
            if (r.getGbAiDailyRevenuePlatformFee() != null) {
                platform = platform.add(r.getGbAiDailyRevenuePlatformFee());
            }
            if (r.getGbAiDailyRevenueDineInOrders() != null) {
                orders += r.getGbAiDailyRevenueDineInOrders();
            }
            if (r.getGbAiDailyRevenueDineInCustomers() != null) {
                cust += r.getGbAiDailyRevenueDineInCustomers();
            }
            if (r.getGbAiDailyRevenueTakeoutOrders() != null) {
                tOrders += r.getGbAiDailyRevenueTakeoutOrders();
            }
            if (r.getGbAiDailyRevenueNotes() != null && !r.getGbAiDailyRevenueNotes().trim().isEmpty()) {
                noteParts.add(r.getGbAiDailyRevenueNotes().trim());
            }
        }
        m.setGbAiDailyRevenueId(firstId);
        m.setGbAiDailyRevenueDistributerId(disId);
        m.setGbAiDailyRevenueDineInRevenue(dineIn);
        m.setGbAiDailyRevenueTakeoutRevenue(takeout);
        m.setGbAiDailyRevenuePlatformFee(platform);
        m.setGbAiDailyRevenueDineInOrders(orders);
        m.setGbAiDailyRevenueDineInCustomers(cust);
        m.setGbAiDailyRevenueTakeoutOrders(tOrders);
        m.setGbAiDailyRevenueNotes(String.join("；", noteParts));
        m.setGbAiDailyRevenueGrossRevenue(dineIn.add(takeout));
        return m;
    }
}
