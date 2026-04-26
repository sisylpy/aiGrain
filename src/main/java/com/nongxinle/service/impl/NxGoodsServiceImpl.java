package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.mapper.NxGoodsMapper;
import com.nongxinle.service.NxGoodsService;
import com.nongxinle.entity.NxGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 农鑫商品Service实现
 */
@Service
public class NxGoodsServiceImpl extends ServiceImpl<NxGoodsMapper, NxGoodsEntity> implements NxGoodsService {

    private static final Logger log = LoggerFactory.getLogger(NxGoodsServiceImpl.class);

    /**
     * 查询单个商品（与老项目兼容）
     * 使用自定义SQL填充fatherGoods、grandGoods、greatGrandGoods关联对象
     */
    @Override
    public NxGoodsEntity queryObject(Integer nxGoodsId) {
        return baseMapper.queryObject(nxGoodsId);
    }

    @Override
    public List<NxGoodsEntity> getiBookCoverData() {
        return baseMapper.getNxGoodsCateList();
    }

    @Override
    public List<Integer> queryOnlyGoodsIds(Map<String, Object> map) {
        return baseMapper.queryOnlyGoodsIds(map);
    }

    @Override
    public int queryNxGoodsCountByGreatGrandId(Map<String, Object> map) {
        return baseMapper.queryNxGoodsCountByGreatGrandId(map);
    }

    @Override
    public List<NxGoodsEntity> queryNxGoodsPageByGreatGrandId(Map<String, Object> map) {
        return baseMapper.queryNxGoodsPageByGreatGrandId(map);
    }

    @Override
    public List<NxGoodsEntity> queryGbDepNxGrandGoodsByGreatId(Map<String, Object> map) {
        log.info("【Service】开始查询，参数: gbDepId={}, gbDisId={}, greatGrandId={}", 
                map.get("gbDepId"), map.get("gbDisId"), map.get("greatGrandId"));
        
        List<NxGoodsEntity> list = baseMapper.queryGbDepNxGrandGoodsByGreatId(map);
        
        log.info("【Service】查询完成，返回list.size()={}", list == null ? "null" : list.size());
        
        // 调试：检查第一个商品的原始数据
        if (list != null && !list.isEmpty()) {
            NxGoodsEntity goods = list.get(0);
            log.info("【Service调试】nxGoodsId={}, nxGoodsName={}", goods.getNxGoodsId(), goods.getNxGoodsName());
            log.info("【Service调试】gbDistributerGoodsEntity={}", goods.getGbDistributerGoodsEntity());
            log.info("【Service调试】gbDepartmentDisGoodsEntity={}", goods.getGbDepartmentDisGoodsEntity());
            log.info("【Service调试】gbDepartmentOrdersEntity={}", goods.getGbDepartmentOrdersEntity());
        }
        
        return list;
    }

    @Override
    public List<NxGoodsEntity> queryDisGoodsEqualSearchStrWithDepOrders(Map<String, Object> map) {
        return baseMapper.queryDisGoodsEqualSearchStrWithDepOrders(map);
    }

    @Override
    public List<NxGoodsEntity> queryDisGoodsQuickSearchStrWithDepOrders(Map<String, Object> map) {
        return baseMapper.queryDisGoodsQuickSearchStrWithDepOrders(map);
    }

    @Override
    public List<NxGoodsEntity> queryDisGoodsQuickSearchPyWithDepOrders(Map<String, Object> map) {
        return baseMapper.queryDisGoodsQuickSearchPyWithDepOrders(map);
    }
}
