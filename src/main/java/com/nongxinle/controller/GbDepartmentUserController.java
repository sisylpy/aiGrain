package com.nongxinle.controller;

import com.alibaba.fastjson2.JSONObject;
import com.nongxinle.common.result.Result;
import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatWhatDay;
import static com.nongxinle.utils.DateUtils.formatWhatYearDayTime;

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
    @Autowired
    private GbDistributerPayService gbDistributerPayService;




    @RequestMapping(value = "/gbLogin/{code}")
    @ResponseBody
    public R gbLogin(@PathVariable String code) {

        MyAPPIDConfig myAPPIDConfig = new MyAPPIDConfig();
        String maimaiAppID = myAPPIDConfig.getTexiansongCaigouAppId();
        String maimaiScreat = myAPPIDConfig.getTexiansongCaigouScreat();
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + maimaiAppID + "&secret=" +
                maimaiScreat + "&js_code=" + code +
                "&grant_type=authorization_code";
        String str = WeChatUtil.httpRequest(url, "GET", null);
        JSONObject jsonObject = JSONObject.parseObject(str);
        String openId = jsonObject.get("openid").toString();
        if (openId != null && !openId.trim().isEmpty()) {
            System.out.println("pepeeooppppp");
            GbDepartmentUserEntity departmentUserEntity = gbDepartmentUserService.queryDepUserByOpenId(openId);

            if (departmentUserEntity != null) {
                GbDistributerEntity gbDistributerEntity = gbDistributerService.queryDistributerInfo(departmentUserEntity.getGbDuDistributerId());
                GbDepartmentUserEntity depUserEntity = gbDepartmentUserService.queryDepUserByOpenId(openId);
                Map<String, Object> stringObjectMap = new HashMap<>();
                stringObjectMap.put("depUserInfo", depUserEntity);
                stringObjectMap.put("depInfo", gbDepartmentService.getById(departmentUserEntity.getGbDuDepartmentId()));
                stringObjectMap.put("disInfo", gbDistributerEntity);
                return R.ok().put("data", stringObjectMap);
            } else {
                return R.error(-1, "请向管理员索要注册邀请");
            }

        } else {
            return R.error(-1, "请进行注册");
        }

    }


    @RequestMapping(value = "/gbRegisterWithFileInvite", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public R gbRegisterWithFileInvite(@RequestParam("file") MultipartFile file,
                                      @RequestParam("restaurantName") String restaurantName,
                                      @RequestParam("code") String code,
                                      @RequestParam("phone") String phone,
                                      @RequestParam("address") String address,
                                      @RequestParam("disId") Integer disId,
                                      HttpSession session) {

        MyAPPIDConfig myAPPIDConfig = new MyAPPIDConfig();

        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + myAPPIDConfig.getTexiansongCaigouAppId() +
                "&secret=" + myAPPIDConfig.getTexiansongCaigouScreat() + "&js_code=" + code + "&grant_type=authorization_code";
        // 发送请求，返回Json字符串
        String str = WeChatUtil.httpRequest(url, "GET", null);

        // 转成Json对象 获取openidjrdhUserRegister
        JSONObject jsonObject = JSONObject.parseObject(str);
        System.out.println(jsonObject);

        // 我们需要的openid，在一个小程序中，openid是唯一的
        String openid = jsonObject.get("openid").toString();

        Map<String, Object> map = new HashMap<>();
        map.put("openId", openid);
        map.put("admin", 2);
        GbDepartmentUserEntity depUserEntities = gbDepartmentUserService.queryDepUsersByOpenIdAndAdmin(map);
        NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryWhichUserByOpenId(openid);

        if (depUserEntities == null && nxJrdhUserEntity == null) {
            GbDistributerEntity gbDistributerEntity = new GbDistributerEntity();
            gbDistributerEntity.setGbDistributerName(restaurantName);
            gbDistributerEntity.setGbDistributerPhone(phone);
            gbDistributerEntity.setGbDistributerAddress(address);
            gbDistributerEntity.setGbDistributerBusinessType(-1);
            gbDistributerEntity.setGbDistributerPrintName("ApplyHalfPanel");
            gbDistributerEntity.setGbDistributerSysCityId(6);
            gbDistributerEntity.setGbDistributerNxDisId(-1);

            GbDepartmentUserEntity purUser = new GbDepartmentUserEntity();
            //1,上传图片
            String newUploadName = "uploadImage";
            String realPath = UploadFile.upload(session, newUploadName, file);

            String filename = file.getOriginalFilename();
            String filePath = newUploadName + "/" + filename;
            //1 disuser save
            purUser.setGbDuWxOpenId(openid);
            purUser.setGbDuWxAvartraUrl(filePath);
            purUser.setGbDuWxNickName(restaurantName+ "采购员");
            gbDistributerEntity.setSingleDepartmentUser(purUser);
            Integer newDisId = gbDistributerService.saveSingleMendianDistributerGb(gbDistributerEntity);

            System.out.println("usidididid" + newDisId);
            if (newDisId != null) {

                GbDistributerEntity inviteGbDis = gbDistributerService.getById(disId);

                BigDecimal bitSet = new BigDecimal(inviteGbDis.getGbDistributerBuyQuantity());
                BigDecimal add = bitSet.add(new BigDecimal(1000));
                inviteGbDis.setGbDistributerBuyQuantity(add.toString());

                gbDistributerService.updateById(inviteGbDis);

                GbDistributerPayEntity gbDistributerPayEntity = new GbDistributerPayEntity();
                gbDistributerPayEntity.setGbGdpGbDisId(disId);
                gbDistributerPayEntity.setGbGdpGbNewDisId(newDisId);
                gbDistributerPayEntity.setGbGdpBuyQuantity("0.1");
                gbDistributerPayEntity.setGbGdpPaySubtotal("0");
                gbDistributerPayEntity.setGbGdpStatus(0);
                gbDistributerPayEntity.setGbGdpPayTime(formatWhatYearDayTime(0));
                gbDistributerPayEntity.setGbGdpType(2);

                gbDistributerPayService.save(gbDistributerPayEntity);

                Map<String, Object> mapRe = new HashMap<>();
                mapRe.put("disInfo", gbDistributerService.queryDistributerInfo(disId));
                mapRe.put("depUserInfo", gbDepartmentUserService.queryDepUserByOpenId(openid));
                return R.ok().put("data", mapRe);
            }
            return R.error(-1, "注册失败");
        } else {
            return R.error(-1, "此微信号已注册过采购员");
        }

    }


    @RequestMapping(value = "/gbPurchaserRegitsteWithFile", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public R gbPurchaserRegitsteWithFile(@RequestParam("file") MultipartFile file,
                                         @RequestParam("userName") String userName,
                                         @RequestParam("code") String code,
                                         @RequestParam("admin") Integer admin,
                                         @RequestParam("depFatherId") Integer depFatherId,
                                         @RequestParam("depId") Integer depId,
                                         @RequestParam("gbDisId") Integer gbDisId,
                                         HttpSession session) {

        System.out.println("ddfafduaufudaffff" + userName);
        MyAPPIDConfig myAPPIDConfig = new MyAPPIDConfig();

        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + myAPPIDConfig.getTexiansongCaigouAppId() +
                "&secret=" + myAPPIDConfig.getTexiansongCaigouScreat() + "&js_code=" + code + "&grant_type=authorization_code";
        // 发送请求，返回Json字符串
        String str = WeChatUtil.httpRequest(url, "GET", null);

        // 转成Json对象 获取openidjrdhUserRegister
        JSONObject jsonObject = JSONObject.parseObject(str);
        System.out.println(jsonObject);

        // 我们需要的openid，在一个小程序中，openid是唯一的
        String openid = jsonObject.get("openid").toString();

        Map<String, Object> map = new HashMap<>();
        map.put("openId", openid);
        map.put("admin", admin);
        GbDepartmentUserEntity depUserEntities = gbDepartmentUserService.queryDepUsersByOpenIdAndAdmin(map);
        NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryWhichUserByOpenId(openid);

        if (depUserEntities == null && nxJrdhUserEntity == null) {

            //1,上传图片
            String newUploadName = "uploadImage";
            String realPath = UploadFile.upload(session, newUploadName, file);

            String filename = file.getOriginalFilename();
            String filePath = newUploadName + "/" + filename;
            //1 disuser save
            GbDepartmentUserEntity gbDepartmentUserEntity = new GbDepartmentUserEntity();
            gbDepartmentUserEntity.setGbDuUrlChange(1);
            gbDepartmentUserEntity.setGbDuWxNickName(userName);
            gbDepartmentUserEntity.setGbDuWxAvartraUrl(filePath);
            gbDepartmentUserEntity.setGbDuDepartmentId(depId);
            gbDepartmentUserEntity.setGbDuDepartmentFatherId(depFatherId);
            gbDepartmentUserEntity.setGbDuDistributerId(gbDisId);
            gbDepartmentUserEntity.setGbDuAdmin(admin);
            gbDepartmentUserEntity.setGbDuWxOpenId(openid);
            gbDepartmentUserEntity.setGbDuJoinDate(formatWhatDay(0));
            gbDepartmentUserEntity.setGbDuLoginTimes(0);
            gbDepartmentUserEntity.setGbDuPrintDeviceId("-1");
            gbDepartmentUserEntity.setGbDuPrintBillDeviceId("-1");
            gbDepartmentUserService.save(gbDepartmentUserEntity);
            Map<String, Object> mapRe = new HashMap<>();
            mapRe.put("disInfo", gbDistributerService.queryDistributerInfo(gbDisId));
            mapRe.put("depUserInfo", gbDepartmentUserService.queryDepUserByOpenId(openid));
            mapRe.put("depInfo", gbDepartmentService.queryDepInfoGb(depId));
            return R.ok().put("data", mapRe);

        } else {
            return R.error(-1, "此微信号已注册过采购员");
        }

    }

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
            GbDistributerPurchaseBatchEntity batchEntity = gbDisPurchaseBatchService.getById(batchId);
            Map<String, Object> map = new HashMap<>();
            GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(gbDisId);
            map.put("disInfo", gbDistributerEntity);

            // 首先判断是不是dis 用户
            Map<String, Object> mapUser = new HashMap<>();
            mapUser.put("openId", openId);
            mapUser.put("admin", GbConstants.DepartmentUserRole.STORE_MANAGER_APP);
            GbDepartmentUserEntity caigouUser = gbDepartmentUserService.queryDepUsersByOpenIdAndAdmin(mapUser);
            if (caigouUser != null) {
                map.put("userInfo", caigouUser);
                map.put("buyUser", true);
                map.put("supplierInfo", null);
                map.put("code", 1);
            } else {
                Map<String, Object> mapUserSell = new HashMap<>();
                mapUserSell.put("openId", openId);
                mapUserSell.put("admin", GbConstants.NxJrdhUserAdminType.GB_SELLER);
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
