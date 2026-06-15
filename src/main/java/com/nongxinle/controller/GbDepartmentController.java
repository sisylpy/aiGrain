package com.nongxinle.controller;

import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.*;
import static com.nongxinle.utils.PinYin4jUtils.getHeadStringByString;

/**
 * 订货部门Controller
 */
@RestController
@RequestMapping("gbdepartment")
public class GbDepartmentController {

    @Autowired
    private GbDepartmentService gbDepartmentService;
    @Autowired
    private GbDepartmentUserService gbDepartmentUserService;
    @Autowired
    private GbDistributerService gbDistributerService;
    @Autowired
    private GbDepartmentOrdersService gbDepartmentOrdersService;
    @Autowired
    private GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    @Autowired
    private GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;




    @RequestMapping(value = "/saveGbDepartment", method = RequestMethod.POST)
    @ResponseBody
    public R saveGbDepartment(@RequestBody GbDepartmentEntity department) {

        String gbDepartmentName = department.getGbDepartmentName();
        String headPinyin = getHeadStringByString(gbDepartmentName, false, null);
        department.setGbDepartmentNamePy(headPinyin);
        department.setGbDepartmentPrintSet(0);
        department.setGbDepartmentLevel(0);
        Integer gbDepartmentDisId = department.getGbDepartmentDisId();
        GbDistributerEntity gbDistributerEntity1 = gbDistributerService.getById(gbDepartmentDisId);
        department.setGbDepartmentPrintName(gbDistributerEntity1.getGbDistributerPrintName());
            if (department.getCankaoDepId() > 0) {
                gbDepartmentService.saveNewDepartmentGbWithDepGoods(department, department.getCankaoDepId());
            } else {
                gbDepartmentService.saveNewDepartmentGb(department);
            }

        GbDistributerEntity gbDistributerEntity = gbDistributerService.queryDistributerWithAllDepartments(department.getGbDepartmentDisId());
        return R.ok().put("data", gbDistributerEntity);
    }



    @RequestMapping(value = "/updateDisContent", method = RequestMethod.POST)
    @ResponseBody
    public R updateDisContent (@RequestBody GbDistributerEntity dis) {
        gbDistributerService.updateById(dis);
//        Integer distributerId = dis.getGbDistributerId();
//        Map<String, Object> map = new HashMap<>();
//        map.put("disId", distributerId);
//        System.out.println("dpepepepe" + map);
//        List<GbDepartmentEntity> gbDepartmentEntityList = gbDepartmentService.queryGroupDepsByDisId(map);
//        if(gbDepartmentEntityList.size() > 0){
//            for(GbDepartmentEntity gbDepartmentEntity: gbDepartmentEntityList){
//                Integer gbDepartmentType = gbDepartmentEntity.getGbDepartmentType();
//                if(gbDepartmentType.equals(getGbDepartmentTypeMendian())){
//                    String gbDepartmentName = dis.getGbDistributerName();
//                    gbDepartmentEntity.setGbDepartmentName(dis.getGbDistributerName());
//                    gbDepartmentEntity.setGbDepartmentAttrName(dis.getGbDistributerName());
//                    String headPinyin = getHeadStringByString(gbDepartmentName, false, null);
//                    gbDepartmentEntity.setGbDepartmentNamePy(headPinyin);
//                    gbDepartmentService.update(gbDepartmentEntity);
//                }
//                if(gbDepartmentType.equals(getGbDepartmentTypeJicai())){
//                    String gbDepartmentName = dis.getGbDistributerName()+"集采部";
//                    gbDepartmentEntity.setGbDepartmentName(dis.getGbDistributerName());
//                    gbDepartmentEntity.setGbDepartmentAttrName(dis.getGbDistributerName());
//                    String headPinyin = getHeadStringByString(gbDepartmentName, false, null);
//                    gbDepartmentEntity.setGbDepartmentNamePy(headPinyin);
//                    gbDepartmentService.update(gbDepartmentEntity);
//                }if(gbDepartmentType.equals(getGbDepartmentTypeAppSupplier())){
//                    String gbDepartmentName = dis.getGbDistributerName()+"配送部";
//                    gbDepartmentEntity.setGbDepartmentName(dis.getGbDistributerName());
//                    gbDepartmentEntity.setGbDepartmentAttrName(dis.getGbDistributerName());
//                    String headPinyin = getHeadStringByString(gbDepartmentName, false, null);
//                    gbDepartmentEntity.setGbDepartmentNamePy(headPinyin);
//                    gbDepartmentService.update(gbDepartmentEntity);
//                }
//            }
//        }

        return R.ok();
    }


    @RequestMapping(value = "/saveSubDepartment", method = RequestMethod.POST)
    @ResponseBody
    public R saveSubDepartment(@RequestBody GbDepartmentEntity subDeps) {

        Integer gbDepartmentFatherId = subDeps.getGbDepartmentFatherId();
        GbDepartmentEntity departmentEntity = gbDepartmentService.getById(gbDepartmentFatherId);

        subDeps.setGbDepartmentSettleFullTime(formatFullTime());
        subDeps.setGbDepartmentSettleDate(formatWhatDay(0));
        subDeps.setGbDepartmentSettleMonth(formatWhatMonth(0));
        subDeps.setGbDepartmentSettleWeek(getWeekOfYear(0).toString());
        subDeps.setGbDepartmentSettleYear(formatWhatYear(0));
        subDeps.setGbDepartmentSettleTimes("0");
        subDeps.setGbDepartmentSubAmount(0);
        subDeps.setGbDepartmentIsGroupDep(0);
        subDeps.setGbDepartmentAttrName(subDeps.getGbDepartmentName());
        Integer gbDepartmentDisId = departmentEntity.getGbDepartmentDisId();
        GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(gbDepartmentDisId);
        subDeps.setGbDepartmentPrintName(gbDistributerEntity.getGbDistributerPrintName());
        String gbDepartmentName = subDeps.getGbDepartmentName();
        String headPinyin = getHeadStringByString(gbDepartmentName, false, null);
        subDeps.setGbDepartmentNamePy(headPinyin);
        subDeps.setGbDepartmentType(departmentEntity.getGbDepartmentType());
        subDeps.setGbDepartmentDisId(departmentEntity.getGbDepartmentDisId());
        subDeps.setGbDepartmentFatherId(departmentEntity.getGbDepartmentId());
        subDeps.setGbDepartmentDepSettleId(-1);
        subDeps.setGbDepartmentLevel(1);
        gbDepartmentService.save(subDeps);

        departmentEntity.setGbDepartmentSubAmount(departmentEntity.getGbDepartmentSubAmount() + 1);
        gbDepartmentService.updateById(departmentEntity);
        List<GbDepartmentEntity> subDepartments = gbDepartmentService.querySubDepartments(gbDepartmentFatherId);
        departmentEntity.setGbDepartmentEntityList(subDepartments);
        Map<String, Object> result = new HashMap<>();
        result.put("mendianInfo", departmentEntity);
        result.put("disInfo", gbDistributerService.queryDistributerWithAllDepartments(departmentEntity.getGbDepartmentDisId()));
        return R.ok().put("data", result);

    }

    @RequestMapping(value = "/getSubDepartmentsGb/{depId}")
    @ResponseBody
    public R getSubDepartmentsGb(@PathVariable Integer depId) {
        System.out.println(depId);
        List<GbDepartmentEntity> departmentEntities = gbDepartmentService.querySubDepartments(depId);

        return R.ok().put("data", departmentEntities);
    }

    /**
     * 门店综合查询：查询指定部门下的用户、子部门、以及子部门下的用户
     */
    @RequestMapping(value = "/getStoreDetailGb/{depId}", method = RequestMethod.GET)
    @ResponseBody
    public R getStoreDetailGb(@PathVariable Integer depId) {
        // 1. 该ID（部门）下的用户
        List<GbDepartmentUserEntity> users = gbDepartmentUserService.queryAllUsersByDepId(depId);

        // 2. 该ID（部门）下面的子部门
        List<GbDepartmentEntity> subDepartments = gbDepartmentService.querySubDepartments(depId);

        // 3. 每个子部门的用户
        for (GbDepartmentEntity subDept : subDepartments) {
            List<GbDepartmentUserEntity> subUsers = gbDepartmentUserService.queryAllUsersByDepId(subDept.getGbDepartmentId());
            subDept.setGbDepartmentUserEntities(subUsers);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("users", users);
        result.put("subDepartments", subDepartments);

        return R.ok().put("data", result);
    }



    @RequestMapping(value = "/purUserSaveMendain", method = RequestMethod.POST)
    @ResponseBody
    public R purUserSaveMendain(@RequestBody GbDepartmentEntity depart) {
        depart.setGbDepartmentSubAmount(depart.getGbDepartmentEntityList() != null ? depart.getGbDepartmentEntityList().size() : 0);
        String gbDepartmentName = depart.getGbDepartmentName();
        String headPinyin = getHeadStringByString(gbDepartmentName, false, null);
        depart.setGbDepartmentNamePy(headPinyin);
        depart.setGbDepartmentAttrName(depart.getGbDepartmentName());
        depart.setGbDepartmentPrintName("ApplyHalfPanel");
        depart.setGbDepartmentPrintSet(0);
        GbDepartmentEntity departmentEntity = gbDepartmentService.saveNewDepartmentGb(depart);
        Integer gbDepartmentDisId = departmentEntity.getGbDepartmentDisId();
        // 保存门店后只需要基础信息，不需要查询所有部门（性能优化）
        GbDistributerEntity gbDistributerEntity = gbDistributerService.queryDistributerWithAllDepartments(gbDepartmentDisId);

        return R.ok().put("data", gbDistributerEntity);
    }

    @RequestMapping(value = "/getDisDepartmentGbMendianJing/{disId}")
    @ResponseBody
    public R getDisDepartmentGbMendianJing(@PathVariable Integer disId) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("depType", GbConstants.DepartmentType.STORE);
        List<GbDepartmentEntity> gbDepartmentEntities = gbDepartmentService.queryGroupDepsByDisId(map);

        return R.ok().put("data", gbDepartmentEntities);
    }

    @RequestMapping(value = "/getDepInfoGb/{depId}")
    @ResponseBody
    public R getDepInfoGb(@PathVariable Integer depId) {
        System.out.println(depId + "idiid");
        GbDepartmentEntity gbDepartmentEntity = gbDepartmentService.getById(depId);
        return R.ok().put("data", gbDepartmentEntity);
    }

    @RequestMapping(value = "/updateGroupNameGb", method = RequestMethod.POST)
    @ResponseBody
    public R updateGroupNameGb(@RequestBody GbDepartmentEntity departmentEntity) {
        System.out.println("ishere" + departmentEntity);
        departmentEntity.setGbDepartmentAttrName(departmentEntity.getGbDepartmentName());
        departmentEntity.setGbDepartmentPrintName("ApplyHalfPanel");
        String gbDepartmentName = departmentEntity.getGbDepartmentName();
        String headPinyin = getHeadStringByString(gbDepartmentName, false, null);
        departmentEntity.setGbDepartmentNamePy(headPinyin);
        gbDepartmentService.updateById(departmentEntity);

        Map<String, Object> result = new HashMap<>();
        result.put("mendianInfo", departmentEntity);
        result.put("disInfo", gbDistributerService.queryDistributerWithAllDepartments(departmentEntity.getGbDepartmentDisId()));
        return R.ok().put("data", result);
    }

    @RequestMapping(value = "/deleteDepartment/{depId}")
    @ResponseBody
    public R deleteDepartment(@PathVariable Integer depId) {
        List<GbDepartmentUserEntity> gbDepartmentUserEntities = gbDepartmentUserService.queryAllUsersByDepId(depId);
        System.out.println("depusueureeee" + gbDepartmentUserEntities);
        Map<String, Object> map = new HashMap<>();
        map.put("depId", depId);
        System.out.println("mapapa" + map);

        GbDepartmentEntity departmentEntity = gbDepartmentService.getById(depId);
        List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersListByParams(map);
        List<GbDepartmentGoodsStockEntity> departmentGoodsStockEntities = gbDepartmentGoodsStockService.queryGoodsStockByParams(map);
        if (gbDepartmentUserEntities.size() > 0 || ordersEntities.size() > 0 || departmentGoodsStockEntities.size() > 0) {
            return R.error(-1, "有部门相关数据，暂无法删除。");
        } else {
            List<GbDepartmentDisGoodsEntity> departmentDisGoodsEntities = gbDepartmentDisGoodsService.queryGbDepDisGoodsByParams(map);
            if (departmentDisGoodsEntities.size() > 0) {
                for (GbDepartmentDisGoodsEntity departmentDisGoodsEntity : departmentDisGoodsEntities) {
                    gbDepartmentDisGoodsService.removeById(departmentDisGoodsEntity.getGbDepartmentDisGoodsId());
                }
            }

            Integer gbDepartmentFatherId = departmentEntity.getGbDepartmentFatherId();
            GbDepartmentEntity fatherDep = gbDepartmentService.getById(gbDepartmentFatherId);
            fatherDep.setGbDepartmentSubAmount(fatherDep.getGbDepartmentSubAmount() - 1);
            gbDepartmentService.updateById(fatherDep);

            Integer gbDepartmentDisId = departmentEntity.getGbDepartmentDisId();
            GbDistributerEntity gbDistributerEntity = gbDistributerService.queryDistributerInfo(gbDepartmentDisId);
            gbDepartmentService.removeById(depId);

            return R.ok().put("data", gbDistributerEntity);
        }
    }




    /**
     * 获取部门用户列表（带采购统计）
     */
    @RequestMapping(value = "/getDepUsersByFatherIdGb", method = RequestMethod.POST)
    @ResponseBody
    public R getDepUsersByFatherIdGb(String startDate, String stopDate, Integer depId) {
        Map<String, Object> map = new HashMap<>();
        map.put("startDate", startDate);
        map.put("stopDate", stopDate);
        map.put("purDepId", depId);
        System.out.println("mapapUUUUUUUU" + map);

        List<GbDepartmentUserEntity> userEntities = gbDepartmentUserService.queryAllUsersByDepId(depId);
        GbDepartmentEntity dep = gbDepartmentService.getById(depId);
        Integer depDisId = dep != null ? dep.getGbDepartmentDisId() : null;

        for (GbDepartmentUserEntity user : userEntities) {
            Integer disId = user.getGbDuDistributerId() != null ? user.getGbDuDistributerId() : depDisId;
            Map<String, Object> base = new HashMap<>();
            base.put("disId", disId);
            base.put("purDepId", depId);
            base.put("purUserId", user.getGbDepartmentUserId());
            base.put("startDate", startDate);
            base.put("stopDate", stopDate);
            base.put("dayuStatus", 2);
            base.put("supplierBuy", -1);
            base.put("useStockFinishDate", true);

            Map<String, Object> billMap = new HashMap<>(base);
            billMap.put("typeNotEqual", 9);
            double bill = toSubtotalDouble(gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(billMap));

            Map<String, Object> returnMap = new HashMap<>(base);
            returnMap.put("purchaseType", 9);
            double ret = toSubtotalDouble(gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(returnMap));

            Map<String, Object> itemData = new HashMap<>();
            itemData.put("billTotal", String.format("%.1f", bill));
            itemData.put("returnPayTotal", String.format("%.1f", ret));
            user.setItemData(itemData);
        }

        return R.ok().put("data", userEntities);
    }

    private static double toSubtotalDouble(Double v) {
        return v != null ? v : 0.0;
    }

}
