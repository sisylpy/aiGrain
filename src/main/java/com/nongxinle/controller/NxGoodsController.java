package com.nongxinle.controller;

import com.nongxinle.dto.NxGoodsByGreatGrandIdDTO;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.NxGoodsEntity;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.NxGoodsService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.nongxinle.utils.DateUtils.formatWhatDay;
import static com.nongxinle.utils.PinYin4jUtils.hanziToPinyin;

/**
 * 农鑫商品Controller
 */
@RestController
@RequestMapping("nxgoods")
@Tag(name = "农鑫商品管理", description = "农鑫商品分类和商品列表查询接口")
public class NxGoodsController {

    private static final Logger log = LoggerFactory.getLogger(NxGoodsController.class);

    /** 快速检索单次返回给前端的商品条数上限，避免 payload 过大 */
    private static final int QUICK_SEARCH_MAX_DISPLAY = 50;

    @Autowired
    private NxGoodsService nxGoodsService;
    @Autowired
    private GbDepartmentDisGoodsService gbDepDisGoodsService;


    /**
     * @param searchStr 搜索字符串F
     * @param disId     批发商id
     * @return 搜索结果 queryGbDepartmentGoodsByQuickSearchGb
     */
    @RequestMapping(value = "/queryDepDisGoodsByQuickSearchGb", method = RequestMethod.POST)
    @ResponseBody
    public R queryDepDisGoodsByQuickSearchGb(String searchStr, Integer disId, String depId) {

        Map<String, Object> map3 = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        map.put("gbDepId", depId);
        map.put("gbDisId", disId);
        map.put("isHidden", 0);
        Map<String, Object> mapD = new HashMap<>();

        if (searchStr == null || searchStr.trim().isEmpty()) {
            map3.put("dep", Collections.emptyList());
            map3.put("dis", Collections.emptyList());
            map3.put("depMatchTotal", 0);
            map3.put("depShownCount", 0);
            map3.put("disMatchTotal", 0);
            map3.put("disShownCount", 0);
            map3.put("maxDisplay", QUICK_SEARCH_MAX_DISPLAY);
            map3.put("resultsLimited", false);
            return R.ok().put("data", map3);
        }
        searchStr = searchStr.trim();
        /*
         * 与 NxGoodsMapper / GbDepartmentDisGoodsMapper 约定一致：
         * - searchStr：原始关键字（中文整串或拉丁整串），供名称 / 别名 LIKE、精确匹配名称等。
         * - searchPinyin：含汉字时为整串转拼音（与库字段格式一致时用于精确/模糊）；纯拉丁时与 searchStr 相同，
         *   保证「名称模糊」SQL 的 #{searchStr} 与「拼音模糊」的 #{searchPinyin} 均有值，避免只带 isHidden+level 扫全表。
         */
        boolean containsHan = searchStr.matches(".*[\\u4E00-\\u9FFF].*");
        if (containsHan) {
            map.put("searchStr", searchStr);
            mapD.put("searchStr", searchStr);
            String pinyin = hanziToPinyin(searchStr);
            map.put("searchPinyin", pinyin);
            mapD.put("searchPinyin", pinyin);
        } else {
            map.put("searchStr", searchStr);
            mapD.put("searchStr", searchStr);
            map.put("searchPinyin", searchStr);
            mapD.put("searchPinyin", searchStr);
        }

        mapD.put("depId",depId);
        mapD.put("date", formatWhatDay(0));
        System.out.println("depserchmapDmapD" + mapD);
        TreeSet<GbDepartmentDisGoodsEntity> disGoodsEntityTreeSet = gbDepDisGoodsService.queryDepDisGoodsQuickSearchStrGb(mapD);
        List<GbDepartmentDisGoodsEntity> depList = new ArrayList<>(disGoodsEntityTreeSet);
        int depTotal = depList.size();
        boolean depLimited = depTotal > QUICK_SEARCH_MAX_DISPLAY;
        if (depLimited) {
            depList = new ArrayList<>(depList.subList(0, QUICK_SEARCH_MAX_DISPLAY));
        }
        map3.put("dep", depList);
        map3.put("depMatchTotal", depTotal);
        map3.put("depShownCount", depList.size());

        List<NxGoodsEntity> equalEntitiesEx = nxGoodsService.queryDisGoodsEqualSearchStrWithDepOrders(map);
        List<NxGoodsEntity> nxGoodsEntities = nxGoodsService.queryDisGoodsQuickSearchStrWithDepOrders(map);
        List<NxGoodsEntity> nxGoodsEntitiesPyin = nxGoodsService.queryDisGoodsQuickSearchPyWithDepOrders(map);

        nxGoodsEntities.removeAll(equalEntitiesEx);

        nxGoodsEntitiesPyin.removeAll(equalEntitiesEx);
        nxGoodsEntitiesPyin.removeAll(nxGoodsEntities);

        List<NxGoodsEntity> combinedList = new ArrayList<>();
        combinedList.addAll(equalEntitiesEx);
        combinedList.addAll(nxGoodsEntities);
        nxGoodsEntitiesPyin.removeAll(combinedList);
        combinedList.addAll(nxGoodsEntitiesPyin);

        int disTotal = combinedList.size();
        boolean disLimited = disTotal > QUICK_SEARCH_MAX_DISPLAY;
        if (disLimited) {
            combinedList = new ArrayList<>(combinedList.subList(0, QUICK_SEARCH_MAX_DISPLAY));
        }
        map3.put("dis", combinedList);
        map3.put("disMatchTotal", disTotal);
        map3.put("disShownCount", combinedList.size());
        map3.put("maxDisplay", QUICK_SEARCH_MAX_DISPLAY);
        if (depLimited || disLimited) {
            map3.put("resultsLimited", true);
            map3.put(
                    "refineSearchMessage",
                    "匹配结果过多（已超过 " + QUICK_SEARCH_MAX_DISPLAY + " 条），本次仅返回前 "
                            + QUICK_SEARCH_MAX_DISPLAY
                            + " 条。请缩小关键词或输入更准确的商品名称 / 拼音后再搜。");
        } else {
            map3.put("resultsLimited", false);
        }

        return R.ok().put("data", map3);
    }



    /**
     * 获取农鑫商品分类和商品ID列表
     * 接口: /nxgoods/gbGetNxGoodsCataGoods
     */
    @Operation(summary = "获取商品分类树", description = "获取农鑫商品的一级和二级分类树结构，以及一级分类下包含的所有商品ID列表")
    @RequestMapping(value = "/gbGetNxGoodsCataGoods", method = RequestMethod.POST)
    public R gbGetNxGoodsCataGoods() {
        // 获取分类（平铺数据：一级分类+二级分类）
        List<NxGoodsEntity> nxGoodsEntities = nxGoodsService.getiBookCoverData();
        
        Map<String, Object> mapR = new HashMap<>();
        
        // 空值检查
        if (nxGoodsEntities == null || nxGoodsEntities.isEmpty()) {
            mapR.put("cataArr", nxGoodsEntities);
            mapR.put("depGoodsArr", new ArrayList<>());
            return R.ok().put("data", mapR);
        }
        
        // 按一级分类分组，填充 nxGoodsEntityList
        Map<Integer, NxGoodsEntity> grandMap = new LinkedHashMap<>();
        for (NxGoodsEntity entity : nxGoodsEntities) {
            Integer parentId = entity.getNxGoodsId();
            if (!grandMap.containsKey(parentId)) {
                grandMap.put(parentId, entity);
                entity.setNxGoodsEntityList(new ArrayList<>());
            }
            // 添加子分类到 nxGoodsEntityList
            NxGoodsEntity subEntity = new NxGoodsEntity();
            subEntity.setNxGoodsId(entity.getSubNxGoodsId());
            subEntity.setNxGoodsName(entity.getSubNxGoodsName());
            subEntity.setNxGoodsDetail(entity.getSubNxGoodsDetail());
            subEntity.setNxGoodsFile(entity.getSubNxGoodsFile());
            subEntity.setNxGoodsFatherId(entity.getSubNxGoodsFatherId());
            subEntity.setNxGoodsSort(entity.getSubNxGoodsSort());
            grandMap.get(parentId).getNxGoodsEntityList().add(subEntity);
        }
        
        // 转换为列表
        List<NxGoodsEntity> groupedList = new ArrayList<>(grandMap.values());
        
        Map<String, Object> map = new HashMap<>();
        map.put("isHidden", 0);
        Integer nxGoodsId = groupedList.get(0).getNxGoodsId();
        map.put("greatGrandId", nxGoodsId);
        
        // 获取商品ID列表
        List<Integer> nxGoodsIdsBySort = nxGoodsService.queryOnlyGoodsIds(map);
        
        mapR.put("cataArr", groupedList);
        mapR.put("nxGoodsIdsSort", nxGoodsIdsBySort);
        
        return R.ok().put("data", mapR);
    }


    /**
     * 
     * @param greatGrandId 一级分类ID
     * @param depId 部门ID
     * @param disId 批发商ID
     * @param limit 每页数量
     * @param page 当前页码
     */
    @Operation(summary = "按一级分类分页查询商品", 
               description = "根据指定的一级分类(greatGrandId)分页查询该分类下的所有商品列表（含GB部门订单等关联对象）")
    @RequestMapping(value = "/gbDepGetNxGoodsByGreatGrandId", method = RequestMethod.POST)
    public R gbDepGetNxGoodsByGreatGrandId(
            @RequestParam Integer greatGrandId,
            @RequestParam Integer depId,
            @RequestParam Integer disId,
            @RequestParam(required = false, defaultValue = "15") Integer limit,
            @RequestParam(required = false, defaultValue = "1") Integer page) {
        
        log.info("【gbDepGetNxGoodsByGreatGrandId】请求参数: greatGrandId={}, depId={}, disId={}, limit={}, page={}", 
                greatGrandId, depId, disId, limit, page);
        
        Map<String, Object> map = new HashMap<>();
        map.put("gbDepId", depId);
        map.put("gbDisId", disId);
        map.put("greatGrandId", greatGrandId);
        map.put("limit", limit);
        map.put("offset", (page - 1) * limit);
        map.put("isHidden", 0);

        // 查询总数
        int total = nxGoodsService.queryNxGoodsCountByGreatGrandId(map);
        log.info("【gbDepGetNxGoodsByGreatGrandId】查询总数: {}", total);
        
        // 查询当前页数据（完整查询，包含关联对象）
        log.info("【gbDepGetNxGoodsByGreatGrandIdmap】查询到商品数量aaa: {}", map);

        List<NxGoodsEntity> list = nxGoodsService.queryGbDepNxGrandGoodsByGreatId(map);
        log.info("【gbDepGetNxGoodsByGreatGrandId】查询到商品数量aaa: {}", list != null ? list.size() : 0);
        
        // 打印第一个商品的全部属性
        if (list != null && !list.isEmpty()) {
            NxGoodsEntity goods = list.get(0);
            log.info("【商品全部属性】{}", goods);
        }

        // 返回分页数据
        Map<String, Object> pageMap = new HashMap<>();
        pageMap.put("totalCount", total);
        pageMap.put("pageSize", limit);
        pageMap.put("totalPage", (total + limit - 1) / limit);
        pageMap.put("currPage", page);
        pageMap.put("list", list);
        
        return R.ok().put("page", pageMap);
    }
}
