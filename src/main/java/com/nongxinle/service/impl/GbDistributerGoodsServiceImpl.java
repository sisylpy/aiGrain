package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.*;
import com.nongxinle.mapper.GbDistributerGoodsMapper;
import com.nongxinle.service.GbDistributerAliasService;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDistributerFatherGoodsService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerStandardService;
import com.nongxinle.service.NxAliasService;
import com.nongxinle.service.NxGoodsService;
import com.nongxinle.service.NxStandardService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.ImagePaths;
import com.nongxinle.utils.UploadFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.nongxinle.utils.PinYin4jUtils.getEnglishKuohao;
import static com.nongxinle.utils.PinYin4jUtils.getHeadStringByString;
import static com.nongxinle.utils.PinYin4jUtils.hanziToPinyin;

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
    @Autowired
    private GbDepartmentDisGoodsService gbDepDisGoodsService;
    @Autowired
    private GbDepartmentService gbDepartmentService;

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
        cgnGoods.setGbDgQuantityDays(nxGoodsEntity.getNxGoodsQuantityDays());

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

    @Override
    public GbDistributerGoodsEntity saveLinshiGoodsGb(MultipartFile file, String goodsName, String standard,
                                                      String detail, Integer disId, Integer toDepId, Integer depId
            ,Integer depFatherId) {
        String filePath = null;
        if (file != null && !file.isEmpty()) {
            String originalName = goodsName.replaceAll("[\\\\/:*?\"<>|]", "");
            String headByString = hanziToPinyin(getEnglishKuohao(originalName));
            filePath = UploadFile.uploadFileName(ImagePaths.GOODS, file, headByString);
        }

        Map<String, Object> map = new HashMap<>(3);
        map.put("disId", disId);
        map.put("nxGoodsId", -1);
        map.put("goodsLevel", 2);

        List<GbDistributerFatherGoodsEntity> fatherGoodsEntities = dgfService.queryDisFathersGoodsByParamsGb(map);
        if (fatherGoodsEntities == null || fatherGoodsEntities.isEmpty()) {
            throw new IllegalStateException("未找到批发商临时父分类（disId=" + disId + "）");
        }

        GbDistributerFatherGoodsEntity fatherGoodsEntity = fatherGoodsEntities.get(0);
        GbDistributerFatherGoodsEntity grandFather = dgfService.queryObject(fatherGoodsEntity.getGbDfgFathersFatherId());
        if (grandFather == null) {
            throw new IllegalStateException("父级分类不存在: gbDistributerFatherGoodsId="
                    + fatherGoodsEntity.getGbDfgFathersFatherId());
        }

        GbDepartmentEntity depEntity = gbDepartmentService.getById(depId);
        if (depEntity == null) {
            throw new IllegalArgumentException("部门不存在: depId=" + depId);
        }

        GbDistributerGoodsEntity goods = new GbDistributerGoodsEntity();
        goods.setGbDgDfgGoodsFatherId(fatherGoodsEntity.getGbDistributerFatherGoodsId());
        goods.setGbDgDfgGoodsGrandId(fatherGoodsEntity.getGbDfgFathersFatherId());
        goods.setGbDgDfgGoodsGreatId(grandFather.getGbDfgFathersFatherId());
        goods.setGbDgGoodsType(GbConstants.DistributorGoodsType.SELF_PURCHASE);
        goods.setGbDgGbDepartmentId(toDepId);
        goods.setGbDgDistributerId(disId);
        goods.setGbDgNxFatherImgLarge(filePath);
        goods.setGbDgNxFatherImg(filePath);
        goods.setGbDgGoodsName(goodsName);
        goods.setGbDgGoodsPinyin(hanziToPinyin(goodsName));
        goods.setGbDgGoodsPy(getHeadStringByString(goodsName, false, null));
        goods.setGbDgGoodsIsHidden(0);
        goods.setGbDgGoodsStandardname(standard);
        goods.setGbDgGoodsDetail(detail);
        goods.setGbDgNxDistributerId(-1);
        goods.setGbDgNxDistributerGoodsId(-1);
        goods.setGbDgGoodsStatus(0);
        goods.setGbDgGoodsIsWeight(0);
        goods.setGbDgPullOff(0);
        goods.setGbDgGbSupplierId(-1);
        goods.setGbDgControlFresh(0);
        goods.setGbDgControlPrice(0);
        goods.setGbDgGoodsInventoryType(1);
        goods.setGbDgIsFranchisePrice(0);
        goods.setGbDgIsSelfControl(0);

        save(goods);

        Integer gbDfgGoodsAmount = fatherGoodsEntity.getGbDfgGoodsAmount();
        fatherGoodsEntity.setGbDfgGoodsAmount((gbDfgGoodsAmount == null ? 0 : gbDfgGoodsAmount) + 1);
        dgfService.update(fatherGoodsEntity);

        return goods;
    }


    /**
     * 按农鑫目录节点 id + 批发商 + 本地分类层级查找已有 {@code gb_distributer_father_goods}，
     * 避免仅按名称 LIKE 时「二级分类」与「品名父」同名（如均为「鲜牛肉」）误命中刚插入的 level=2 节点。
     */
    private GbDistributerFatherGoodsEntity findDisFatherGoodsByNxLevel(int disId, Integer nxGoodsId, int level) {
        if (nxGoodsId == null) {
            return null;
        }
        List<GbDistributerFatherGoodsEntity> list = dgfService.queryDisFathersGoodsByNxGoodsId(nxGoodsId);
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (GbDistributerFatherGoodsEntity e : list) {
            if (e.getGbDfgDistributerId() == null || !Objects.equals(e.getGbDfgDistributerId(), disId)) {
                continue;
            }
            if (e.getGbDfgFatherGoodsLevel() == null || e.getGbDfgFatherGoodsLevel() != level) {
                continue;
            }
            return e;
        }
        return null;
    }

    /** 名称检索结果中挑选二级分类（level=1）；排除刚创建的品名父；优先 nx 与目录 grand 一致。 */
    private static GbDistributerFatherGoodsEntity pickGrandFromAmbiguousNameHits(
            List<GbDistributerFatherGoodsEntity> hits,
            int excludeNewFatherGoodsId,
            Integer nxGrandId) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        GbDistributerFatherGoodsEntity byNx = null;
        GbDistributerFatherGoodsEntity anyLevel1 = null;
        for (GbDistributerFatherGoodsEntity e : hits) {
            if (e == null || e.getGbDistributerFatherGoodsId() == null) {
                continue;
            }
            if (e.getGbDistributerFatherGoodsId() == excludeNewFatherGoodsId) {
                continue;
            }
            if (e.getGbDfgFatherGoodsLevel() == null || e.getGbDfgFatherGoodsLevel() != 1) {
                continue;
            }
            if (anyLevel1 == null) {
                anyLevel1 = e;
            }
            if (nxGrandId != null && nxGrandId.equals(e.getGbDfgNxGoodsId())) {
                byNx = e;
            }
        }
        return byNx != null ? byNx : anyLevel1;
    }

    private static GbDistributerFatherGoodsEntity pickGreatGrandFromAmbiguousNameHits(
            List<GbDistributerFatherGoodsEntity> hits,
            Integer nxGreatGrandId) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        GbDistributerFatherGoodsEntity byNx = null;
        GbDistributerFatherGoodsEntity anyLevel0 = null;
        for (GbDistributerFatherGoodsEntity e : hits) {
            if (e == null || e.getGbDistributerFatherGoodsId() == null) {
                continue;
            }
            if (e.getGbDfgFatherGoodsLevel() == null || e.getGbDfgFatherGoodsLevel() != 0) {
                continue;
            }
            if (anyLevel0 == null) {
                anyLevel0 = e;
            }
            if (nxGreatGrandId != null && nxGreatGrandId.equals(e.getGbDfgNxGoodsId())) {
                byNx = e;
            }
        }
        return byNx != null ? byNx : anyLevel0;
    }

    /**
     * 持久化批发商商品并维护父级分类树（与老项目逻辑一致）。
     */
    private GbDistributerGoodsEntity persistDistributerGoodsWithFatherHierarchy(GbDistributerGoodsEntity goods) {
        Integer nxGoodsId = goods.getGbDgNxGoodsId();
        NxGoodsEntity nxGoodsEntity = nxGoodsService.queryObject(nxGoodsId);
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

            save(goods);

            String grandName = goods.getGbDgNxGrandName();
            GbDistributerFatherGoodsEntity existingGrand =
                    findDisFatherGoodsByNxLevel(GbDgDistributerId, goods.getGbDgNxGrandId(), 1);
            if (existingGrand == null) {
                Map<String, Object> map2 = new HashMap<>();
                map2.put("disId", GbDgDistributerId);
                map2.put("fathersFatherName", grandName);
                List<GbDistributerFatherGoodsEntity> grandGoodsFather = dgfService.queryHasDisFathersFather(map2);
                existingGrand = pickGrandFromAmbiguousNameHits(
                        grandGoodsFather, distributerFatherGoodsId, goods.getGbDgNxGrandId());
            }

            if (existingGrand != null) {
                GbDistributerFatherGoodsEntity gbDistributerFatherGoodsEntity = existingGrand;
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

                String greatGrandName = goods.getGbDgNxGreatGrandName();
                GbDistributerFatherGoodsEntity existingGreatGrand =
                        findDisFatherGoodsByNxLevel(GbDgDistributerId, goods.getGbDgNxGreatGrandId(), 0);
                if (existingGreatGrand == null) {
                    Map<String, Object> map3 = new HashMap<>();
                    map3.put("disId", GbDgDistributerId);
                    map3.put("fathersFatherName", greatGrandName);
                    List<GbDistributerFatherGoodsEntity> greatGrandGoodsFather = dgfService.queryHasDisFathersFather(map3);
                    existingGreatGrand = pickGreatGrandFromAmbiguousNameHits(
                            greatGrandGoodsFather, goods.getGbDgNxGreatGrandId());
                }

                if (existingGreatGrand != null) {
                    GbDistributerFatherGoodsEntity gbDistributerFatherGoodsEntity = existingGreatGrand;
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
