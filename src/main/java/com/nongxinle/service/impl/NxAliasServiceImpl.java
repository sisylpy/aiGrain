package com.nongxinle.service.impl;

import com.nongxinle.entity.NxGoodsEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.nongxinle.mapper.NxAliasMapper;
import com.nongxinle.entity.NxAliasEntity;
import com.nongxinle.service.NxAliasService;

/**
 * 农鑫商品别名Service实现
 */
@Service("nxAliasService")
public class NxAliasServiceImpl implements NxAliasService {
	@Autowired
	private NxAliasMapper nxAliasMapper;
	
	@Override
	public NxAliasEntity queryObject(Integer nxAliasId){
		return nxAliasMapper.selectById(nxAliasId);
	}
	
	@Override
	public List<NxAliasEntity> queryList(Map<String, Object> map){
		return null;
	}
	
	@Override
	public int queryTotal(Map<String, Object> map){
		return 0;
	}
	
	@Override
	public void save(NxAliasEntity nxAlias){
		nxAliasMapper.insert(nxAlias);
	}
	
	@Override
	public void update(NxAliasEntity nxAlias){
		nxAliasMapper.updateById(nxAlias);
	}
	
	@Override
	public void delete(Integer nxAliasId){
		nxAliasMapper.deleteById(nxAliasId);
	}
	
	@Override
	public void deleteBatch(Integer[] nxAliasIds){
		nxAliasMapper.deleteBatchIds(Arrays.asList(nxAliasIds));
	}

    @Override
    public List<NxAliasEntity> queryNxAliasList(Map<String, Object> map) {
		return nxAliasMapper.queryNxAliasList(map);
    }

    @Override
    public List<NxGoodsEntity> queryNxGoodsByName(Map<String, Object> map) {
		return nxAliasMapper.queryNxGoodsByName(map);
    }

}
