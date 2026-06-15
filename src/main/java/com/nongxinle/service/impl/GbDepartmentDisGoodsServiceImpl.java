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
    public GbDepartmentDisGoodsEntity createDepDisGoodsForJjOrder(
            GbDepartmentOrdersEntity gbDepartmentOrders,
            GbDistributerGoodsEntity gbDisGoods) {
        String depGoodsName = gbDisGoods.getGbDgGoodsName();

        Integer greatId = gbDisGoods.getGbDgDfgGoodsGreatId();
        if (greatId == null && gbDisGoods.getGbDgDfgGoodsGrandId() != null) {
            GbDistributerFatherGoodsEntity grandGoods =
                    gbDistributerFatherGoodsService.queryObject(gbDisGoods.getGbDgDfgGoodsGrandId());
            if (grandGoods != null) {
                greatId = grandGoods.getGbDfgFathersFatherId();
            }
        }

        String depPinyin = gbDisGoods.getGbDgGoodsPinyin();
        String depPy = gbDisGoods.getGbDgGoodsPy();
        if (depPinyin == null || depPinyin.isEmpty()) {
            depPinyin = hanziToPinyin(depGoodsName);
        }
        if (depPy == null || depPy.isEmpty()) {
            depPy = getHeadStringByString(depGoodsName, false, null);
        }

        GbDepartmentDisGoodsEntity depDisGoods = new GbDepartmentDisGoodsEntity();
        depDisGoods.setGbDdgDepGoodsName(depGoodsName);
        depDisGoods.setGbDdgDisGoodsId(gbDisGoods.getGbDistributerGoodsId());
        depDisGoods.setGbDdgDisGoodsFatherId(gbDisGoods.getGbDgDfgGoodsFatherId());
        depDisGoods.setGbDdgDisGoodsGrandId(gbDisGoods.getGbDgDfgGoodsGrandId());
        depDisGoods.setGbDdgDisGoodsGreatId(greatId);
        depDisGoods.setGbDdgDepGoodsPinyin(depPinyin);
        depDisGoods.setGbDdgDepGoodsPy(depPy);
        depDisGoods.setGbDdgDepGoodsStandardname(gbDisGoods.getGbDgGoodsStandardname());
        depDisGoods.setGbDdgDepartmentId(gbDepartmentOrders.getGbDoDepartmentId());
        depDisGoods.setGbDdgDepartmentFatherId(gbDepartmentOrders.getGbDoDepartmentFatherId());
        depDisGoods.setGbDdgGbDepartmentId(gbDisGoods.getGbDgGbDepartmentId());
        depDisGoods.setGbDdgGbDisId(gbDisGoods.getGbDgDistributerId());
        depDisGoods.setGbDdgGoodsType(gbDisGoods.getGbDgGoodsType());
        depDisGoods.setGbDdgStockTotalWeight("0.0");
        depDisGoods.setGbDdgStockTotalSubtotal("0.0");
        depDisGoods.setGbDdgShowStandardId(-1);
        depDisGoods.setGbDdgShowStandardName(gbDisGoods.getGbDgGoodsStandardname());
        depDisGoods.setGbDdgOrderStandard(gbDepartmentOrders.getGbDoStandard());
        depDisGoods.setGbDdgShowStandardScale("-1");
        depDisGoods.setGbDdgShowStandardWeight(gbDisGoods.getGbDgGoodsStandardWeight());
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
    public List<GbDistributerGoodsEntity> disQueryDisGoodsWithOrderForAiTree(Map<String, Object> map) {
        return baseMapper.disQueryDisGoodsWithOrderForAiTree(map);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> disQueryDepGoodsWithOrderForAiTree(Map<String, Object> map) {
        return baseMapper.disQueryDepGoodsWithOrderForAiTree(map);
    }

    @Override
    public GbDepartmentDisGoodsEntity queryDepGoodsDetailById(Integer depGoodsId) {
        return baseMapper.queryDepGoodsDetailById(depGoodsId);
    }

    @Override
    public TreeSet<GbDepartmentDisGoodsEntity> queryDepDisGoodsQuickSearchStrGb(Map<String, Object> map) {
        List<GbDepartmentDisGoodsEntity> list = baseMapper.queryDepDisGoodsQuickSearchStrGb(map);
        if (list == null || list.isEmpty()) {
            return new TreeSet<>();
        }
        return new TreeSet<>(list);
    }
}
