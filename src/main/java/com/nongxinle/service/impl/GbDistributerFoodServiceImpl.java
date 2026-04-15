package com.nongxinle.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import com.nongxinle.mapper.GbDistributerFoodMapper;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.service.GbDistributerFoodService;


@Service("gbDistributerFoodService")
public class GbDistributerFoodServiceImpl implements GbDistributerFoodService {
	@Autowired
	private GbDistributerFoodMapper gbDistributerFoodMapper;
	
	@Override
	public GbDistributerFoodEntity queryObject(Integer gbDistributerFoodId){
		return gbDistributerFoodMapper.queryObject(gbDistributerFoodId);
	}
	
	@Override
	public List<GbDistributerFoodEntity> queryList(Map<String, Object> map){
		return gbDistributerFoodMapper.queryList(map);
	}
	
	@Override
	public int queryTotal(Map<String, Object> map){
		return gbDistributerFoodMapper.queryTotal(map);
	}
	
	@Override
	public void save(GbDistributerFoodEntity gbDistributerFood){
		gbDistributerFoodMapper.save(gbDistributerFood);
	}
	
	@Override
	public void update(GbDistributerFoodEntity gbDistributerFood){
		gbDistributerFoodMapper.update(gbDistributerFood);
	}
	
	@Override
	public void delete(Integer gbDistributerFoodId){
		gbDistributerFoodMapper.delete(gbDistributerFoodId);
	}
	
	@Override
	public void deleteBatch(Integer[] gbDistributerFoodIds){
		gbDistributerFoodMapper.deleteBatch(gbDistributerFoodIds);
	}

    @Override
    public List<GbDistributerFoodEntity> queryFoodByParams(Map<String, Object> map) {
		return gbDistributerFoodMapper.queryFoodByParams(map);
    }

    @Override
    public List<GbDistributerFoodEntity> queryDisAllFood(Map<String, Object> map) {
		return gbDistributerFoodMapper.queryDisAllFood(map);
    }

}