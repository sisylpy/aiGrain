package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.NxJrdhUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 今日达用户Mapper接口
 */
@Mapper
public interface NxJrdhUserMapper extends BaseMapper<NxJrdhUserEntity> {

    /**
     * 根据微信OpenId查询用户
     */
    @Select("SELECT * FROM nx_jrdh_user WHERE nx_jrdh_wx_open_id = #{openId}")
    NxJrdhUserEntity queryWhichUserByOpenId(@Param("openId") String openId);

    /**
     * 根据管理员参数查询用户
     */
    @Select("<script>" +
            "SELECT * FROM nx_jrdh_user " +
            "<where>" +
            "   <if test='openId != null'>" +
            "       AND nx_jrdh_wx_open_id = #{openId}" +
            "   </if>" +
            "   <if test='admin != null'>" +
            "       AND nx_jrdh_admin = #{admin}" +
            "   </if>" +

            "   <if test='gbDisId != null'>" +
            "       AND nx_jrdh_gb_distributer_id = #{gbDisId}" +
            "   </if>" +
            "</where>" +
            "</script>")
    NxJrdhUserEntity queryJrdhUserByAdmin(Map<String, Object> map);

    /**
     * 根据参数查询今日达用户（openId和admin）
     */
    @Select("<script>" +
            "SELECT * FROM nx_jrdh_user " +
            "<where>" +
            "   <if test='openId != null'>" +
            "       AND nx_jrdh_wx_open_id = #{openId}" +
            "   </if>" +
            "   <if test='admin != null'>" +
            "       AND nx_jrdh_admin = #{admin}" +
            "   </if>" +
            "</where>" +
            "</script>")
    NxJrdhUserEntity queryJrdhUserByParams(Map<String, Object> map);

}
