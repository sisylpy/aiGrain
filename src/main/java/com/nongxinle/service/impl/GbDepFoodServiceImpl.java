package com.nongxinle.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import com.nongxinle.mapper.GbDepFoodMapper;
import com.nongxinle.entity.GbDepFoodEntity;
import com.nongxinle.service.GbDepFoodService;


@Service("gbDepFoodService")
public class GbDepFoodServiceImpl implements GbDepFoodService {
	@Autowired
	private GbDepFoodMapper gbDepFoodMapper;
	
	@Override
	public GbDepFoodEntity queryObject(Integer gbDepFoodId){
		return gbDepFoodMapper.queryObject(gbDepFoodId);
	}
	
	@Override
	public List<GbDepFoodEntity> queryList(Map<String, Object> map){
		return gbDepFoodMapper.queryList(map);
	}
	
	@Override
	public int queryTotal(Map<String, Object> map){
		return gbDepFoodMapper.queryTotal(map);
	}
	
	@Override
	public void save(GbDepFoodEntity gbDepFood){
		gbDepFoodMapper.save(gbDepFood);
	}
	
	@Override
	public void update(GbDepFoodEntity gbDepFood){
		gbDepFoodMapper.update(gbDepFood);
	}
	
	@Override
	public void delete(Integer gbDepFoodId){
		gbDepFoodMapper.delete(gbDepFoodId);
	}
	
	@Override
	public void deleteBatch(Integer[] gbDepFoodIds){
		gbDepFoodMapper.deleteBatch(gbDepFoodIds);
	}

    @Override
    public List<GbDepFoodEntity> queryDepFoodByParams(Map<String, Object> map) {
		return gbDepFoodMapper.queryDepFoodByParams(map);
    }

    @Override
    public List<GbDepFoodEntity> queryDepAllFood(Map<String, Object> map) {
		return gbDepFoodMapper.queryDepAllFood(map);
    }

}
