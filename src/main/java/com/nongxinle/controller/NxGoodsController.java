package com.nongxinle.controller;

import com.nongxinle.dto.NxGoodsByGreatGrandIdDTO;
import com.nongxinle.entity.NxGoodsEntity;
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
import java.util.stream.Collectors;

/**
 * 农鑫商品Controller
 */
@RestController
@RequestMapping("nxgoods")
@Tag(name = "农鑫商品管理", description = "农鑫商品分类和商品列表查询接口")
public class NxGoodsController {

    private static final Logger log = LoggerFactory.getLogger(NxGoodsController.class);

    @Autowired
    private NxGoodsService nxGoodsService;

    /**
     * 获取农鑫商品分类和商品ID列表
     * 接口: /nxgoods/gbDepGetNxCataGoods
     */
    @Operation(summary = "获取商品分类树", description = "获取农鑫商品的一级和二级分类树结构，以及一级分类下包含的所有商品ID列表")
    @RequestMapping(value = "/gbDepGetNxCataGoods", method = RequestMethod.POST)
    public R gbDepGetNxCataGoods() {
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
        List<Integer> departmentDisGoodsEntities = nxGoodsService.queryOnlyGoodsIds(map);
        
        mapR.put("cataArr", groupedList);
        mapR.put("depGoodsArr", departmentDisGoodsEntities);
        
        return R.ok().put("data", mapR);
    }

    /**
     * 根据父级分类获取商品分页列表
     * 接口: /nxgoods/gbDepGetNxFatherGoods
     */
    /**
     * 按一级分类分页查询商品
     * 
     * 原接口: gbDepGetNxFatherGoods（已废弃，名称有误导性）
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
        
        // 查询总数
        int total = nxGoodsService.queryNxGoodsCountByGreatGrandId(map);
        log.info("【gbDepGetNxGoodsByGreatGrandId】查询总数: {}", total);
        
        // 查询当前页数据（完整查询，包含关联对象）
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
