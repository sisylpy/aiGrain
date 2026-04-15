package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.NxAliasEntity;
import com.nongxinle.entity.NxGoodsEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 农鑫商品别名Mapper接口
 */
@Mapper
public interface NxAliasMapper extends BaseMapper<NxAliasEntity> {

    /**
     * 根据商品ID查询别名列表
     */
    List<NxAliasEntity> queryNxAliasList(Map<String, Object> map);

    /**
     * 根据别名名称查询商品（包含别名信息）
     */
    List<NxGoodsEntity> queryNxGoodsByName(Map<String, Object> map);
}
