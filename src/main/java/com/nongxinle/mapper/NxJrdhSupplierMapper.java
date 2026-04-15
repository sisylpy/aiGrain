package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 今日达供应商Mapper接口
 */
@Mapper
public interface NxJrdhSupplierMapper extends BaseMapper<NxJrdhSupplierEntity> {

    /**
     * 根据批发商ID和用户ID查询供应商
     */
    @Select("SELECT * FROM nx_jrdh_supplier WHERE nx_jrdhs_gb_distributer_id = #{gbDisId} AND nx_jrdhs_user_id = #{userId}")
    NxJrdhSupplierEntity querySellUserSupplier(Map<String, Object> map);

    /**
     * 根据用户ID查询供应商及其批发商信息（老项目兼容方法）
     */
    List<NxJrdhSupplierEntity> queryJrdhSupplerWithDisByUserId(Map<String, Object> map);

}
