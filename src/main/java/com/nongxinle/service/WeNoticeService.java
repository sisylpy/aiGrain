package com.nongxinle.service;

import java.util.Map;

/**
 * 微信通知服务接口
 */
public interface WeNoticeService {

    /**
     * 发送订单变更提醒模板消息（供应商端）
     * @param openId 用户openId
     * @param page 跳转页面路径
     * @param data 模板数据
     * @return 是否发送成功
     */
    boolean changeOrderSuppliertixingMessageJj(String openId, String page, Map<String, TemplateData> data);

    /**
     * 发送自动采购提醒模板消息（供应商端）
     * @param openId 用户openId
     * @param page 跳转页面路径
     * @param data 模板数据
     * @return 是否发送成功
     */
    boolean autoGbSuppliertixingMessageJj(String openId, String page, Map<String, TemplateData> data);

    /**
     * 模板消息数据
     */
    class TemplateData {
        private String value;

        public TemplateData() {
        }

        public TemplateData(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
