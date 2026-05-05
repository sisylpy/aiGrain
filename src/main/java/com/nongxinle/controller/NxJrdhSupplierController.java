package com.nongxinle.controller;

import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.NxJrdhSupplierService;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 今日达供应商Controller
 */
@RestController
@RequestMapping("nxjrdhsupplier")
public class NxJrdhSupplierController {

    @Autowired
    private GbDistributerPurchaseGoodsService purchaseGoodsService;
    @Autowired
    private GbDistributerPurchaseBatchService gbPurBatchService;
    @Autowired
    private NxJrdhSupplierService nxJrdhSupplierService;
    @Autowired
    private GbDistributerGoodsService gbDistributerGoodsService;



    //disGetAllSellers
    @RequestMapping(value = "/gbDisGetAllSuppliers/{disId}")
    @ResponseBody
    public R gbDisGetAllSuppliers(@PathVariable Integer disId) {
        Map<String, Object> map3 = new HashMap<>();
        map3.put("gbDisId", disId);
        System.out.println("map3" + map3);
        List<NxJrdhSupplierEntity> nxJrdhSupplierEntities = nxJrdhSupplierService.queryJrdhSupplerByParams(map3);

        return R.ok().put("data", nxJrdhSupplierEntities);
    }

    @RequestMapping(value = "/updateJrdhSupplier", method = RequestMethod.POST)
    @ResponseBody
    public R updateJrdhSupplier (@RequestBody NxJrdhSupplierEntity supplier) {
        Integer nxJrdhSupplierId = supplier.getNxJrdhSupplierId();
        NxJrdhSupplierEntity byId = nxJrdhSupplierService.getById(nxJrdhSupplierId);
        byId.setNxJrdhsSupplierName(supplier.getNxJrdhsSupplierName());
        nxJrdhSupplierService.updateById(byId);
        return R.ok();
    }



    /**
     * 获取部门的供应商列表
     */
    @RequestMapping(value = "/depGetSupplier", method = RequestMethod.POST)
    @ResponseBody
    public R depGetSupplier(@RequestParam Integer depId, @RequestParam(required = false) String startDate, @RequestParam(required = false) String stopDate) {
        Map<String, Object> map3 = new HashMap<>();
        map3.put("gbDepId", depId);
        List<NxJrdhSupplierEntity> nxJrdhSupplierEntities = purchaseGoodsService.queryDisPurGoodsSupplierList(map3);

        if (nxJrdhSupplierEntities.size() > 0) {
            for (NxJrdhSupplierEntity supplierEntity : nxJrdhSupplierEntities) {
                Map<String, Object> mapS = new HashMap<>();
                mapS.put("supplierId", supplierEntity.getNxJrdhSupplierId());
                mapS.put("startDate", startDate);
                mapS.put("stopDate", stopDate);
                mapS.put("equalStatus", 3);
                mapS.put("notEqualPurchaseType", 9);
                Double unPayOrderDouble = 0.0;
                Double unPayReturn = 0.0;
                Double havePayOrderDouble = 0.0;
                Double havePayReturn = 0.0;



                Integer unPayCount = gbPurBatchService.queryDisPurchaseBatchCount(mapS);
                System.out.println("mappsss" + mapS);
                if (unPayCount > 0) {
                    unPayOrderDouble = gbPurBatchService.querySupplierUnSettleSubtotal(mapS);
                }
                mapS.put("notEqualPurchaseType", null);
                mapS.put("purchaseType", 9);
                Integer unPayTuihuoCount = gbPurBatchService.queryDisPurchaseBatchCount(mapS);
                if (unPayTuihuoCount > 0) {
                    unPayReturn = gbPurBatchService.querySupplierUnSettleSubtotal(mapS);
                }
                mapS.put("equalStatus", 4);
                mapS.put("notEqualPurchaseType", 9);
                mapS.put("purchaseType", null);
                System.out.println("mappsss" + mapS);
                Integer havePayCount = gbPurBatchService.queryDisPurchaseBatchCount(mapS);
                if (havePayCount > 0) {
                    havePayOrderDouble = gbPurBatchService.querySupplierUnSettleSubtotal(mapS);
                }
                mapS.put("notEqualPurchaseType", null);
                mapS.put("purchaseType", 9);
                Integer havePayTuihuoCount = gbPurBatchService.queryDisPurchaseBatchCount(mapS);
                if (havePayTuihuoCount > 0) {
                    havePayReturn = gbPurBatchService.querySupplierUnSettleSubtotal(mapS);
                }

                int billCount = unPayCount + havePayCount;
                double billTotal = unPayOrderDouble + havePayOrderDouble;
                int havePayCountTotal = havePayCount + havePayTuihuoCount;
                double havePayTotl = havePayOrderDouble - havePayReturn;
                double actPayTotal = unPayOrderDouble - unPayReturn;
                int actPayCountTotal = unPayCount + unPayTuihuoCount;

                Map<String, Object> mapDataOne = new HashMap<>();
                mapDataOne.put("billCount", billCount);
                mapDataOne.put("billTotal", String.format("%.1f", billTotal));
                mapDataOne.put("unPayCount", unPayCount);
                mapDataOne.put("unPayTotal", String.format("%.1f", unPayOrderDouble));
                mapDataOne.put("havePayCount", havePayCountTotal);
                mapDataOne.put("havePayTotal", String.format("%.1f", havePayTotl));
                mapDataOne.put("returnBillCount", unPayTuihuoCount);
                mapDataOne.put("returnPayTotal", String.format("%.1f", unPayReturn));
                mapDataOne.put("actPayCount", actPayCountTotal);
                mapDataOne.put("actPayTotal", String.format("%.1f", actPayTotal));
                supplierEntity.setItemData(mapDataOne);
            }
        }
        return R.ok().put("data", nxJrdhSupplierEntities);
    }



    @RequestMapping(value = "/deleteGbDisSuppler/{id}")
    @ResponseBody
    public R deleteGbDisSuppler(@PathVariable Integer id) {
        Map<String, Object> map = new HashMap<>();
        map.put("supplierId", id);
        map.put("status", 4);
        int i = gbPurBatchService.queryDisPurchaseBatchCount(map);

        if (i > 0 ) {
            return R.error(-1, "有未结账账单");
        } else {
            NxJrdhSupplierEntity supplierEntity = nxJrdhSupplierService.getById(id);
            Map<String, Object> mapS = new HashMap<>();
            mapS.put("supplierId", supplierEntity.getNxJrdhSupplierId());
            mapS.put("disId", supplierEntity.getNxJrdhsGbDistributerId());
            List<GbDistributerGoodsEntity> gbDistributerGoodsEntities = gbDistributerGoodsService.queryDisGoodsByParams(mapS);
            if(gbDistributerGoodsEntities.size() > 0){
                for(GbDistributerGoodsEntity distributerGoodsEntity: gbDistributerGoodsEntities){
                    distributerGoodsEntity.setGbDgGbSupplierId(null);
                    distributerGoodsEntity.setGbDgNxDistributerId(-1);
                    distributerGoodsEntity.setGbDgNxDistributerGoodsId(-1);
                    gbDistributerGoodsService.update(distributerGoodsEntity);
                }
            }


            supplierEntity.setNxJrdhsGbDepartmentId(-1);
            nxJrdhSupplierService.updateById(supplierEntity);

            return R.ok();

        }
    }


    @RequestMapping(value = "/depGetAllSupplier/{depId}")
    @ResponseBody
    public R depGetAllSupplier(@PathVariable  Integer depId) {
        Map<String, Object> map3 = new HashMap<>();
        map3.put("gbDepId", depId);
        List<NxJrdhSupplierEntity> nxJrdhSupplierEntities = nxJrdhSupplierService.queryJrdhSupplerByParams(map3);

        return R.ok().put("data", nxJrdhSupplierEntities);
    }




}
