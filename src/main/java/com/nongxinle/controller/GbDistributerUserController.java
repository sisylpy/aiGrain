package com.nongxinle.controller;

import com.alibaba.fastjson2.JSONObject;
import com.nongxinle.common.result.Result;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbDistributerEntity;
import com.nongxinle.entity.NxJrdhUserEntity;
import com.nongxinle.service.GbDepartmentUserService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDistributerService;
import com.nongxinle.service.GbDistributerUserService;
import com.nongxinle.service.NxJrdhUserService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.ImagePaths;
import com.nongxinle.utils.MyAPPIDConfig;
import com.nongxinle.utils.R;
import com.nongxinle.utils.UploadFile;
import com.nongxinle.utils.WeChatUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 批发商用户Controller
 */
@RestController
@RequestMapping("gbdistributeruser")
public class GbDistributerUserController {

    @Autowired
    private GbDistributerUserService gbDistributerUserService;
    @Autowired
    private GbDepartmentUserService gbDepartmentUserService;
    @Autowired
    private GbDepartmentService gbDepartmentService;
    @Autowired
    private GbDistributerService gbDistributerService;
    @Autowired
    private NxJrdhUserService nxJrdhUserService;




    @RequestMapping("/gbRegisterWithFile")
    @ResponseBody
    public R gbRegisterWithFile(@RequestParam("file") MultipartFile file,
                                @RequestParam("restaurantName") String restaurantName,
                                @RequestParam("code") String code,
                                @RequestParam("phone") String phone,
                                @RequestParam("address") String address,
                                HttpSession session) {
        System.out.println("restaurantName" + restaurantName);

        MyAPPIDConfig myAPPIDConfig = new MyAPPIDConfig();

        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + myAPPIDConfig.getTexiansongCaigouAppId() +
                "&secret=" + myAPPIDConfig.getTexiansongCaigouScreat() + "&js_code=" + code + "&grant_type=authorization_code";
        // 发送请求，返回Json字符串
        String str = WeChatUtil.httpRequest(url, "GET", null);

        // 转成Json对象 获取openidjrdhUserRegister
        JSONObject jsonObject = JSONObject.parseObject(str);
        System.out.println(jsonObject);

        // 我们需要的openid，在一个小程序中，openid是唯一的
        // 微信接口可能返回错误
        if (!jsonObject.containsKey("openid")) {
            Integer errcode = jsonObject.getInteger("errcode");
            String errmsg = jsonObject.getString("errmsg");
            System.out.println("微信登录失败: errcode=" + errcode + ", errmsg=" + errmsg);
            return R.error(-1, "微信登录失败，请重试");
        }
        String openid = jsonObject.getString("openid");

        Map<String, Object> map = new HashMap<>();
        map.put("openId", openid);
        map.put("admin", GbConstants.DepartmentUserRole.STORE_MANAGER_APP);
        GbDepartmentUserEntity depUserEntities = gbDepartmentUserService.queryDepUsersByOpenIdAndAdmin(map);
        NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryWhichUserByOpenId(openid);

        if (depUserEntities == null && nxJrdhUserEntity == null) {
            GbDistributerEntity gbDistributerEntity = new GbDistributerEntity();
            gbDistributerEntity.setGbDistributerName(restaurantName);
            gbDistributerEntity.setGbDistributerPhone(phone);
            gbDistributerEntity.setGbDistributerAddress(address);
            gbDistributerEntity.setGbDistributerPrintName("ApplyHalfPanel");
            gbDistributerEntity.setGbDistributerSysCityId(6);
            gbDistributerEntity.setGbDistributerBusinessType(-1);
            gbDistributerEntity.setGbDistributerNxDisId(-1);
            gbDistributerEntity.setGbDistributerRecordSeconds("30");
            gbDistributerEntity.setGbDistributerStockCycle(0);

            GbDepartmentUserEntity managerUser = new GbDepartmentUserEntity();
            String filePath = UploadFile.upload(session, ImagePaths.UPLOAD, file);
//            //1 disuser save
            managerUser.setGbDuWxOpenId(openid);
            managerUser.setGbDuWxAvartraUrl(filePath);
            managerUser.setGbDuWxNickName(restaurantName+ "管理员");
            managerUser.setGbDuAdmin(GbConstants.DepartmentUserRole.STORE_MANAGER_APP);
            gbDistributerEntity.setSingleDepartmentUser(managerUser);
            Integer disId = gbDistributerService.saveSingleMendianDistributerGb(gbDistributerEntity);
            System.out.println("usidididid" + disId);

            if (disId != null) {
                Map<String, Object> mapRe = new HashMap<>();
                // 查询完整批发商信息（含所有部门列表）
                mapRe.put("disInfo", gbDistributerService.queryDistributerBaseInfo(disId));
                mapRe.put("depUserInfo", gbDepartmentUserService.queryDepUserByOpenId(openid));
                return R.ok().put("data", mapRe);
            }
            return R.error(-1, "注册失败");
        } else {
            return R.error(-1, "此微信号已注册过用户");
        }

    }


    /**
     * GB登录接口
     * 根据微信code获取openId，查询用户信息
     */
    @GetMapping("/gbLoginIndex/{code}")
    public Result gbLoginIndex(@PathVariable String code) {
        // 参数校验
        if (code == null || code.trim().isEmpty() || "undefined".equals(code) || "null".equals(code)) {
            return Result.error(-1, "登录凭证无效，请重新授权");
        }

        // 获取微信小程序配置
        MyAPPIDConfig myAPPIDConfig = new MyAPPIDConfig();
        String appId = myAPPIDConfig.getTexiansongCaigouAppId();
        String secret = myAPPIDConfig.getTexiansongCaigouScreat();

        // 调用微信接口获取openId
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + appId + "&secret=" +
                secret + "&js_code=" + code +
                "&grant_type=authorization_code";
        String str = WeChatUtil.httpRequest(url, "GET", null);

        // 网络请求失败
        if (str == null) {
            System.out.println("微信登录失败: 网络请求失败");
            return Result.error(-1, "网络连接失败，请检查网络后重试");
        }

        JSONObject jsonObject = JSONObject.parseObject(str);
        // 微信接口可能返回错误，如 code 无效或已使用
        if (jsonObject == null || !jsonObject.containsKey("openid")) {
            Integer errcode = jsonObject != null ? jsonObject.getInteger("errcode") : null;
            String errmsg = jsonObject != null ? jsonObject.getString("errmsg") : "微信接口无响应";
            System.out.println("微信登录失败: errcode=" + errcode + ", errmsg=" + errmsg);
            return Result.error(-1, "微信登录失败，请重试");
        }
        String openId = jsonObject.getString("openid");

        if (openId != null && !openId.trim().isEmpty()) {
            // 查询部门用户
            GbDepartmentUserEntity departmentUserEntity = gbDepartmentUserService.queryDepUserByOpenId(openId);

            if (departmentUserEntity != null) {
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

}
