package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.*;
import com.nongxinle.mapper.GbDistributerMapper;
import com.nongxinle.service.*;
import com.nongxinle.utils.DateUtils;
import com.nongxinle.utils.GbTypeUtils;
import com.nongxinle.utils.PinYin4jUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GbDistributerServiceImpl extends ServiceImpl<GbDistributerMapper, GbDistributerEntity> implements GbDistributerService {

    @Autowired
    private GbDepartmentService gbDepartmentService;
    @Autowired
    private GbDepartmentUserService gbDepartmentUserService;
    @Autowired
    private GbDistributerModuleService gbDistributerModuleService;
    @Autowired
    private GbDistributerFatherGoodsService dgfService;
    @Autowired
    private GbAiRestaurantProfileService gbAiRestaurantProfileService;

    @Override
    public GbDistributerEntity queryDistributerBaseInfo(Integer disId) {

        GbDistributerEntity distributer = getById(disId);
        // 查询门店部门 (type=1)
        LambdaQueryWrapper<GbDepartmentEntity> mendianWrapper = new LambdaQueryWrapper<>();
        mendianWrapper.eq(GbDepartmentEntity::getGbDepartmentDisId, disId)
                .eq(GbDepartmentEntity::getGbDepartmentType, GbTypeUtils.GB_DEPARTMENT_TYPE_MENDIAN)
                .eq(GbDepartmentEntity::getGbDepartmentIsGroupDep, 1);
        List<GbDepartmentEntity> mendianList = gbDepartmentService.list(mendianWrapper);
        distributer.setMendianDepartmentList(mendianList);
        return distributer;
    }

    @Override
    public GbDistributerEntity queryDistributerWithAllDepartments(Integer disId) {
        GbDistributerEntity distributer = getById(disId);
        if (distributer == null) {
            return null;
        }

        // 查询门店部门 (type=1)
        LambdaQueryWrapper<GbDepartmentEntity> mendianWrapper = new LambdaQueryWrapper<>();
        mendianWrapper.eq(GbDepartmentEntity::getGbDepartmentDisId, disId)
                .eq(GbDepartmentEntity::getGbDepartmentType, GbTypeUtils.GB_DEPARTMENT_TYPE_MENDIAN)
                .eq(GbDepartmentEntity::getGbDepartmentIsGroupDep, 1);
        List<GbDepartmentEntity> mendianList = gbDepartmentService.list(mendianWrapper);
        distributer.setMendianDepartmentList(mendianList);

        // 查询集采部门 (type=2)
        LambdaQueryWrapper<GbDepartmentEntity> purWrapper = new LambdaQueryWrapper<>();
        purWrapper.eq(GbDepartmentEntity::getGbDepartmentDisId, disId)
                .eq(GbDepartmentEntity::getGbDepartmentType, GbTypeUtils.GB_DEPARTMENT_TYPE_JICAI);
        List<GbDepartmentEntity> purList = gbDepartmentService.list(purWrapper);
        distributer.setPurDepartmentList(purList);

        // 查询库房部门 (type=3)
        LambdaQueryWrapper<GbDepartmentEntity> stockWrapper = new LambdaQueryWrapper<>();
        stockWrapper.eq(GbDepartmentEntity::getGbDepartmentDisId, disId)
                .eq(GbDepartmentEntity::getGbDepartmentType, GbTypeUtils.GB_DEPARTMENT_TYPE_KUFANG);
        List<GbDepartmentEntity> stockList = gbDepartmentService.list(stockWrapper);
        distributer.setStockDepartmentList(stockList);

        // 查询厨房部门 (type=4)
        LambdaQueryWrapper<GbDepartmentEntity> kitchenWrapper = new LambdaQueryWrapper<>();
        kitchenWrapper.eq(GbDepartmentEntity::getGbDepartmentDisId, disId)
                .eq(GbDepartmentEntity::getGbDepartmentType, GbTypeUtils.GB_DEPARTMENT_TYPE_KITCHEN);
        List<GbDepartmentEntity> kitchenList = gbDepartmentService.list(kitchenWrapper);
        distributer.setKitchenDepartmentList(kitchenList);

        // 查询加盟部门 (type=11)
        LambdaQueryWrapper<GbDepartmentEntity> franchWrapper = new LambdaQueryWrapper<>();
        franchWrapper.eq(GbDepartmentEntity::getGbDepartmentDisId, disId)
                .eq(GbDepartmentEntity::getGbDepartmentType, GbTypeUtils.GB_DEPARTMENT_TYPE_JIAMENG);
        List<GbDepartmentEntity> franchList = gbDepartmentService.list(franchWrapper);
        distributer.setFranchiseeDepartmentList(franchList);

        // 查询配送商部门 (type=5) - 单个
        LambdaQueryWrapper<GbDepartmentEntity> appSuppWrapper = new LambdaQueryWrapper<>();
        appSuppWrapper.eq(GbDepartmentEntity::getGbDepartmentDisId, disId)
                .eq(GbDepartmentEntity::getGbDepartmentType, GbTypeUtils.GB_DEPARTMENT_TYPE_APP_SUPPLIER).last("LIMIT 1");
        GbDepartmentEntity appSupp = gbDepartmentService.getOne(appSuppWrapper);
        distributer.setAppSupplierDepartment(appSupp);

        return distributer;
    }

    @Override
    public Integer saveSingleMendianDistributerGb(GbDistributerEntity gbDistributerEntity) {
        //1.保存distributer
        gbDistributerEntity.setGbDistributerSettleDate(DateUtils.formatWhatDay(0));
        gbDistributerEntity.setGbDistributerSettleFullTime(DateUtils.formatFullTime());
        gbDistributerEntity.setGbDistributerSettleMonth(DateUtils.formatWhatMonth(0));
        gbDistributerEntity.setGbDistributerSettleWeek(DateUtils.getWeekOfYear(0).toString());
        gbDistributerEntity.setGbDistributerSettleYear(DateUtils.formatWhatYear(0));
        gbDistributerEntity.setGbDistributerManager("09:00");
        gbDistributerEntity.setGbDistributerSettleTimes("0");
        gbDistributerEntity.setGbDistributerBuyQuantity("10");
        save(gbDistributerEntity);

        //模块
        GbDistributerModuleEntity gbDistributerModuleEntity = new GbDistributerModuleEntity();
        gbDistributerModuleEntity.setGbDmPurchaseNumber(-1);
        gbDistributerModuleEntity.setGbDmDirectSalesNumber(0);
        gbDistributerModuleEntity.setGbDmAppSupplierNumber(-1);
        gbDistributerModuleEntity.setGbDmCentralKitchenNumber(-1);
        gbDistributerModuleEntity.setGbDmStockNumber(-1);
        gbDistributerModuleEntity.setGbDmFixedSupplierNumber(-1);
        gbDistributerModuleEntity.setGbDmFranchiseeNumber(-1);
        gbDistributerModuleEntity.setGbDmDistributerId(gbDistributerEntity.getGbDistributerId());
        gbDistributerModuleService.save(gbDistributerModuleEntity);

        System.out.println("bucbaocuun");

        //保存门店模块
        saveDepartmentSingleMendian(gbDistributerEntity, GbTypeUtils.GB_DEPARTMENT_TYPE_MENDIAN);
//        saveDepartment(gbDistributerEntity, "配送部门", GbTypeUtils.GB_DEPARTMENT_TYPE_APP_SUPPLIER);

        //保存临时商品
        saveLinshiFatherGoods(gbDistributerEntity);

        return gbDistributerEntity.getGbDistributerId();
    }

    @Override
    public GbDistributerEntity queryDistributerInfo(Integer gbDepartmentDisId) {
        return queryDistributerWithAllDepartments(gbDepartmentDisId);
    }

    private void saveDepartmentSingleMendian(GbDistributerEntity gbDistributerEntity, Integer type) {
        GbDepartmentEntity departmentEntity = new GbDepartmentEntity();
        departmentEntity.setGbDepartmentDisId(gbDistributerEntity.getGbDistributerId());
        departmentEntity.setGbDepartmentFatherId(0);
        departmentEntity.setGbDepartmentType(type);
        departmentEntity.setGbDepartmentSettleFullTime(DateUtils.formatFullTime());
        departmentEntity.setGbDepartmentSettleDate(DateUtils.formatWhatDay(0));
        departmentEntity.setGbDepartmentSettleMonth(DateUtils.formatWhatMonth(0));
        departmentEntity.setGbDepartmentSettleWeek(DateUtils.getWeekOfYear(0).toString());
        departmentEntity.setGbDepartmentSettleYear(DateUtils.formatWhatYear(0));
        departmentEntity.setGbDepartmentSettleTimes("0");
        departmentEntity.setGbDepartmentSubAmount(0);
        departmentEntity.setGbDepartmentIsGroupDep(1);
        departmentEntity.setGbDepartmentType(type);
        departmentEntity.setGbDepartmentAttrName(gbDistributerEntity.getGbDistributerName());
        departmentEntity.setGbDepartmentName(gbDistributerEntity.getGbDistributerName() );
        departmentEntity.setGbDepartmentPrintSet(0);
        String gbDepartmentName = departmentEntity.getGbDepartmentName();
        String headPinyin = PinYin4jUtils.getHeadStringByString(gbDepartmentName, false, null);
        departmentEntity.setGbDepartmentNamePy(headPinyin);
        gbDepartmentService.save(departmentEntity);

        GbDepartmentUserEntity gbDepartmentUserEntity = gbDistributerEntity.getSingleDepartmentUser();
        gbDepartmentUserEntity.setGbDuDepartmentFatherId(departmentEntity.getGbDepartmentId());
        gbDepartmentUserEntity.setGbDuDepartmentId(departmentEntity.getGbDepartmentId());
        gbDepartmentUserEntity.setGbDuLoginTimes(0);
        gbDepartmentUserEntity.setGbDuWxPhone(gbDistributerEntity.getGbDistributerPhone());
        gbDepartmentUserEntity.setGbDuDistributerId(gbDistributerEntity.getGbDistributerId());
        gbDepartmentUserEntity.setGbDuAdmin(2);
        gbDepartmentUserEntity.setGbDuUrlChange(1);
        gbDepartmentUserEntity.setGbDuJoinDate(DateUtils.formatWhatDay(0));
        gbDepartmentUserEntity.setGbDuPrintBillDeviceId("-1");
        gbDepartmentUserEntity.setGbDuPrintDeviceId("-1");
        gbDepartmentUserService.save(gbDepartmentUserEntity);

        // 自动创建部门AI画像
        GbAiRestaurantProfileEntity profile = new GbAiRestaurantProfileEntity();
        profile.setGbAiRestaurantProfileDepartmentId(Long.valueOf(departmentEntity.getGbDepartmentId()));
        profile.setGbAiRestaurantProfileDistributerId(Long.valueOf(gbDistributerEntity.getGbDistributerId()));
        profile.setGbAiRestaurantProfileRestaurantName(departmentEntity.getGbDepartmentName());
        profile.setGbAiRestaurantProfileAddress(gbDistributerEntity.getGbDistributerAddress());
        gbAiRestaurantProfileService.save(profile);


        saveSubDepartment(gbDistributerEntity, departmentEntity.getGbDepartmentId(), type);
    }

    private void saveSubDepartment(GbDistributerEntity gbDistributerEntity, Integer fatherId, Integer type) {
        GbDepartmentEntity departmentEntity = new GbDepartmentEntity();
        departmentEntity.setGbDepartmentDisId(gbDistributerEntity.getGbDistributerId());
        departmentEntity.setGbDepartmentFatherId(fatherId);
        departmentEntity.setGbDepartmentType(type);
        departmentEntity.setGbDepartmentSettleFullTime(DateUtils.formatFullTime());
        departmentEntity.setGbDepartmentSettleDate(DateUtils.formatWhatDay(0));
        departmentEntity.setGbDepartmentSettleMonth(DateUtils.formatWhatMonth(0));
        departmentEntity.setGbDepartmentSettleWeek(DateUtils.getWeekOfYear(0).toString());
        departmentEntity.setGbDepartmentSettleYear(DateUtils.formatWhatYear(0));
        departmentEntity.setGbDepartmentSettleTimes("0");
        departmentEntity.setGbDepartmentSubAmount(0);
        departmentEntity.setGbDepartmentIsGroupDep(0);
        departmentEntity.setGbDepartmentAttrName(gbDistributerEntity.getGbDistributerName());
        departmentEntity.setGbDepartmentName(gbDistributerEntity.getGbDistributerName() + "部门一");
        departmentEntity.setGbDepartmentPrintSet(0);
        String gbDepartmentName = departmentEntity.getGbDepartmentName();
        String headPinyin = PinYin4jUtils.getHeadStringByString(gbDepartmentName, false, null);
        departmentEntity.setGbDepartmentNamePy(headPinyin);
        gbDepartmentService.save(departmentEntity);
    }

    private void saveLinshiFatherGoods(GbDistributerEntity gbDistributerEntity) {
        Integer nxDistributerId = gbDistributerEntity.getGbDistributerId();

        GbDistributerFatherGoodsEntity greatGrand = new GbDistributerFatherGoodsEntity();
        greatGrand.setGbDfgDistributerId(nxDistributerId);
        greatGrand.setGbDfgFatherGoodsLevel(0);
        greatGrand.setGbDfgFatherGoodsImg("goodsImage/logo.jpg");
        greatGrand.setGbDfgFatherGoodsName("临时添加");
        greatGrand.setGbDfgFatherGoodsColor("#757575");
        greatGrand.setGbDfgFathersFatherId(0);
        dgfService.save(greatGrand);

        GbDistributerFatherGoodsEntity grand = new GbDistributerFatherGoodsEntity();
        grand.setGbDfgDistributerId(nxDistributerId);
        grand.setGbDfgFatherGoodsLevel(1);
        grand.setGbDfgFatherGoodsImg("goodsImage/logo.jpg");
        grand.setGbDfgFatherGoodsName("临时添加");
        grand.setGbDfgGoodsAmount(0);
        grand.setGbDfgFatherGoodsColor("#757575");
        grand.setGbDfgFathersFatherId(greatGrand.getGbDistributerFatherGoodsId());
        dgfService.save(grand);

        GbDistributerFatherGoodsEntity father = new GbDistributerFatherGoodsEntity();
        father.setGbDfgDistributerId(nxDistributerId);
        father.setGbDfgFatherGoodsLevel(2);
        father.setGbDfgFatherGoodsImg("goodsImage/logo.jpg");
        father.setGbDfgFatherGoodsName("临时添加");
        father.setGbDfgGoodsAmount(0);
        father.setGbDfgFatherGoodsColor("#757575");
        father.setGbDfgFathersFatherId(grand.getGbDistributerFatherGoodsId());
        dgfService.save(father);
    }
}
