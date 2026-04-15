package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDepartmentUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订货部门用户Mapper接口
 */
@Mapper
public interface GbDepartmentUserMapper extends BaseMapper<GbDepartmentUserEntity> {

    /**
     * 根据微信OpenId查询部门用户
     */
    @Select("SELECT * FROM gb_department_user WHERE gb_DU_wx_open_id = #{openId}")
    GbDepartmentUserEntity queryDepUserByOpenId(@Param("openId") String openId);

    /**
     * 根据部门ID查询所有用户
     */
    @Select("SELECT * FROM gb_department_user WHERE gb_DU_department_id = #{depId}")
    List<GbDepartmentUserEntity> queryAllUsersByDepId(@Param("depId") Integer depId);

}
