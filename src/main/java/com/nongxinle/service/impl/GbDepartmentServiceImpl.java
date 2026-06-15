package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepFoodEntity;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nongxinle.utils.DateUtils.*;
import static com.nongxinle.utils.PinYin4jUtils.getHeadStringByString;

/**
 * 订货部门Service实现
 */
@Service
public class GbDepartmentServiceImpl extends ServiceImpl<GbDepartmentMapper, GbDepartmentEntity> implements GbDepartmentService {

    @Autowired
    private GbDepartmentDisGoodsService gbDepartmentDisGoodsService;

    @Autowired
    private GbDepFoodService gbDepFoodService;

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

    @Override
    public GbDepartmentEntity queryDepInfo(Map<String, Object> mapDep) {

        return baseMapper.queryDepInfo(mapDep);
    }

    @Override
    public GbDepartmentEntity saveNewDepartmentGbWithDepGoods(GbDepartmentEntity department, Integer cankaoDepFatherId) {
        // 1. 先保存新部门及其自带子部门（复用原有逻辑）
        saveNewDepartmentGb(department);

        // 2. 查询参考父部门的所有子部门
        List<GbDepartmentEntity> refSubDeps = querySubDepartments(cankaoDepFatherId);
        if (refSubDeps == null || refSubDeps.isEmpty()) {
            return department;
        }

        // 3. 遍历参考子部门，拷贝到新部门下并拷贝其部门商品
        for (GbDepartmentEntity refSubDep : refSubDeps) {
            // 3a. 创建新子部门（名称与参考子部门一致）
            GbDepartmentEntity newSubDep = new GbDepartmentEntity();
            newSubDep.setGbDepartmentName(refSubDep.getGbDepartmentName());
            newSubDep.setGbDepartmentFatherId(department.getGbDepartmentId());
            newSubDep.setGbDepartmentType(department.getGbDepartmentType());
            newSubDep.setGbDepartmentDisId(department.getGbDepartmentDisId());
            newSubDep.setGbDepartmentSubAmount(0);
            newSubDep.setGbDepartmentIsGroupDep(0);
            newSubDep.setGbDepartmentAttrName(refSubDep.getGbDepartmentName());
            newSubDep.setGbDepartmentPrintName("ApplyHalfPanel");
            newSubDep.setGbDepartmentNamePy(getHeadStringByString(refSubDep.getGbDepartmentName(), false, null));
            newSubDep.setGbDepartmentDepSettleId(-1);
            newSubDep.setGbDepartmentLevel(1);
            newSubDep.setGbDepartmentSettleFullTime(formatFullTime());
            newSubDep.setGbDepartmentSettleDate(formatWhatDay(0));
            newSubDep.setGbDepartmentSettleMonth(formatWhatMonth(0));
            newSubDep.setGbDepartmentSettleWeek(getWeekOfYear(0).toString());
            newSubDep.setGbDepartmentSettleYear(formatWhatYear(0));
            newSubDep.setGbDepartmentSettleTimes("0");
            save(newSubDep);

            // 3b. 查询参考子部门的部门商品
            Map<String, Object> goodsQuery = new HashMap<>();
            goodsQuery.put("depId", refSubDep.getGbDepartmentId());
            List<GbDepartmentDisGoodsEntity> refGoodsList = gbDepartmentDisGoodsService.queryGbDepDisGoodsByParams(goodsQuery);
            if (refGoodsList == null || refGoodsList.isEmpty()) {
                continue;
            }

            // 3c. 查询新子部门已有的 disGoodsId，避免重复插入
            Map<String, Object> existQuery = new HashMap<>();
            existQuery.put("depId", newSubDep.getGbDepartmentId());
            List<GbDepartmentDisGoodsEntity> existGoodsList = gbDepartmentDisGoodsService.queryGbDepDisGoodsByParams(existQuery);
            Set<Integer> existDisGoodsIds = new HashSet<>();
            if (existGoodsList != null) {
                for (GbDepartmentDisGoodsEntity exist : existGoodsList) {
                    if (exist.getGbDdgDisGoodsId() != null) {
                        existDisGoodsIds.add(exist.getGbDdgDisGoodsId());
                    }
                }
            }

            // 3d. 拷贝部门商品到新子部门（去重）
            for (GbDepartmentDisGoodsEntity refGoods : refGoodsList) {
                Integer disGoodsId = refGoods.getGbDdgDisGoodsId();
                if (disGoodsId != null && existDisGoodsIds.contains(disGoodsId)) {
                    continue; // 已存在，跳过
                }
                GbDepartmentDisGoodsEntity newGoods = new GbDepartmentDisGoodsEntity();
                // 指向新父部门和新子部门
                newGoods.setGbDdgDepartmentFatherId(department.getGbDepartmentId());
                newGoods.setGbDdgDepartmentId(newSubDep.getGbDepartmentId());
                // 拷贝商品关联
                newGoods.setGbDdgDisGoodsId(disGoodsId);
                newGoods.setGbDdgDisGoodsFatherId(refGoods.getGbDdgDisGoodsFatherId());
                newGoods.setGbDdgDisGoodsGrandId(refGoods.getGbDdgDisGoodsGrandId());
                newGoods.setGbDdgDisGoodsGreatId(refGoods.getGbDdgDisGoodsGreatId());
                // 拷贝商品名称信息
                newGoods.setGbDdgDepGoodsName(refGoods.getGbDdgDepGoodsName());
                newGoods.setGbDdgDepGoodsPinyin(refGoods.getGbDdgDepGoodsPinyin());
                newGoods.setGbDdgDepGoodsPy(refGoods.getGbDdgDepGoodsPy());
                newGoods.setGbDdgDepGoodsStandardname(refGoods.getGbDdgDepGoodsStandardname());
                newGoods.setGbDdgDepGoodsDetail(refGoods.getGbDdgDepGoodsDetail());
                newGoods.setGbDdgDepGoodsBrand(refGoods.getGbDdgDepGoodsBrand());
                newGoods.setGbDdgDepGoodsPlace(refGoods.getGbDdgDepGoodsPlace());
                // 拷贝分类与供应商信息
                newGoods.setGbDdgGoodsType(refGoods.getGbDdgGoodsType());
                newGoods.setGbDdgNxDistributerId(refGoods.getGbDdgNxDistributerId());
                newGoods.setGbDdgNxDistributerGoodsId(refGoods.getGbDdgNxDistributerGoodsId());
                newGoods.setGbDdgGbDepartmentId(refGoods.getGbDdgGbDepartmentId());
                newGoods.setGbDdgGbSupplierId(refGoods.getGbDdgGbSupplierId());
                newGoods.setGbDdgGbDisId(refGoods.getGbDdgGbDisId());
                // 库存初始化为零（新店无库存）
                newGoods.setGbDdgStockTotalWeight("0.0");
                newGoods.setGbDdgStockTotalSubtotal("0.0");
                // 拷贝规格与价格配置
                newGoods.setGbDdgShowStandardId(refGoods.getGbDdgShowStandardId());
                newGoods.setGbDdgShowStandardName(refGoods.getGbDdgShowStandardName());
                newGoods.setGbDdgShowStandardWeight(refGoods.getGbDdgShowStandardWeight());
                newGoods.setGbDdgShowStandardScale(refGoods.getGbDdgShowStandardScale());
                newGoods.setGbDdgLevelPrice(refGoods.getGbDdgLevelPrice());
                newGoods.setGbDdgSellingPrice(refGoods.getGbDdgSellingPrice());
                newGoods.setGbDdgOrderPrice(refGoods.getGbDdgOrderPrice());
                newGoods.setGbDdgOrderStandard(refGoods.getGbDdgOrderStandard());
                newGoods.setGbDdgPrintStandard(refGoods.getGbDdgPrintStandard());
                newGoods.setGbDdgPrepareStatus(refGoods.getGbDdgPrepareStatus());
                newGoods.setGbDdgDepGoodsStatus(refGoods.getGbDdgDepGoodsStatus());
                gbDepartmentDisGoodsService.save(newGoods);
                existDisGoodsIds.add(disGoodsId);
            }
        }

        // 4. 复制参考部门的菜品到新部门
        Map<String, Object> foodQuery = new HashMap<>();
        foodQuery.put("depFatherId", cankaoDepFatherId);
        List<GbDepFoodEntity> refFoodList = gbDepFoodService.queryDepFoodByParams(foodQuery);
        if (refFoodList != null && !refFoodList.isEmpty()) {
            for (GbDepFoodEntity refFood : refFoodList) {
                GbDepFoodEntity newFood = new GbDepFoodEntity();
                newFood.setGbDfDepId(department.getGbDepartmentId());
                newFood.setGbDfFoodId(refFood.getGbDfFoodId());
                newFood.setGbDfNxFoodId(refFood.getGbDfNxFoodId());
                newFood.setGbDfFoodName(refFood.getGbDfFoodName());
                newFood.setGbDfFoodPrice(refFood.getGbDfFoodPrice());
                newFood.setGbDfStatus(refFood.getGbDfStatus());
                newFood.setGbDfFoodPinyin(refFood.getGbDfFoodPinyin());
                newFood.setGbDfFoodPy(refFood.getGbDfFoodPy());
                newFood.setGbDfDepFatherId(String.valueOf(department.getGbDepartmentId()));
                newFood.setGbDfFoodFatherId(refFood.getGbDfFoodFatherId());
                newFood.setGbDfFoodImg(refFood.getGbDfFoodImg());
                newFood.setGbDfFoodImgLarge(refFood.getGbDfFoodImgLarge());
                newFood.setGbDfFoodMethod(refFood.getGbDfFoodMethod());
                newFood.setGbDfFoodDetail(refFood.getGbDfFoodDetail());
                newFood.setGbDfGoodsSort(refFood.getGbDfGoodsSort());
                newFood.setGbDfDistributerId(refFood.getGbDfDistributerId());
                gbDepFoodService.save(newFood);
            }
        }

        return department;
    }

}
