package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.mapper.GbDepFoodSalesMapper;
import com.nongxinle.service.GbDepFoodSalesService;
import org.springframework.stereotype.Service;

@Service
public class GbDepFoodSalesServiceImpl extends ServiceImpl<GbDepFoodSalesMapper, GbDepFoodSalesEntity>
        implements GbDepFoodSalesService {
}
