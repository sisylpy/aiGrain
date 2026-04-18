package com.nongxinle.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.nongxinle.service.WeNoticeService;
import com.nongxinle.utils.MyAPPIDConfig;
import com.nongxinle.utils.WeChatUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信通知服务实现类
 */
@Slf4j
@Service
public class WeNoticeServiceImpl implements WeNoticeService {

    // 订单变更提醒模板消息ID（需要在微信后台配置）
    private static final String ORDER_CHANGE_TEMPLATE_ID = "YOUR_TEMPLATE_ID"; // TODO: 替换为实际的模板ID

    @Override
    public boolean changeOrderSuppliertixingMessageJj(String openId, String page, Map<String, TemplateData> data) {
        try {
            if (openId == null || openId.trim().isEmpty()) {
                log.warn("发送微信通知失败: openId为空");
                return false;
            }

            // 获取access_token
            String accessToken = getAccessToken();
            if (accessToken == null) {
                log.error("获取access_token失败");
                return false;
            }

            // 构建发送模板消息的请求
            String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken;
            
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("touser", openId);  // 用户openId
            requestData.put("template_id", ORDER_CHANGE_TEMPLATE_ID);  // 模板ID
            requestData.put("page", page);  // 点击后跳转的页面
            
            // 构建模板数据
            Map<String, Object> templateData = new HashMap<>();
            if (data != null) {
                for (Map.Entry<String, TemplateData> entry : data.entrySet()) {
                    Map<String, String> item = new HashMap<>();
                    item.put("value", entry.getValue().getValue() != null ? entry.getValue().getValue() : "");
                    templateData.put(entry.getKey(), item);
                }
            }
            requestData.put("data", templateData);

            // 发送请求
            String result = WeChatUtil.httpRequest(url, "POST", JSONObject.toJSONString(requestData));
            log.info("微信模板消息发送结果: {}", result);

            if (result != null) {
                JSONObject resultJson = JSONObject.parseObject(result);
                Integer errcode = resultJson.getInteger("errcode");
                if (errcode != null && errcode == 0) {
                    log.info("微信通知发送成功, openId: {}", openId);
                    return true;
                } else {
                    log.error("微信通知发送失败, errcode: {}, errmsg: {}", 
                            resultJson.getInteger("errcode"), resultJson.getString("errmsg"));
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("发送微信通知异常", e);
            return false;
        }
    }

    /**
     * 获取微信access_token
     */
    private String getAccessToken() {
        try {
            MyAPPIDConfig myAPPIDConfig = new MyAPPIDConfig();
            String appId = myAPPIDConfig.getTexiansongCaigouAppId();
            String secret = myAPPIDConfig.getTexiansongCaigouScreat();

            String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=" 
                    + appId + "&secret=" + secret;
            
            String result = WeChatUtil.httpRequest(url, "GET", null);
            if (result != null) {
                JSONObject jsonObject = JSONObject.parseObject(result);
                return jsonObject.getString("access_token");
            }
        } catch (Exception e) {
            log.error("获取access_token异常", e);
        }
        return null;
    }

    @Override
    public boolean autoGbSuppliertixingMessageJj(String openId, String page, Map<String, TemplateData> data) {
        try {
            if (openId == null || openId.trim().isEmpty()) {
                log.warn("发送微信通知失败: openId为空");
                return false;
            }

            // 获取access_token
            String accessToken = getAccessToken();
            if (accessToken == null) {
                log.error("获取access_token失败");
                return false;
            }

            // 构建发送模板消息的请求
            String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken;
            
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("touser", openId);
            requestData.put("template_id", "YOUR_TEMPLATE_ID"); // TODO: 替换为实际的模板ID
            requestData.put("page", page);
            
            // 构建模板数据
            Map<String, Object> templateData = new HashMap<>();
            if (data != null) {
                for (Map.Entry<String, TemplateData> entry : data.entrySet()) {
                    Map<String, String> item = new HashMap<>();
                    item.put("value", entry.getValue().getValue() != null ? entry.getValue().getValue() : "");
                    templateData.put(entry.getKey(), item);
                }
            }
            requestData.put("data", templateData);

            // 发送请求
            String result = WeChatUtil.httpRequest(url, "POST", JSONObject.toJSONString(requestData));
            log.info("微信模板消息发送结果: {}", result);

            if (result != null) {
                JSONObject resultJson = JSONObject.parseObject(result);
                Integer errcode = resultJson.getInteger("errcode");
                if (errcode != null && errcode == 0) {
                    log.info("微信通知发送成功, openId: {}", openId);
                    return true;
                } else {
                    log.error("微信通知发送失败, errcode: {}", resultJson.getInteger("errcode"));
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("发送微信通知异常", e);
            return false;
        }
    }
}
