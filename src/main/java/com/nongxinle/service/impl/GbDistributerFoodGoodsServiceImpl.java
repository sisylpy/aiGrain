package com.nongxinle.service.impl;

import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import com.nongxinle.mapper.GbDistributerFoodGoodsMapper;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.service.GbDistributerFoodGoodsService;

import static com.nongxinle.utils.DateUtils.formatWhatDay;


@Service("gbDistributerFoodGoodsService")
public class GbDistributerFoodGoodsServiceImpl implements GbDistributerFoodGoodsService {
	@Autowired
	private GbDistributerFoodGoodsMapper gbDistributerFoodGoodsMapper;
	
	@Override
	public GbDistributerFoodGoodsEntity queryObject(Integer gbDistributerFoodGoodsId){
		return gbDistributerFoodGoodsMapper.queryObject(gbDistributerFoodGoodsId);
	}
	
	@Override
	public List<GbDistributerFoodGoodsEntity> queryList(Map<String, Object> map){
		return gbDistributerFoodGoodsMapper.queryList(map);
	}
	
	@Override
	public int queryTotal(Map<String, Object> map){
		return gbDistributerFoodGoodsMapper.queryTotal(map);
	}
	
	@Override
	public void save(GbDistributerFoodGoodsEntity gbDistributerFoodGoods){
		gbDistributerFoodGoodsMapper.save(gbDistributerFoodGoods);
	}
	
	@Override
	public void update(GbDistributerFoodGoodsEntity gbDistributerFoodGoods){
		gbDistributerFoodGoodsMapper.update(gbDistributerFoodGoods);
	}
	
	@Override
	public void delete(Integer gbDistributerFoodGoodsId){
		gbDistributerFoodGoodsMapper.delete(gbDistributerFoodGoodsId);
	}
	
	@Override
	public void deleteBatch(Integer[] gbDistributerFoodGoodsIds){
		gbDistributerFoodGoodsMapper.deleteBatch(gbDistributerFoodGoodsIds);
	}

    @Override
    public List<GbDistributerFoodGoodsEntity> queryFoodGoodsByParams(Map<String, Object> map) {
		return gbDistributerFoodGoodsMapper.queryFoodGoodsByParams(map);
    }

    @Override
    public List<GbDistributerFoodGoodsEntity> queryFoodGoodsByFoodId(Integer foodId) {
        return gbDistributerFoodGoodsMapper.queryFoodGoodsByFoodId(
                foodId, formatWhatDay(-29), formatWhatDay(0));
    }

    @Override
    public List<GbDistributerFoodGoodsEntity> queryFoodGoodsByDisGoodsId(Integer disGoodsId, Integer disId) {
        return gbDistributerFoodGoodsMapper.queryFoodGoodsByDisGoodsId(disGoodsId, disId);
    }

}