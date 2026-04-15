package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.mapper.NxStandardMapper;
import com.nongxinle.service.NxStandardService;
import com.nongxinle.entity.NxStandardEntity;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 农鑫商品规格Service实现
 */
@Service
public class NxStandardServiceImpl extends ServiceImpl<NxStandardMapper, NxStandardEntity> implements NxStandardService {

    @Override
    public List<NxStandardEntity> queryGoodsStandardListByGoodId(Integer nxGoodsId) {
        return baseMapper.queryGoodsStandardListByGoodsId(nxGoodsId);
    }

    @Override
    public List<NxStandardEntity> queryList(Integer nxGoodsId) {
        return baseMapper.queryList(nxGoodsId);
    }
}
