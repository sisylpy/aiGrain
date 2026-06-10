package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 批发商商品Service接口
 * 注意：老项目没有继承 IService，是直接定义的接口
 */
public interface GbDistributerGoodsService extends IService<GbDistributerGoodsEntity> {
    
    // 老项目的 queryObject 方法
    default GbDistributerGoodsEntity queryObject(Integer gbDistributerGoodsId) {
        return getById(gbDistributerGoodsId);
    }
    
    List<GbDistributerGoodsEntity> queryDisGoodsByParams(Map<String, Object> map);

    /**
     * 批发商商品快速搜索
     * 支持中文字名称、拼音、拼音首字母、别名搜索
     */
    List<GbDistributerGoodsEntity> queryGbDisGoodsQuickSearchStr(Map<String, Object> map);

    /**
     * 从农鑫（Nx）商品创建批发商商品：落库商品、父级分类树、别名与规格。
     * <p>业务规则与字段赋值与老项目保持一致。
     *
     * @param gbDisId   批发商 ID
     * @param depId     批发商侧部门 ID（写入商品的 gbDgGbDepartmentId）
     * @param nxGoodsId 农鑫商品 ID
     * @return 已持久化后的批发商商品（含生成的分类 ID）
     */
    GbDistributerGoodsEntity createDistributerGoodsFromNxGoods(Integer gbDisId, Integer depId, Integer nxGoodsId);

    /**
     * 保存批发商临时自建商品：可选图片、落库商品、更新父分类数量、写入部门分销商品。
     *
     * @param file     商品图，可空
     * @param toDepId  写入商品的 gbDgGbDepartmentId
     * @param depId    部门主键，用于部门分销商品的父子部门 ID
     */
    GbDistributerGoodsEntity saveLinshiGoodsGb(MultipartFile file, String goodsName, String standard, String detail,
                                               Integer disId, Integer toDepId, Integer depId, Integer depFatherId,
                                               String standardWeight, String cartonUnit, String itemsPerCarton);

    // 老项目的 update 方法，使用 default 委托给 updateById
    default boolean update(GbDistributerGoodsEntity entity) {
        return updateById(entity);
    }
    
    // 老项目的 delete 方法
    default boolean delete(Integer gbDistributerGoodsId) {
        return removeById(gbDistributerGoodsId);
    }
}
