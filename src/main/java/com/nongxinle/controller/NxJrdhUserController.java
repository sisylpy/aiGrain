package com.nongxinle.controller;

/**
 * @author lpy
 * @date 10-12 11:38
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSONObject;
import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;

import static com.nongxinle.utils.DateUtils.formatWhatDay;


@RestController
@RequestMapping("nxjrdhuser")
public class NxJrdhUserController {
    @Autowired
    private NxJrdhUserService nxJrdhUserService;

    @Autowired
    private NxJrdhSupplierService nxJrdhSupplierService;

    @Autowired
    private GbDistributerPurchaseBatchService gbDisPurchaseBatchService;

    @Autowired
    private GbDepartmentUserService gbDepartmentUserService;

    @RequestMapping(value = "/jrdhSellerRegisterWithFileGbJj", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public R jrdhSellerRegisterWithFileGbJj(@RequestParam("file") MultipartFile file,
                                            @RequestParam("userName") String userName,
                                            @RequestParam("code") String code,
                                            @RequestParam("admin") Integer admin,
                                            @RequestParam("gbDisId") Integer gbDisId,
                                            @RequestParam("buyUserId") Integer buyUserId,
                                            @RequestParam("gbDepId") Integer gbDepId,
                                            HttpSession session) {
        MyAPPIDConfig myAPPIDConfig = new MyAPPIDConfig();

        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + myAPPIDConfig.getTexiansongCaigouAppId() +
                "&secret=" + myAPPIDConfig.getTexiansongCaigouScreat() + "&js_code=" + code + "&grant_type=authorization_code";
        // 发送请求，返回Json字符串
        String str = WeChatUtil.httpRequest(url, "GET", null);

        // 转成Json对象 获取openidjrdhUserRegister
        JSONObject jsonObject = JSONObject.parseObject(str);

        // 我们需要的openid，在一个小程序中，openid是唯一的
        String openid = jsonObject.get("openid").toString();

        boolean yizhuce;
        Map<String, Object> map = new HashMap<>();
        map.put("gbDisId", gbDisId);
        map.put("admin", admin);
        map.put("openId", openid);
        NxJrdhUserEntity jrdhUserEntity1 = nxJrdhUserService.queryJrdhUserByAdmin(map);
        if (jrdhUserEntity1 != null) {
            System.out.println("yizhucueueueueueue");
            yizhuce = true;
            saveJrdhSupplerGb(jrdhUserEntity1, gbDisId, buyUserId, gbDepId);
        } else {
            //添加新用户
            NxJrdhUserEntity jrdhUserEntity = new NxJrdhUserEntity();
            jrdhUserEntity.setNxJrdhWxOpenId(openid);
            jrdhUserEntity.setNxJrdhJoinDate(formatWhatDay(0));
            //1,上传图片
            String newUploadName = ImagePaths.UPLOAD;
            String filePath = UploadFile.upload(session, newUploadName, file);

            jrdhUserEntity.setNxJrdhWxNickName(userName);
            jrdhUserEntity.setNxJrdhWxAvartraUrl(filePath);
            jrdhUserEntity.setNxJrdhUrlChange(1);
            jrdhUserEntity.setNxJrdhNxDistributerId(-1);
            jrdhUserEntity.setNxJrdhNxPurchaserUserId(-1);
            jrdhUserEntity.setNxJrdhNxCommunityId(-1);
            jrdhUserEntity.setNxJrdhGbDistributerId(-1);
            jrdhUserEntity.setNxJrdhGbDepartmentId(-1);
            jrdhUserEntity.setNxJrdhGbDepartmentUserId(-1);
            jrdhUserEntity.setNxJrdhAdmin(admin);
            jrdhUserEntity.setNxJrdhDeviceId("-1");
            jrdhUserEntity.setNxJrdhDevicePrintId("-1");
            nxJrdhUserService.save(jrdhUserEntity);

            saveJrdhSupplerGb(jrdhUserEntity, gbDisId, buyUserId, gbDepId);

            yizhuce = false;
        }

        if (yizhuce) {
            return R.error("-1");
        } else {
            return R.ok();
        }

    }

    private NxJrdhSupplierEntity saveJrdhSupplerGb(NxJrdhUserEntity jrdhUserEntity, Integer gbDisId, Integer buyUserId,
                                                   Integer gbDepId) {
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

    @RequestMapping(value = "/getJrdhUser/{userId}")
    @ResponseBody
    public R getJrdhUser(@PathVariable Integer userId) {
        NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryObject(userId);

        return R.ok().put("data", nxJrdhUserEntity);
    }

    /**
     * 更新昵称；可选 multipart 头像。兼容原路径 {@code /updateJrdhUser} 与 {@code /updateJrdhUserWithFile}。
     */
    @PostMapping(value = {"/updateJrdhUser", "/updateJrdhUserWithFile"})
    @ResponseBody
    public R updateJrdhUser(@RequestParam(value = "file", required = false) MultipartFile file,
                            @RequestParam String userName,
                            @RequestParam Integer userId,
                            HttpSession session) {
        NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryObject(userId);
        if (nxJrdhUserEntity == null) {
            return R.error(-1, "用户不存在");
        }

        if (file != null && !file.isEmpty()) {
            String oldPath = nxJrdhUserEntity.getNxJrdhWxAvartraUrl();
            if (oldPath != null && !oldPath.trim().isEmpty()) {
                UploadFile.deleteFile(oldPath);
            }
            String filePath = UploadFile.upload(session, ImagePaths.UPLOAD, file);
            nxJrdhUserEntity.setNxJrdhWxAvartraUrl(filePath);
            nxJrdhUserEntity.setNxJrdhUrlChange(1);
        }

        nxJrdhUserEntity.setNxJrdhWxNickName(userName);
        nxJrdhUserService.updateById(nxJrdhUserEntity);

        List<NxJrdhSupplierEntity> supplierEntities = nxJrdhSupplierService.querySupplierByUserId(userId);
        if (supplierEntities.size() > 0) {
            for (NxJrdhSupplierEntity supplierEntity : supplierEntities) {
                supplierEntity.setNxJrdhsSupplierName(userName);
                nxJrdhSupplierService.updateById(supplierEntity);
            }
        }

        return R.ok().put("data", nxJrdhUserService.queryObject(userId));
    }

    @RequestMapping(value = "/indexJrdhUserLoginJj/{code}")
    @ResponseBody
    public R indexJrdhUserLoginJj(@PathVariable String code) {

        System.out.println("urllrlrlcode" + code);

        MyAPPIDConfig myAPPIDConfig = new MyAPPIDConfig();
        String maimaiAppID = myAPPIDConfig.getTexiansongCaigouAppId();
        String maimaiScreat = myAPPIDConfig.getTexiansongCaigouScreat();
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + maimaiAppID + "&secret=" +
                maimaiScreat + "&js_code=" + code +
                "&grant_type=authorization_code";
        String str = WeChatUtil.httpRequest(url, "GET", null);
        JSONObject jsonObject = JSONObject.parseObject(str);
        String openId = jsonObject.get("openid").toString();

        Map<String, Object> dataMap = new HashMap<>();

        if (openId != null) {
            NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryWhichUserByOpenId(openId);
            if(nxJrdhUserEntity != null){
                Map<String, Object> map = new HashMap<>();
                map.put("userId", nxJrdhUserEntity.getNxJrdhUserId());
                System.out.println("whhwehepururr" + map);
                List<NxJrdhSupplierEntity> supplierEntities = nxJrdhSupplierService.queryJrdhSupplerWithDisByUserId(map);
                dataMap.put("userInfo", nxJrdhUserEntity);
                dataMap.put("arr", supplierEntities);
                return R.ok().put("data", dataMap);
            }else{
                return R.error(-1, "注册失败");
            }

        } else {
            return R.error(-1, "注册失败");
        }

    }
}