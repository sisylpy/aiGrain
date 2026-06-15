package com.nongxinle.controller;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.wxpay.sdk.WXPay;
import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


import static com.nongxinle.utils.DateUtils.*;
import static com.nongxinle.utils.PinYin4jUtils.getHeadStringByString;

/**
 * @author lpy
 */
@RestController
@RequestMapping("gbdistributerpay")
public class GbDistributerPayController {
    @Autowired
    private GbDistributerPayService gbDistributerPayService;
    @Autowired
    private GbDistributerService gbDistributerService;
    @Autowired
	private GbDepartmentService gbDepartmentService;
    @Autowired
	private GbDistributerModuleService gbDistributerModuleService;



    @ResponseBody
    @RequestMapping(value = "/disPayUser", method = RequestMethod.POST)
    public R disPayUser(String subtotal, String openId, Integer payId) {

        MyAPPIDConfig config = new MyAPPIDConfig();
//
        Double aDouble = Double.parseDouble(subtotal) * 100;
        int i = aDouble.intValue();
        String s1 = String.valueOf(i);
        String tradeNo = CommonUtils.generateOutTradeNo();
        SortedMap<String, String> params = new TreeMap<>();
        params.put("appid", config.getTexiansongCaigouAppId());
        params.put("mch_id", config.getMchID());
        params.put("nonce_str", CommonUtils.generateUUID());
        params.put("body", "订单支付");
        params.put("out_trade_no", tradeNo);
        params.put("fee_type", "CNY");
        params.put("total_fee", s1);
        params.put("spbill_create_ip", "101.42.222.149");
        params.put("notify_url", "https://grainservice.club/api/gbdistributerpay/notify");
        params.put("trade_type", "JSAPI");
        params.put("openid", openId);

        //map转xml
        try {
            WXPay wxpay = new WXPay(config);
            long time = System.currentTimeMillis();
            String tString = String.valueOf(time / 1000);
            Map<String, String> resp = wxpay.unifiedOrder(params);
            System.out.println(resp);
            SortedMap<String, String> reMap = new TreeMap<>();
            reMap.put("appId", config.getAppID());
            reMap.put("nonceStr", resp.get("nonce_str"));
            reMap.put("package", "prepay_id=" + resp.get("prepay_id"));
            reMap.put("signType", "MD5");
            reMap.put("timeStamp", tString);
            String s = WxPayUtils.creatSign(reMap, config.getKey());
            reMap.put("paySign", s);

            GbDistributerPayEntity payEntity = gbDistributerPayService.queryPayItemByPayId(payId);
            payEntity.setGbGdpTradeNo(tradeNo);
            payEntity.setGbGdpPayTime(formatWhatTime(0));
            gbDistributerPayService.updateById(payEntity);
            reMap.put("orderId", payEntity.getGbDistributerPayId().toString());
            return R.ok().put("map", reMap);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return R.ok();
    }




    @RequestMapping(value = "/disGetPayList", method = RequestMethod.POST)
    @ResponseBody
    public R disGetPayList(Integer disId, Integer type) {

        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        System.out.println("mappdaaa" + map);
        List<GbDistributerPayEntity> payEntities = gbDistributerPayService.queryDisPayListByParams(map);
        return R.ok().put("data", payEntities);

    }


    @RequestMapping(value = "/buyMachines", method = RequestMethod.POST)
    @ResponseBody
    public R buyMachines(@RequestBody List<GbDistributerPayEntity> payEntityList) {

        System.out.println("dee" + payEntityList);

        MyAPPIDConfig config = new MyAPPIDConfig();
        String openId = "";
        Double aDouble = 0.0;
        for (GbDistributerPayEntity payEntity : payEntityList) {
            openId = payEntity.getPayUserOpenId();
            aDouble = aDouble + Double.parseDouble(payEntity.getGbGdpPaySubtotal()) * 100;
        }

        System.out.println("subsosososoososo" + aDouble);
        int i = aDouble.intValue();
        String s1 = String.valueOf(i);
        String tradeNo = CommonUtils.generateOutTradeNo();
        SortedMap<String, String> params = new TreeMap<>();
        params.put("appid", config.getTexiansongCaigouAppId());
        params.put("mch_id", config.getMchID());
        params.put("nonce_str", CommonUtils.generateUUID());
        params.put("body", "订单支付");
        params.put("out_trade_no", tradeNo);
        params.put("fee_type", "CNY");
        params.put("total_fee", s1);
        params.put("spbill_create_ip", "101.42.222.149");
        params.put("notify_url", "https://grainservice.club/api/gbdistributerpay/notifyMachine");
        params.put("trade_type", "JSAPI");
        params.put("openid", openId);

        System.out.println("paassss" + params);

        //map转xml
        try {
            WXPay wxpay = new WXPay(config);
            long time = System.currentTimeMillis();
            String tString = String.valueOf(time / 1000);
            Map<String, String> resp = wxpay.unifiedOrder(params);
            System.out.println(resp);
            SortedMap<String, String> reMap = new TreeMap<>();
            reMap.put("appId", config.getAppID());
            reMap.put("nonceStr", resp.get("nonce_str"));
            reMap.put("package", "prepay_id=" + resp.get("prepay_id"));
            reMap.put("signType", "MD5");
            reMap.put("timeStamp", tString);
            String s = WxPayUtils.creatSign(reMap, config.getKey());
            reMap.put("paySign", s);

            for (GbDistributerPayEntity payEntity : payEntityList) {

                payEntity.setGbGdpTradeNo(tradeNo);

                String startDate = formatWhatDay(0);
                String stopDate = afterWhatDay(startDate, 365);
                String couponStartTime = "00:00:00:00";
                String couponStopTime = "23:59:59:00";
//
                String replaceStart = couponStartTime.replace(":", "-");
                String replaceStop = couponStopTime.replace(":", "-");
                String start = startDate + "-" + replaceStart;
                String stop = stopDate + "-" + replaceStop;
                System.out.println("dadfaf" + start + "stop=====" + stop);


                String[] splitStart = start.split("-");
                int year = Integer.parseInt(splitStart[0]);
                int month = Integer.parseInt(splitStart[1]);
                int day = Integer.parseInt(splitStart[2]);
                int hour = Integer.parseInt(splitStart[3]);
                int minute = Integer.parseInt(splitStart[4]);
                int haomiao = Integer.parseInt(splitStart[5]);
                LocalDateTime beginTime = LocalDateTime.of(year, month, day, hour, minute, haomiao);

                String[] splitStop = stop.split("-");
                int yearS = Integer.parseInt(splitStop[0]);
                int monthS = Integer.parseInt(splitStop[1]);
                int dayS = Integer.parseInt(splitStop[2]);
                int hourS = Integer.parseInt(splitStop[3]);
                int minuteS = Integer.parseInt(splitStop[4]);
                int haomiaoS = Integer.parseInt(splitStop[5]);
                LocalDateTime stopTime = LocalDateTime.of(yearS, monthS, dayS, hourS, minuteS, haomiaoS);
                System.out.println("adafasd" + beginTime + "stttt" + stopTime);
                Date beginTTT = Date.from(beginTime.atZone(ZoneId.systemDefault()).toInstant());
                Date stopTTT = Date.from(stopTime.atZone(ZoneId.systemDefault()).toInstant());

                payEntity.setGbGdpFromTime(beginTTT);
                payEntity.setGbGdpStopTime(stopTTT);
                payEntity.setGbGdpStatus(-1);
                payEntity.setGbGdpPaySubtotal(payEntity.getGbGdpPaySubtotal());
                gbDistributerPayService.save(payEntity);
            }


            return R.ok().put("map", reMap);


        } catch (Exception e) {
            e.printStackTrace();
        }


        return R.ok();
    }


    @ResponseBody
    @RequestMapping(value = "/gbDisBuyUser", method = RequestMethod.POST)
    public R gbDisBuyUser(Integer disId, String subtotal, String openId, String quantity, Integer type) {

        System.out.println("lppdodoeoeoeeoeoogbgbbgbg");
        MyWxJJCGPayConfig config = new MyWxJJCGPayConfig();
        Double aDouble = Double.parseDouble(subtotal) * 100;
        int i = aDouble.intValue();
        String s1 = String.valueOf(i);
        String tradeNo = CommonUtils.generateOutTradeNo();
        SortedMap<String, String> params = new TreeMap<>();
        params.put("appid", config.getAppID());
        params.put("mch_id", config.getMchID());
        params.put("nonce_str", CommonUtils.generateUUID());
        params.put("body", "订单支付");
        params.put("out_trade_no", tradeNo);
        params.put("fee_type", "CNY");
        params.put("total_fee", s1);
        params.put("spbill_create_ip", "101.42.222.149");
        params.put("notify_url", "https://grainservice.club/api/gbdistributerpay/notify");
        params.put("trade_type", "JSAPI");
        params.put("openid", openId);

        //map转xml
        try {
            WXPay wxpay = new WXPay(config);
            long time = System.currentTimeMillis();
            String tString = String.valueOf(time / 1000);
            Map<String, String> resp = wxpay.unifiedOrder(params);
            System.out.println(resp);
            SortedMap<String, String> reMap = new TreeMap<>();
            reMap.put("appId", config.getAppID());
            reMap.put("nonceStr", resp.get("nonce_str"));
            reMap.put("package", "prepay_id=" + resp.get("prepay_id"));
            reMap.put("signType", "MD5");
            reMap.put("timeStamp", tString);
            String s = WxPayUtils.creatSign(reMap, config.getKey());
            reMap.put("paySign", s);

            GbDistributerPayEntity payEntity = new GbDistributerPayEntity();
            payEntity.setGbGdpGbDisId(disId);
            payEntity.setGbGdpPaySubtotal(subtotal);
            payEntity.setGbGdpType(type);
            payEntity.setGbGdpStatus(-1);
            payEntity.setGbGdpTradeNo(tradeNo);
            if (type == 1) {
                int multiply = new BigDecimal(quantity).multiply(new BigDecimal(10000)).intValue();
                payEntity.setGbGdpBuyQuantity(String.valueOf(multiply));
            } else {
                payEntity.setGbGdpBuyQuantity(String.valueOf(quantity));
            }

            payEntity.setGbGdpStatus(-1);
            gbDistributerPayService.save(payEntity);



            reMap.put("orderId", payEntity.getGbDistributerPayId().toString());

            return R.ok().put("map", reMap);

        } catch (Exception e) {
            e.printStackTrace();
        }


        return R.ok();

    }


    /**
     * @Title: callBack
     * @Description: 支付完成的回调函数
     * @param:
     * @return:
     */
    @RequestMapping("/notify")
    public String callBack(HttpServletRequest request, HttpServletResponse response) {
        // System.out.println("微信支付成功,微信发送的callback信息,请注意修改订单信息");
        InputStream is = null;
        try {

            is = request.getInputStream();// 获取请求的流信息(这里是微信发的xml格式所有只能使用流来读)
            String xml = WxPayUtils.InputStream2String(is);
            Map<String, String> notifyMap = WxPayUtils.xmlToMap(xml);// 将微信发的xml转map
            System.out.println("微信返回给回调函数的信息为：" + xml);
            if (notifyMap.get("result_code").equals("SUCCESS")) {
                /*
                 * 以下是自己的业务处理------仅做参考 更新order对应字段/已支付金额/状态码
                 * 更新bill支付状态
                 */
                System.out.println("===notify===回调方法已经被调！！！");
                String ordersSn = notifyMap.get("out_trade_no");// 商户订单号
                List<GbDistributerPayEntity> list = gbDistributerPayService.queryListByTradeNo(ordersSn);
                if (list.size() > 0) {
                    for (GbDistributerPayEntity payEntity : list) {
                        payEntity.setGbGdpStatus(0);
                        payEntity.setGbGdpPayTime(formatWhatYearDayTime(0));
                        gbDistributerPayService.updateById(payEntity);

                        GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(payEntity.getGbGdpGbDisId());
                        if (payEntity.getGbGdpType() == 0) {
                            BigDecimal decimal = new BigDecimal(gbDistributerEntity.getGbDistributerBuyQuantity());
                            System.out.println("decimdall========" + decimal);
                            BigDecimal decimal1 = new BigDecimal(payEntity.getGbGdpBuyQuantity());
                            BigDecimal add = decimal.add(decimal1);
                            gbDistributerEntity.setGbDistributerBuyQuantity(add.toString());
                            if(gbDistributerEntity.getGbDistributerBusinessType() == -1){
                                gbDistributerEntity.setGbDistributerBusinessType(0);
                            }
                            gbDistributerService.updateById(gbDistributerEntity);
                        }

                        maybeRewardReferrerSecondTime(payEntity);
                    }
                }




            }

            // 告诉微信服务器收到信息了，不要在调用回调action了========这里很重要回复微信服务器信息用流发送一个xml即可
            response.getWriter().write("<xml><return_code><![CDATA[SUCCESS]]></return_code></xml>");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    @RequestMapping("/notifyMachine")
    public String callBackMachine(HttpServletRequest request, HttpServletResponse response) {
        // System.out.println("微信支付成功,微信发送的callback信息,请注意修改订单信息");
        InputStream is = null;
        try {

            is = request.getInputStream();// 获取请求的流信息(这里是微信发的xml格式所有只能使用流来读)
            String xml = WxPayUtils.InputStream2String(is);
            Map<String, String> notifyMap = WxPayUtils.xmlToMap(xml);// 将微信发的xml转map
            System.out.println("微信返回给回调函数的信息为：" + xml);
            if (notifyMap.get("result_code").equals("SUCCESS")) {
                /*
                 * 以下是自己的业务处理------仅做参考 更新order对应字段/已支付金额/状态码
                 * 更新bill支付状态
                 */
                System.out.println("===notify===回调方法已经被调！！！");
                String ordersSn = notifyMap.get("out_trade_no");// 商户订单号

                List<GbDistributerPayEntity> list = gbDistributerPayService.queryListByTradeNo(ordersSn);

                Integer gbDisId = -1;
                if (list.size() > 0) {
                    for (GbDistributerPayEntity payEntity : list) {

                        gbDisId = payEntity.getGbGdpGbDisId();
                        payEntity.setGbGdpStatus(0);
                        payEntity.setGbGdpPayTime(formatWhatYearDayTime(0));
                        gbDistributerPayService.updateById(payEntity);

                        GbDistributerEntity nxDistributerEntity = gbDistributerService.getById(payEntity.getGbGdpGbDisId());
                        nxDistributerEntity.setGbDistributerBusinessType(1);
                        gbDistributerService.updateById(nxDistributerEntity);
                        if(payEntity.getGbGdpType() != 0){
							savePayTypeDepartment(nxDistributerEntity,payEntity.getGbGdpType());
						}
                    }
                }


            }

            // 告诉微信服务器收到信息了，不要在调用回调action了========这里很重要回复微信服务器信息用流发送一个xml即可
            response.getWriter().write("<xml><return_code><![CDATA[SUCCESS]]></return_code></xml>");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }


    private  void  savePayTypeDepartment(GbDistributerEntity  gbDistributerEntity, Integer type){
		GbDepartmentEntity departmentEntity = new GbDepartmentEntity();
		departmentEntity.setGbDepartmentDisId(gbDistributerEntity.getGbDistributerId());
		departmentEntity.setGbDepartmentFatherId(0);
		departmentEntity.setGbDepartmentType(type);
		gbDistributerEntity.setGbDistributerSettleDate(formatWhatDay(0));
		departmentEntity.setGbDepartmentSettleFullTime(formatFullTime());
		departmentEntity.setGbDepartmentSettleDate(formatWhatDay(0));
		departmentEntity.setGbDepartmentSettleMonth(formatWhatMonth(0));
		departmentEntity.setGbDepartmentSettleWeek(getWeekOfYear(0).toString());
		departmentEntity.setGbDepartmentSettleYear(formatWhatYear(0));
		departmentEntity.setGbDepartmentSettleTimes("0");
		departmentEntity.setGbDepartmentSubAmount(0);
		departmentEntity.setGbDepartmentIsGroupDep(1);
		departmentEntity.setGbDepartmentAttrName(gbDistributerEntity.getGbDistributerName());
		departmentEntity.setGbDepartmentName(gbDistributerEntity.getGbDistributerName());
		departmentEntity.setGbDepartmentPrintSet(0);
		String gbDepartmentName = departmentEntity.getGbDepartmentName();
		String headPinyin = getHeadStringByString(gbDepartmentName, false, null);
		departmentEntity.setGbDepartmentNamePy(headPinyin);
		gbDepartmentService.save(departmentEntity);


	}



    @RequestMapping(value = "/gbDisGetBuyType", method = RequestMethod.POST)
    @ResponseBody
    public R gbDisGetBuyType(Integer disId, Integer type) {
        List<GbDistributerPayEntity> list = new ArrayList<>();
        if (type == 0) {
            GbDistributerPayEntity payEntity = new GbDistributerPayEntity();
            payEntity.setGbGdpBuyQuantity("6.0");
//            payEntity.setGbGdpPaySubtotal("598");
            payEntity.setGbGdpPaySubtotal("0.1");
            payEntity.setPerPrice("1");
            list.add(payEntity);
            GbDistributerPayEntity payEntity1 = new GbDistributerPayEntity();
            payEntity1.setGbGdpBuyQuantity("4.0");
            payEntity1.setGbGdpPaySubtotal("358");
//            payEntity1.setGbGdpPaySubtotal("6.58");
            payEntity1.setPerPrice("0.9");
            list.add(payEntity1);
            GbDistributerPayEntity payEntity2 = new GbDistributerPayEntity();
            payEntity2.setGbGdpBuyQuantity("6.0");
            payEntity2.setGbGdpPaySubtotal("480");
            payEntity2.setPerPrice("0.8");
            list.add(payEntity2);
        }

//        if (type == 1) {
//
//
//            GbDistributerPayEntity payEntity = new GbDistributerPayEntity();
//            payEntity.setGbGdpBuyQuantity("1");
//            payEntity.setGbGdpPaySubtotal("1800");
////            payEntity.setGbGdpPaySubtotal("1.8");
//            payEntity.setPerPrice("5");
//            list.add(payEntity);
//            GbDistributerPayEntity payEntity1 = new GbDistributerPayEntity();
//            payEntity1.setGbGdpBuyQuantity("2");
////            payEntity1.setGbGdpPaySubtotal("3.420");
//            payEntity1.setGbGdpPaySubtotal("3420");
//            payEntity1.setPerPrice("4.75");
//            list.add(payEntity1);
//            GbDistributerPayEntity payEntity3 = new GbDistributerPayEntity();
//            payEntity3.setGbGdpBuyQuantity("3");
////            payEntity3.setGbGdpPaySubtotal("4.860");
//            payEntity3.setGbGdpPaySubtotal("4860");
//            payEntity3.setPerPrice("4.5");
//            list.add(payEntity3);
//
//        }
        if (type == 1) {
            GbDistributerPayEntity subStore = new GbDistributerPayEntity();
            subStore.setGbGdpBuyQuantity("1");
            subStore.setGbGdpPaySubtotal("0");
            subStore.setGbGdpType(1);
            subStore.setPerPrice("分店");
            subStore.setGbGdpImgUrl("uploadImage/imPurchase/guanli.png");
            subStore.setGbGdpSellDetail("实时监控 精准管理\n 管理者可以随时查看分店库存情况，掌握原料的使用状态、剩余数量，以及新鲜度等关键信息，能够对原料的采购、存储、使用等各环节进行精准的管理。");
            list.add(subStore);

            GbDistributerPayEntity payEntity = new GbDistributerPayEntity();
            payEntity.setGbGdpBuyQuantity("1");
            payEntity.setGbGdpPaySubtotal("2800");
            payEntity.setGbGdpType(0);
            payEntity.setPerPrice("连锁店管理端");
            payEntity.setGbGdpImgUrl("uploadImage/imPurchase/guanli.png");
            payEntity.setGbGdpSellDetail("实时监控 精准管理\n 管理者可以随时查看分店库存情况，掌握原料的使用状态、剩余数量，以及新鲜度等关键信息，能够对原料的采购、存储、使用等各环节进行精准的管理。");
            list.add(payEntity);

            GbDistributerPayEntity payEntity1 = new GbDistributerPayEntity();
            payEntity1.setGbGdpBuyQuantity("1");
            payEntity1.setGbGdpPaySubtotal("750");
//            payEntity1.setGbGdpPaySubtotal("7.50");
            payEntity1.setGbGdpType(3);
            payEntity1.setPerPrice("时鲜库房端");
            payEntity1.setGbGdpSellDetail("保持领先\n后厨人员直接参与库存管理和订货，他们可以根据实际使用情况调整库存数据。");
            payEntity1.setGbGdpImgUrl("uploadImage/imPurchase/kufang.png");
            list.add(payEntity1);


            GbDistributerPayEntity payEntity2 = new GbDistributerPayEntity();
            payEntity2.setGbGdpBuyQuantity("1");
            payEntity2.setGbGdpPaySubtotal("3500");
//            payEntity2.setGbGdpPaySubtotal("3.500");
            payEntity2.setPerPrice("时鲜制作");
            payEntity2.setGbGdpType(4);
            payEntity2.setGbGdpImgUrl("uploadImage/imPurchase/zhizuo.png");
            payEntity2.setGbGdpSellDetail("提升原材料新鲜度\n中央厨房能够实时监控原材料的库存情况，包括新鲜度、保质期和库存量。这样可以确保使用的原材料始终处于最佳状态，提高食品的整体质量");
            list.add(payEntity2);

            GbDistributerPayEntity payEntity3 = new GbDistributerPayEntity();
            payEntity3.setGbGdpBuyQuantity("1");
            payEntity3.setGbGdpPaySubtotal("2950");
            payEntity3.setPerPrice("时鲜窗口售卖");
            payEntity3.setGbGdpType(5);
            payEntity3.setGbGdpImgUrl("uploadImage/imPurchase/window.png");
            payEntity3.setGbGdpSellDetail("任何用户可以通过直观的图形界面快速完成下单、查询、结账等操作，无需复杂的培训，多联单打印功能可以为商家提供订单、收据等多份副本的打印需求。");
            list.add(payEntity3);

            GbDistributerPayEntity payEntity4 = new GbDistributerPayEntity();
            payEntity4.setGbGdpBuyQuantity("1");
            payEntity4.setGbGdpPaySubtotal("1550");
            payEntity4.setPerPrice("私域会员");
            payEntity4.setGbGdpType(7);
            payEntity4.setGbGdpImgUrl("uploadImage/imPurchase/member.png");
            payEntity4.setGbGdpSellDetail("解决有些客户的会员管理能力差和内容运营能力薄弱的问题，通过丰富的优惠券和会员卡活动，企业可以轻松开展各种促销活动，吸引更多新用户，并保持老用户的活跃度");

            list.add(payEntity4);

        }

        Map<String, Object> map = new HashMap<>();
        Map<String, Object> mapP = new HashMap<>();
        mapP.put("disId", disId);
        mapP.put("type", type);
        mapP.put("equalStatus", -1);

        System.out.println("mappd" + mapP);
        List<GbDistributerPayEntity> payEntities = gbDistributerPayService.queryDisPayListByParams(mapP);

        GbDistributerEntity gbDistributerEntity = gbDistributerService.queryDistributerWithAllDepartments(disId);

        map.put("list", list);
        map.put("payEntities", payEntities);
        map.put("liwu", 1000);
        map.put("liwuDay", 3);
        map.put("disInfo", gbDistributerEntity);


        return R.ok().put("data", map);
    }

    /**
     * 被推荐用户「注册时已发放第一次奖励」对应 gb_distributer_pay 中一条 type=2、status=0、gb_gdp_gb_new_dis_id=被推荐方。
     * 当其首次真实购买（type=0）微信支付成功时，再给推荐方 gb_distributer_buy_quantity 增加「两万分」；
     * gbDistributerBuyQuantity / 本条流水 gb_gdp_buy_quantity 均以「万」为单位，故两万分记为 2。
     * 并将首次奖励行改为 status=1，同时插入一条 type=8 的二次奖励流水且 status=1。
     */
    private static final BigDecimal REFERRER_SECOND_REWARD_WAN = new BigDecimal("20000");
    private static final int GDP_PAY_TYPE_REFERRAL_REGISTER = 2;
    /** 首购触发的推荐人二次积分奖励流水（与注册 type=2 区分） */
    private static final int GDP_PAY_TYPE_REFERRAL_SECOND_BONUS = 8;

    private void maybeRewardReferrerSecondTime(GbDistributerPayEntity paidEntity) {
        if (paidEntity == null || paidEntity.getGbGdpGbDisId() == null) {
            return;
        }
        if (paidEntity.getGbGdpType() == null || paidEntity.getGbGdpType() != 0) {
            return;
        }
        Integer buyerDisId = paidEntity.getGbGdpGbDisId();

        long referralPayCount = gbDistributerPayService.count(new LambdaQueryWrapper<GbDistributerPayEntity>()
                .eq(GbDistributerPayEntity::getGbGdpGbNewDisId, buyerDisId)
                .eq(GbDistributerPayEntity::getGbGdpStatus, 0)
                .eq(GbDistributerPayEntity::getGbGdpType, GDP_PAY_TYPE_REFERRAL_REGISTER));
        if (referralPayCount != 1) {
            return;
        }

        long buyerPaidPurchaseCount = gbDistributerPayService.count(new LambdaQueryWrapper<GbDistributerPayEntity>()
                .eq(GbDistributerPayEntity::getGbGdpGbDisId, buyerDisId)
                .eq(GbDistributerPayEntity::getGbGdpStatus, 0)
                .eq(GbDistributerPayEntity::getGbGdpType, 0));
        if (buyerPaidPurchaseCount != 1) {
            return;
        }

        GbDistributerPayEntity referralRow = gbDistributerPayService.getOne(new LambdaQueryWrapper<GbDistributerPayEntity>()
                .eq(GbDistributerPayEntity::getGbGdpGbNewDisId, buyerDisId)
                .eq(GbDistributerPayEntity::getGbGdpStatus, 0)
                .eq(GbDistributerPayEntity::getGbGdpType, GDP_PAY_TYPE_REFERRAL_REGISTER)
                .last("LIMIT 1"));
        if (referralRow == null || referralRow.getGbGdpGbDisId() == null) {
            return;
        }
        Integer referrerDisId = referralRow.getGbGdpGbDisId();
        if (referrerDisId.equals(buyerDisId)) {
            return;
        }

        GbDistributerEntity referrer = gbDistributerService.getById(referrerDisId);
        if (referrer == null) {
            return;
        }
        String q = referrer.getGbDistributerBuyQuantity();
        if (q == null || q.isEmpty()) {
            q = "0";
        }
        BigDecimal next = new BigDecimal(q).add(REFERRER_SECOND_REWARD_WAN);
        referrer.setGbDistributerBuyQuantity(next.toString());
        gbDistributerService.updateById(referrer);

        referralRow.setGbGdpStatus(1);
        gbDistributerPayService.updateById(referralRow);

        GbDistributerPayEntity secondBonus = new GbDistributerPayEntity();
        secondBonus.setGbGdpGbDisId(referrerDisId);
        secondBonus.setGbGdpGbNewDisId(buyerDisId);
        secondBonus.setGbGdpBuyQuantity("2");
        secondBonus.setGbGdpPaySubtotal("0");
        secondBonus.setGbGdpStatus(1);
        secondBonus.setGbGdpType(GDP_PAY_TYPE_REFERRAL_SECOND_BONUS);
        secondBonus.setGbGdpPayTime(formatWhatYearDayTime(0));
        secondBonus.setGbGdpTradeNo(CommonUtils.generateOutTradeNo());
        gbDistributerPayService.save(secondBonus);
    }

}
