package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerAliasEntity;
import com.nongxinle.mapper.GbDistributerAliasMapper;
import com.nongxinle.service.GbDistributerAliasService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 批发商商品别名Service实现
 */
@Service("gbDistributerAliasService")
public class GbDistributerAliasServiceImpl implements GbDistributerAliasService {
	@Autowired
	private GbDistributerAliasMapper gbDistributerAliasMapper;
	
	@Override
	public GbDistributerAliasEntity queryObject(Integer gbDistributerAliasId){
		return gbDistributerAliasMapper.selectById(gbDistributerAliasId);
	}
	
	@Override
	public List<GbDistributerAliasEntity> queryList(Map<String, Object> map){
		return null;
	}
	
	@Override
	public int queryTotal(Map<String, Object> map){
		return 0;
	}
	
	@Override
	public void save(GbDistributerAliasEntity gbDistributerAlias){
		gbDistributerAliasMapper.insert(gbDistributerAlias);
	}
	
	@Override
	public void update(GbDistributerAliasEntity gbDistributerAlias){
		gbDistributerAliasMapper.updateById(gbDistributerAlias);
	}
	
	@Override
	public void delete(Integer gbDistributerAliasId){
		gbDistributerAliasMapper.deleteById(gbDistributerAliasId);
	}
	
	@Override
	public void deleteBatch(Integer[] gbDistributerAliasIds){
		gbDistributerAliasMapper.deleteBatchIds(Arrays.asList(gbDistributerAliasIds));
	}

	@Override
	public List<GbDistributerAliasEntity> queryDisAliasByDisGoodsId(Integer disGoodsId) {
		return gbDistributerAliasMapper.queryDisAliasByDisGoodsId(disGoodsId);
	}
}
