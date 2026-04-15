package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.NxStandardEntity;

import java.util.List;
import java.util.Map;

/**
 * 农鑫商品规格Service接口
 */
public interface NxStandardService extends IService<NxStandardEntity> {

    /**
     * 根据商品ID查询规格列表
     */
    List<NxStandardEntity> queryGoodsStandardListByGoodId(Integer nxGoodsId);

    /**
     * 根据商品ID查询规格列表
     */
    List<NxStandardEntity> queryList(Integer nxGoodsId);
}
