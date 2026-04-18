package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.mapper.GbDepartmentDisGoodsMapper;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDistributerFatherGoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static com.nongxinle.utils.PinYin4jUtils.getHeadStringByString;
import static com.nongxinle.utils.PinYin4jUtils.hanziToPinyin;

/**
 * 部门商品关联Service实现
 */
@Service
public class GbDepartmentDisGoodsServiceImpl extends ServiceImpl<GbDepartmentDisGoodsMapper, GbDepartmentDisGoodsEntity> implements GbDepartmentDisGoodsService {

    @Autowired
    private GbDistributerFatherGoodsService gbDistributerFatherGoodsService;

    @Override
    public GbDepartmentDisGoodsEntity createDepDisGoodsForJjOrderAfterNxImport(
            GbDepartmentOrdersEntity gbDepartmentOrders,
            GbDistributerGoodsEntity gbNewGoods) {
        GbDistributerFatherGoodsEntity grandGoods =
                gbDistributerFatherGoodsService.queryObject(gbNewGoods.getGbDgDfgGoodsGrandId());
        Integer greatFatherGoodsId = grandGoods.getGbDfgFathersFatherId();

        GbDepartmentDisGoodsEntity depDisGoods = new GbDepartmentDisGoodsEntity();
        String gbDoGoodsName = gbDepartmentOrders.getGbDoGoodsName();
        depDisGoods.setGbDdgDepGoodsName(gbDoGoodsName);
        depDisGoods.setGbDdgDisGoodsId(gbNewGoods.getGbDistributerGoodsId());
        depDisGoods.setGbDdgDisGoodsFatherId(gbNewGoods.getGbDgDfgGoodsFatherId());
        depDisGoods.setGbDdgDisGoodsGrandId(gbNewGoods.getGbDgDfgGoodsGrandId());
        depDisGoods.setGbDdgDisGoodsGreatId(greatFatherGoodsId);
        depDisGoods.setGbDdgDepGoodsPinyin(hanziToPinyin(gbDoGoodsName));
        depDisGoods.setGbDdgDepGoodsPy(getHeadStringByString(gbDoGoodsName, false, null));
        depDisGoods.setGbDdgDepGoodsStandardname(gbNewGoods.getGbDgGoodsStandardname());
        depDisGoods.setGbDdgDepartmentId(gbDepartmentOrders.getGbDoDepartmentId());
        depDisGoods.setGbDdgDepartmentFatherId(gbDepartmentOrders.getGbDoDepartmentFatherId());
        depDisGoods.setGbDdgGbDepartmentId(gbNewGoods.getGbDgGbDepartmentId());
        depDisGoods.setGbDdgGbDisId(gbNewGoods.getGbDgDistributerId());
        depDisGoods.setGbDdgGoodsType(gbNewGoods.getGbDgGoodsType());
        depDisGoods.setGbDdgStockTotalWeight("0.0");
        depDisGoods.setGbDdgStockTotalSubtotal("0.0");
        depDisGoods.setGbDdgShowStandardId(-1);
        depDisGoods.setGbDdgShowStandardName(gbNewGoods.getGbDgGoodsStandardname());
        depDisGoods.setGbDdgOrderStandard(gbDepartmentOrders.getGbDoStandard());
        depDisGoods.setGbDdgShowStandardScale("-1");
        depDisGoods.setGbDdgShowStandardWeight(null);
        depDisGoods.setGbDdgNxDistributerGoodsId(gbNewGoods.getGbDgNxDistributerGoodsId());
        depDisGoods.setGbDdgNxDistributerId(-1);
        depDisGoods.setGbDdgPrintStandard(gbNewGoods.getGbDgGoodsStandardname());
        save(depDisGoods);
        return depDisGoods;
    }

    @Override
    public GbDepartmentDisGoodsEntity createDepDisGoodsForJjOrderFromExistingDisGoods(
            GbDepartmentOrdersEntity gbDepartmentOrders,
            GbDistributerGoodsEntity gbDistributerGoodsEntity) {
        GbDepartmentDisGoodsEntity depDisGoods = new GbDepartmentDisGoodsEntity();
        depDisGoods.setGbDdgDepGoodsName(gbDistributerGoodsEntity.getGbDgGoodsName());
        depDisGoods.setGbDdgDisGoodsId(gbDistributerGoodsEntity.getGbDistributerGoodsId());
        depDisGoods.setGbDdgDisGoodsFatherId(gbDistributerGoodsEntity.getGbDgDfgGoodsFatherId());
        depDisGoods.setGbDdgDisGoodsGrandId(gbDistributerGoodsEntity.getGbDgDfgGoodsGrandId());
        depDisGoods.setGbDdgDisGoodsGreatId(gbDistributerGoodsEntity.getGbDgDfgGoodsGreatId());
        depDisGoods.setGbDdgDepGoodsPinyin(gbDistributerGoodsEntity.getGbDgGoodsPinyin());
        depDisGoods.setGbDdgDepGoodsPy(gbDistributerGoodsEntity.getGbDgGoodsPy());
        depDisGoods.setGbDdgDepGoodsStandardname(gbDistributerGoodsEntity.getGbDgGoodsStandardname());
        depDisGoods.setGbDdgDepartmentId(gbDepartmentOrders.getGbDoDepartmentId());
        depDisGoods.setGbDdgDepartmentFatherId(gbDepartmentOrders.getGbDoDepartmentFatherId());
        depDisGoods.setGbDdgGbDepartmentId(gbDistributerGoodsEntity.getGbDgGbDepartmentId());
        depDisGoods.setGbDdgGbDisId(gbDistributerGoodsEntity.getGbDgDistributerId());
        depDisGoods.setGbDdgGoodsType(gbDistributerGoodsEntity.getGbDgGoodsType());
        depDisGoods.setGbDdgStockTotalWeight("0.0");
        depDisGoods.setGbDdgStockTotalSubtotal("0.0");
        depDisGoods.setGbDdgShowStandardId(-1);
        depDisGoods.setGbDdgShowStandardName(gbDistributerGoodsEntity.getGbDgGoodsStandardname());
        depDisGoods.setGbDdgShowStandardWeight(gbDistributerGoodsEntity.getGbDgGoodsStandardWeight());
        save(depDisGoods);
        return depDisGoods;
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> disGetDepDisGoodsCataGb(Map<String, Object> map) {
        return baseMapper.disGetDepDisGoodsCataGb(map);
    }

    @Override
    public List<Integer> queryOnlyDepGoodsIds(Map<String, Object> map) {
        return baseMapper.queryOnlyDepGoodsIds(map);
    }

    @Override
    public int queryDepGoodsCount(Map<String, Object> mapC) {
        return baseMapper.queryDepGoodsCount(mapC);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> depQueryDepGoodsWithOrderForAi(Map<String, Object> map) {
        return baseMapper.depQueryDepGoodsWithOrderForAi(map);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> queryGbDepDisGoodsByParams(Map<String, Object> map) {
        return baseMapper.queryGbDepDisGoodsByParams(map);
    }

    @Override
    public List<Integer> queryOnlyDisGoodsIds(Map<String, Object> map) {
        return baseMapper.queryOnlyDisGoodsIds(map);
    }

    @Override
    public GbDepartmentDisGoodsEntity queryDepartmentGoodsForAi(Map<String, Object> map) {
        return baseMapper.queryDepartmentGoodsForAi(map);
    }

    @Override
    public int queryDisGoodsCount(Map<String, Object> map) {
        return baseMapper.queryDisGoodsCount(map);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> disQueryDisGoodsWithOrderForAi(Map<String, Object> map) {
        return baseMapper.disQueryDisGoodsWithOrderForAi(map);
    }

    @Override
    public TreeSet<GbDistributerGoodsEntity> disQueryDisGoodsWithOrderForAiTree(Map<String, Object> map) {
        return baseMapper.disQueryDisGoodsWithOrderForAiTree(map);
    }
}
