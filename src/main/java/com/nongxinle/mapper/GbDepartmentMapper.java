package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDepartmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 订货部门Mapper接口
 */
@Mapper
public interface GbDepartmentMapper extends BaseMapper<GbDepartmentEntity> {

    /**
     * 查询子部门
     * @param depFatherId 父部门ID
     * @return 子部门列表
     */
    List<GbDepartmentEntity> querySubDepartments(@Param("depFatherId") Integer depFatherId);

    /**
     * 根据分销商ID查询分组部门（门店）
     * @param map 包含disId和depType的Map
     * @return 部门列表
     */
    List<GbDepartmentEntity> queryGroupDepsByDisId(Map<String, Object> map);

}
