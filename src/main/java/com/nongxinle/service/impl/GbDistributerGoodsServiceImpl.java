package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.*;
import com.nongxinle.mapper.GbDistributerGoodsMapper;
import com.nongxinle.service.GbDistributerAliasService;
import com.nongxinle.service.GbDistributerFatherGoodsService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerStandardService;
import com.nongxinle.service.NxAliasService;
import com.nongxinle.service.NxGoodsService;
import com.nongxinle.service.NxStandardService;
import com.nongxinle.utils.GbConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批发商商品Service实现
 */
@Service
public class GbDistributerGoodsServiceImpl extends ServiceImpl<GbDistributerGoodsMapper, GbDistributerGoodsEntity> implements GbDistributerGoodsService {

    @Autowired
    private NxGoodsService nxGoodsService;
    @Autowired
    private NxAliasService nxAliasService;
    @Autowired
    private NxStandardService nxStandardService;
    @Autowired
    private GbDistributerFatherGoodsService dgfService;
    @Autowired
    private GbDistributerAliasService gbDistributerAliasService;
    @Autowired
    private GbDistributerStandardService gbDistributerStandardService;

    @Override
    public List<GbDistributerGoodsEntity> queryDisGoodsByParams(Map<String, Object> map) {
        return baseMapper.queryDisGoodsByParams(map);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryGbDisGoodsQuickSearchStr(Map<String, Object> map) {
        return baseMapper.queryGbDisGoodsQuickSearchStr(map);
    }

    @Override
    public GbDistributerGoodsEntity createDistributerGoodsFromNxGoods(Integer gbDisId, Integer depId, Integer nxGoodsId) {
        NxGoodsEntity nxGoodsEntity = nxGoodsService.queryObject(nxGoodsId);
        GbDistributerGoodsEntity cgnGoods = new GbDistributerGoodsEntity();
        cgnGoods.setGbDgGoodsName(nxGoodsEntity.getNxGoodsName());
        cgnGoods.setGbDgGoodsStandardname(nxGoodsEntity.getNxGoodsStandardname());
        cgnGoods.setGbDgGoodsPy(nxGoodsEntity.getNxGoodsPy());
        cgnGoods.setGbDgGoodsPinyin(nxGoodsEntity.getNxGoodsPinyin());
        cgnGoods.setGbDgGoodsStandardWeight(nxGoodsEntity.getNxGoodsStandardWeight());
        cgnGoods.setGbDgGoodsDetail(nxGoodsEntity.getNxGoodsDetail());
        cgnGoods.setGbDgGoodsBrand(nxGoodsEntity.getNxGoodsBrand());
        cgnGoods.setGbDgGoodsPlace(nxGoodsEntity.getNxGoodsPlace());
        cgnGoods.setGbDgGoodsSort(nxGoodsEntity.getNxGoodsSort());
        cgnGoods.setGbDgGoodsSonsSort(nxGoodsEntity.getNxGoodsSonsSort());
        cgnGoods.setGbDgNxFatherImgLarge(nxGoodsEntity.getNxGoodsFileBig());
        cgnGoods.setGbDgNxFatherImg(nxGoodsEntity.getNxGoodsFile());
        cgnGoods.setGbDgNxGoodsFatherColor(nxGoodsEntity.getColor());
        cgnGoods.setGbDgDistributerId(gbDisId);
        cgnGoods.setGbDgGoodsStatus(0);
        cgnGoods.setGbDgGoodsIsWeight(0);
        cgnGoods.setGbDgGoodsIsHidden(0);
        cgnGoods.setGbDgNxGoodsId(nxGoodsEntity.getNxGoodsId());
        cgnGoods.setGbDgNxFatherId(nxGoodsEntity.getNxGoodsFatherId());
        cgnGoods.setGbDgNxFatherName(nxGoodsEntity.getFatherGoods().getNxGoodsName());
        cgnGoods.setGbDgNxFatherImg(nxGoodsEntity.getFatherGoods().getNxGoodsFile());
        cgnGoods.setGbDgNxGrandName(nxGoodsEntity.getGrandGoods().getNxGoodsName());
        cgnGoods.setGbDgNxGrandId(nxGoodsEntity.getGrandGoods().getNxGoodsId());
        cgnGoods.setGbDgNxGreatGrandName(nxGoodsEntity.getGreatGrandGoods().getNxGoodsName());
        cgnGoods.setGbDgNxGreatGrandId(nxGoodsEntity.getGreatGrandGoods().getNxGoodsId());
        cgnGoods.setGbDgPullOff(0);
        cgnGoods.setGbDgGoodsType(GbConstants.DistributorGoodsType.SELF_PURCHASE);
        cgnGoods.setGbDgGbSupplierId(-1);
        cgnGoods.setGbDgNxDistributerId(-1);
        cgnGoods.setGbDgNxDistributerGoodsId(-1);
        cgnGoods.setGbDgNxDistributerGoodsPrice("0.1");
        cgnGoods.setGbDgGbDepartmentId(depId);
        cgnGoods.setGbDgControlFresh(0);
        cgnGoods.setGbDgControlPrice(0);
        cgnGoods.setGbDgGoodsInventoryType(1);
        cgnGoods.setGbDgIsFranchisePrice(0);
        cgnGoods.setGbDgIsSelfControl(0);

        GbDistributerGoodsEntity disGoods = persistDistributerGoodsWithFatherHierarchy(cgnGoods);

        Map<String, Object> map = new HashMap<>();
        map.put("goodsId", nxGoodsId);
        List<NxAliasEntity> aliasEntities = nxAliasService.queryNxAliasList(map);
        if (aliasEntities.size() > 0) {
            for (NxAliasEntity aliasEntity : aliasEntities) {
                GbDistributerAliasEntity disAlias = new GbDistributerAliasEntity();
                disAlias.setGbDaDisGoodsId(disGoods.getGbDistributerGoodsId());
                disAlias.setGbDaAliasName(aliasEntity.getNxAliasName());
                gbDistributerAliasService.save(disAlias);
            }
        }

        List<NxStandardEntity> nxStandardEntities = nxStandardService.queryGoodsStandardListByGoodId(nxGoodsId);

        if (nxStandardEntities.size() > 0) {
            for (NxStandardEntity standardEntity : nxStandardEntities) {
                GbDistributerStandardEntity distributerStandardEntity = new GbDistributerStandardEntity();
                distributerStandardEntity.setGbDsDisGoodsId(disGoods.getGbDistributerGoodsId());
                distributerStandardEntity.setGbDsStandardName(standardEntity.getNxStandardName());
                gbDistributerStandardService.save(distributerStandardEntity);
            }
        }
        if(nxGoodsEntity.getNxGoodsItemsPerCarton() != null){
            String nxGoodsCartonUnit = nxGoodsEntity.getNxGoodsCartonUnit();
            GbDistributerStandardEntity distributerStandardEntity = new GbDistributerStandardEntity();
            distributerStandardEntity.setGbDsDisGoodsId(disGoods.getGbDistributerGoodsId());
            distributerStandardEntity.setGbDsStandardName(nxGoodsCartonUnit);
            gbDistributerStandardService.save(distributerStandardEntity);
        }

        return disGoods;
    }

    /**
     * 持久化批发商商品并维护父级分类树（与老项目逻辑一致）。
     */
    private GbDistributerGoodsEntity persistDistributerGoodsWithFatherHierarchy(GbDistributerGoodsEntity goods) {
        Integer nxGoodsId = goods.getGbDgNxGoodsId();
        NxGoodsEntity nxGoodsEntity = nxGoodsService.queryObject(nxGoodsId);

        goods.setGbDgGoodsName(nxGoodsEntity.getNxGoodsName());
        goods.setGbDgNxFatherImg(nxGoodsEntity.getNxGoodsFile());
        goods.setGbDgGoodsStandardname(nxGoodsEntity.getNxGoodsStandardname());
        goods.setGbDgGoodsDetail(nxGoodsEntity.getNxGoodsDetail());
        goods.setGbDgGoodsPlace(nxGoodsEntity.getNxGoodsPlace());
        goods.setGbDgGoodsBrand(nxGoodsEntity.getNxGoodsBrand());
        goods.setGbDgGoodsStandardWeight(nxGoodsEntity.getNxGoodsStandardWeight());
        goods.setGbDgGoodsPinyin(nxGoodsEntity.getNxGoodsPinyin());
        goods.setGbDgGoodsPy(nxGoodsEntity.getNxGoodsPy());
        goods.setGbDgNxFatherId(nxGoodsEntity.getNxGoodsFatherId());
        goods.setGbDgGoodsSort(nxGoodsEntity.getNxGoodsSort());
        goods.setGbDgGoodsSonsSort(nxGoodsEntity.getNxGoodsSonsSort());
        goods.setGbDgQuantityDays(nxGoodsEntity.getNxGoodsQuantityDays());

        goods.setGbDgGoodsIsHidden(0);
        goods.setGbDgGoodsIsWeight(0);
        goods.setGbDgControlPrice(0);
        goods.setGbDgControlFresh(0);
        goods.setGbDgPullOff(0);
        goods.setGbDgGoodsStatus(1);
        goods.setGbDgGoodsType(2);
        goods.setGbDgIsFranchisePrice(0);
        goods.setGbDgIsSelfControl(0);
        goods.setGbDgGoodsInventoryType(1);
        goods.setGbDgGbSupplierId(-1);
        goods.setGbDgNxDistributerId(-1);
        goods.setGbDgNxDistributerGoodsId(-1);
        goods.setGbDgNxDistributerGoodsPrice("0.1");

        Integer GbDgDistributerId = goods.getGbDgDistributerId();
        Integer GbDgNxFatherId = goods.getGbDgNxFatherId();

        Map<String, Object> map11 = new HashMap<>();
        map11.put("disId", GbDgDistributerId);
        map11.put("nxFatherId", GbDgNxFatherId);
        List<GbDistributerGoodsEntity> nxDistributerGoodsEntities = queryDisGoodsByParams(map11);

        if (nxDistributerGoodsEntities.size() > 0) {
            GbDistributerGoodsEntity disGoodsEntity = nxDistributerGoodsEntities.get(0);
            Integer nxDgDfgGoodsFatherId1 = disGoodsEntity.getGbDgDfgGoodsFatherId();
            Integer nxDgDfgGoodsGrandId = disGoodsEntity.getGbDgDfgGoodsGrandId();
            Integer nxDgDfgGoodsGreatId = disGoodsEntity.getGbDgDfgGoodsGreatId();

            GbDistributerFatherGoodsEntity nxDistributerFatherGoodsEntity = dgfService.queryObject(nxDgDfgGoodsFatherId1);
            Integer nxDfgGoodsAmount = nxDistributerFatherGoodsEntity.getGbDfgGoodsAmount();
            nxDistributerFatherGoodsEntity.setGbDfgGoodsAmount(nxDfgGoodsAmount + 1);
            dgfService.update(nxDistributerFatherGoodsEntity);

            goods.setGbDgDfgGoodsFatherId(nxDgDfgGoodsFatherId1);
            goods.setGbDgDfgGoodsGrandId(nxDgDfgGoodsGrandId);
            goods.setGbDgDfgGoodsGreatId(nxDgDfgGoodsGreatId);

            save(goods);

        } else {
            GbDistributerFatherGoodsEntity dgf = new GbDistributerFatherGoodsEntity();
            dgf.setGbDfgDistributerId(goods.getGbDgDistributerId());
            dgf.setGbDfgFatherGoodsName(goods.getGbDgNxFatherName());
            dgf.setGbDfgFatherGoodsLevel(2);
            dgf.setGbDfgGoodsAmount(1);
            dgf.setGbDfgPriceAmount(0);
            dgf.setGbDfgPriceTwoAmount(0);
            dgf.setGbDfgPriceThreeAmount(0);
            dgf.setGbDfgFatherGoodsColor(goods.getGbDgNxGoodsFatherColor());
            dgf.setGbDfgNxGoodsId(goods.getGbDgNxFatherId());
            dgf.setGbDfgFatherGoodsImg(goods.getGbDgNxFatherImg());
            dgf.setGbDfgFatherGoodsImgLarge(goods.getGbDgNxFatherImgLarge());
            dgf.setGbDfgFatherGoodsSort(nxGoodsEntity.getFatherGoods().getNxGoodsSort());
            dgfService.save(dgf);

            Integer distributerFatherGoodsId = dgf.getGbDistributerFatherGoodsId();
            goods.setGbDgDfgGoodsFatherId(distributerFatherGoodsId);

            System.out.println("zizin" + dgf.getGbDfgFathersFatherId());
            save(goods);

            String grandName = goods.getGbDgNxGrandName();
            Map<String, Object> map2 = new HashMap<>();
            map2.put("disId", GbDgDistributerId);
            map2.put("fathersFatherName", grandName);
            List<GbDistributerFatherGoodsEntity> grandGoodsFather = dgfService.queryHasDisFathersFather(map2);

            if (grandGoodsFather.size() > 0) {
                GbDistributerFatherGoodsEntity gbDistributerFatherGoodsEntity = grandGoodsFather.get(0);
                Integer nxDfgGoodsAmount = dgf.getGbDfgGoodsAmount();
                dgf.setGbDfgGoodsAmount(nxDfgGoodsAmount + 1);
                dgf.setGbDfgFathersFatherId(gbDistributerFatherGoodsEntity.getGbDistributerFatherGoodsId());
                dgfService.update(dgf);

                Integer nxDfgFathersFatherId = gbDistributerFatherGoodsEntity.getGbDistributerFatherGoodsId();
                goods.setGbDgDfgGoodsGrandId(nxDfgFathersFatherId);
                GbDistributerFatherGoodsEntity great = dgfService.queryObject(nxDfgFathersFatherId);
                goods.setGbDgDfgGoodsGreatId(great.getGbDfgFathersFatherId());
                updateById(goods);

            } else {
                GbDistributerFatherGoodsEntity grand = new GbDistributerFatherGoodsEntity();
                String nxCgGrandFatherName = goods.getGbDgNxGrandName();
                grand.setGbDfgFatherGoodsName(nxCgGrandFatherName);
                grand.setGbDfgDistributerId(goods.getGbDgDistributerId());
                grand.setGbDfgFatherGoodsLevel(1);
                grand.setGbDfgGoodsAmount(1);
                grand.setGbDfgFatherGoodsColor(goods.getGbDgNxGoodsFatherColor());
                grand.setGbDfgNxGoodsId(goods.getGbDgNxGrandId());
                NxGoodsEntity nxGrand = nxGoodsService.queryObject(goods.getGbDgNxGrandId());
                grand.setGbDfgFatherGoodsImg(nxGrand.getNxGoodsFile());
                grand.setGbDfgFatherGoodsImgLarge(nxGrand.getNxGoodsFileBig());
                grand.setGbDfgFatherGoodsSort(nxGrand.getNxGoodsSort());
                dgfService.save(grand);

                dgf.setGbDfgFathersFatherId(grand.getGbDistributerFatherGoodsId());
                dgfService.update(dgf);

                goods.setGbDgDfgGoodsGrandId(grand.getGbDistributerFatherGoodsId());
                updateById(goods);

                Map<String, Object> map3 = new HashMap<>();
                map3.put("disId", GbDgDistributerId);
                String greatGrandName = goods.getGbDgNxGreatGrandName();
                map3.put("fathersFatherName", greatGrandName);
                List<GbDistributerFatherGoodsEntity> greatGrandGoodsFather = dgfService.queryHasDisFathersFather(map3);

                if (greatGrandGoodsFather.size() > 0) {
                    GbDistributerFatherGoodsEntity gbDistributerFatherGoodsEntity = greatGrandGoodsFather.get(0);
                    Integer disFatherId = gbDistributerFatherGoodsEntity.getGbDistributerFatherGoodsId();
                    grand.setGbDfgFathersFatherId(disFatherId);
                    Integer gbDfgGoodsAmount = grand.getGbDfgGoodsAmount();
                    grand.setGbDfgGoodsAmount(gbDfgGoodsAmount + 1);
                    dgfService.update(grand);

                    goods.setGbDgDfgGoodsGreatId(disFatherId);
                    updateById(goods);

                } else {
                    GbDistributerFatherGoodsEntity greatGrand = new GbDistributerFatherGoodsEntity();
                    NxGoodsEntity greatGrandEntity = nxGoodsService.queryObject(goods.getGbDgNxGreatGrandId());
                    String greatGrandName1 = goods.getGbDgNxGreatGrandName();
                    greatGrand.setGbDfgFatherGoodsName(greatGrandName1);
                    greatGrand.setGbDfgDistributerId(goods.getGbDgDistributerId());
                    greatGrand.setGbDfgFatherGoodsImg(greatGrandEntity.getNxGoodsFile());
                    greatGrand.setGbDfgFatherGoodsImgLarge(greatGrandEntity.getNxGoodsFileBig());
                    greatGrand.setGbDfgFatherGoodsLevel(0);
                    greatGrand.setGbDfgFathersFatherId(0);
                    greatGrand.setGbDfgFatherGoodsColor(goods.getGbDgNxGoodsFatherColor());
                    greatGrand.setGbDfgNxGoodsId(goods.getGbDgNxGreatGrandId());
                    greatGrand.setGbDfgFatherGoodsSort(greatGrandEntity.getNxGoodsSort());
                    greatGrand.setGbDfgGoodsAmount(1);
                    dgfService.save(greatGrand);

                    grand.setGbDfgFathersFatherId(greatGrand.getGbDistributerFatherGoodsId());
                    dgfService.update(grand);

                    goods.setGbDgDfgGoodsGreatId(greatGrand.getGbDistributerFatherGoodsId());
                    updateById(goods);
                }
            }
        }

        return goods;
    }
}
