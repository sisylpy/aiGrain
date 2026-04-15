package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.NxStandardEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 农鑫商品规格Mapper接口
 */
@Mapper
public interface NxStandardMapper extends BaseMapper<NxStandardEntity> {

    /**
     * 根据商品ID查询规格列表
     */
    List<NxStandardEntity> queryGoodsStandardListByGoodsId(@Param("nxGoodsId") Integer nxGoodsId);

    /**
     * 根据商品ID查询规格列表
     */
    List<NxStandardEntity> queryList(@Param("nxGoodsId") Integer nxGoodsId);
}
