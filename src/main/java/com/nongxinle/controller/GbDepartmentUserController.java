package com.nongxinle.controller;

import com.alibaba.fastjson2.JSONObject;
import com.nongxinle.common.result.Result;
import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.MyAPPIDConfig;
import com.nongxinle.utils.WeChatUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 订货部门用户Controller
 */
@RestController
@RequestMapping("gbdepartmentuser")
public class GbDepartmentUserController {

    @Autowired
    private GbDepartmentUserService gbDepartmentUserService;
    @Autowired
    private GbDepartmentService gbDepartmentService;
    @Autowired
    private GbDistributerService gbDistributerService;
    @Autowired
    private NxJrdhUserService nxJrdhUserService;
    @Autowired
    private NxJrdhSupplierService nxJrdhSupplierService;
    @Autowired
    private GbDistributerPurchaseBatchService gbDisPurchaseBatchService;

    /**
     * GB登录接口
     * 根据微信code获取openId，查询用户信息
     */
    @GetMapping("/gbLoginIndex/{code}")
    public Result gbLoginIndex(@PathVariable String code) {
        // 获取微信小程序配置
        MyAPPIDConfig myAPPIDConfig = new MyAPPIDConfig();
        String appId = myAPPIDConfig.getTexiansongCaigouAppId();
        String secret = myAPPIDConfig.getTexiansongCaigouScreat();

        // 调用微信接口获取openId
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + appId + "&secret=" +
                secret + "&js_code=" + code +
                "&grant_type=authorization_code";
        String str = WeChatUtil.httpRequest(url, "GET", null);
        JSONObject jsonObject = JSONObject.parseObject(str);
        String openId = jsonObject.get("openid").toString();

        if (openId != null && !openId.trim().isEmpty()) {
            // 查询部门用户
            GbDepartmentUserEntity departmentUserEntity = gbDepartmentUserService.queryDepUserByOpenId(openId);

            if (departmentUserEntity != null) {
                // 查询批发商信息
                // 查询完整批发商信息（含所有部门列表）
                GbDistributerEntity gbDistributerEntity = gbDistributerService.queryDistributerWithAllDepartments(departmentUserEntity.getGbDuDistributerId());
                // 查询部门信息
                GbDepartmentEntity gbDepartmentEntity = gbDepartmentService.queryDepInfoGb(departmentUserEntity.getGbDuDepartmentId());

                Map<String, Object> data = new HashMap<>();
                data.put("depUserInfo", departmentUserEntity);
                data.put("disInfo", gbDistributerEntity);
                data.put("depInfo", gbDepartmentEntity);
                return Result.ok().put("data", data);
            } else {
                System.out.println("jrjrrjjrjrjr" + openId);
                // 检查是否是今日达用户
                NxJrdhUserEntity jrdhUserEntity = nxJrdhUserService.queryWhichUserByOpenId(openId);
                if (jrdhUserEntity != null) {
                    return Result.ok().put("data", "noBuyer");
                }
                return Result.error(-1, "请向管理员索要注册邀请");
            }
        } else {
            return Result.error(-1, "请进行注册");
        }
    }

    /**
     * 删除部门用户
     */
    @RequestMapping(value = "/deleteDepUser/{userId}")
    @ResponseBody
    public Result deleteDepUser(@PathVariable Integer userId) {
        boolean removed = gbDepartmentUserService.removeById(userId);
        if (removed) {
            return Result.ok();
        }
        return Result.error(-1, "用户不存在");
    }

    /**
     * 今日达用户登录接口
     * 根据微信code获取openId，查询今日达用户信息
     */
    @PostMapping("/whichJrdhUserLoginGbJj")
    public Result whichJrdhUserLoginGbJj(String code, Integer gbDisId, Integer batchId,
                                         Integer gbDepId, Integer buyUserId) {
        System.out.println("whiciciiicic");
        // 获取微信小程序配置
        MyAPPIDConfig myAPPIDConfig = new MyAPPIDConfig();
        String appId = myAPPIDConfig.getTexiansongCaigouAppId();
        String secret = myAPPIDConfig.getTexiansongCaigouScreat();

        // 调用微信接口获取openId
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + appId + "&secret=" +
                secret + "&js_code=" + code +
                "&grant_type=authorization_code";
        String str = WeChatUtil.httpRequest(url, "GET", null);
        JSONObject jsonObject = JSONObject.parseObject(str);
        String openId = jsonObject.get("openid").toString();

        if (openId != null && !openId.trim().isEmpty()) {
            Map<String, Object> mapB = new HashMap<>();
            mapB.put("batchId", batchId);
            GbDistributerPurchaseBatchEntity batchEntity = gbDisPurchaseBatchService.getById(batchId);
            Map<String, Object> map = new HashMap<>();
            GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(gbDisId);

            map.put("disInfo", gbDistributerEntity);

            // 首先判断是不是dis 用户
            Map<String, Object> mapUser = new HashMap<>();
            mapUser.put("openId", openId);
            mapUser.put("admin", 2);
            GbDepartmentUserEntity caigouUser = gbDepartmentUserService.queryDepUsersByOpenIdAndAdmin(mapUser);
            if (caigouUser != null) {
                map.put("userInfo", caigouUser);
                map.put("buyUser", true);
                map.put("supplierInfo", null);
                map.put("code", 1);
            } else {
                Map<String, Object> mapUserSell = new HashMap<>();
                mapUserSell.put("openId", openId);
                mapUserSell.put("admin", 3);
                NxJrdhUserEntity jrdhUserEntitySell = nxJrdhUserService.queryJrdhUserByParams(mapUserSell);
                if (jrdhUserEntitySell != null) {
                    Map<String, Object> mapS = new HashMap<>();
                    mapS.put("gbDisId", gbDisId);
                    mapS.put("userId", jrdhUserEntitySell.getNxJrdhUserId());
                    NxJrdhSupplierEntity nxJrdhSupplierEntity = nxJrdhSupplierService.querySellUserSupplier(mapS);
                    if (nxJrdhSupplierEntity == null) {
                        NxJrdhSupplierEntity supplierEntity = saveJrdhSupplerGb(jrdhUserEntitySell, gbDisId, gbDepId, buyUserId);
                        map.put("supplierInfo", supplierEntity);
                    } else {
                        map.put("supplierInfo", nxJrdhSupplierEntity);
                    }
                    map.put("buyUser", false);
                    map.put("userInfo", jrdhUserEntitySell);
                    map.put("code", 1);
                } else {
                    map.put("code", -1);
                    return Result.ok().put("data", map);
                }
            }

            if (batchEntity != null) {
                map.put("batch", batchEntity);
                return Result.ok().put("data", map);
            } else {
                map.put("batch", -1);
                return Result.ok().put("data", map);
            }
        } else {
            return Result.error(-1, "注册失败");
        }
    }

    private NxJrdhSupplierEntity saveJrdhSupplerGb(NxJrdhUserEntity jrdhUserEntity, Integer gbDisId,
                                                   Integer gbDepId, Integer buyUserId) {
        NxJrdhSupplierEntity supplierEntity = new NxJrdhSupplierEntity();
        supplierEntity.setNxJrdhsUserId(jrdhUserEntity.getNxJrdhUserId());
        supplierEntity.setNxJrdhsGbDistributerId(gbDisId);
        supplierEntity.setNxJrdhsSupplierName(jrdhUserEntity.getNxJrdhWxNickName());
        supplierEntity.setNxJrdhsNxCommunityId(-1);
        supplierEntity.setNxJrdhsNxPurUserId(-1);
        supplierEntity.setNxJrdhsGbDepartmentId(gbDepId);
        supplierEntity.setNxJrdhsNxDistributerId(-1);
        nxJrdhSupplierService.save(supplierEntity);
        return supplierEntity;
    }

}
