package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.mapper.GbDepartmentGoodsStockReduceMapper;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 部门商品库存减少Service实现
 */
@Service
public class GbDepartmentGoodsStockReduceServiceImpl extends ServiceImpl<GbDepartmentGoodsStockReduceMapper, GbDepartmentGoodsStockReduceEntity> implements GbDepartmentGoodsStockReduceService {

    @Autowired
    private GbDepartmentGoodsStockReduceMapper gbDepartmentGoodsStockReduceMapper;

    @Override
    public Integer queryReduceTypeCount(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceTypeCount(map);
    }

    @Override
    public Double queryReduceCostSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceCostSubtotal(map);
    }

    @Override
    public Double queryReduceByTypeTotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceByTypeTotal(map);
    }

    @Override
    public Map<String, Object> queryReduceAllTypesTotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceAllTypesTotal(map);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryStockSubtotalTopTimes(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryStockSubtotalTopTimes(map);
    }

    @Override
    public List<Map<String, Object>> queryGbPurchaseGoodsTopDay(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryGbPurchaseGoodsTopDay(map);
    }

}
