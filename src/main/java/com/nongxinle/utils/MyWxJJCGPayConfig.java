package com.nongxinle.utils;

import com.github.wxpay.sdk.WXPayConfig;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 京京采购等场景使用的微信支付 V2 配置。
 * 若与 {@link MyAPPIDConfig} 共用同一小程序与商户号，可保持与之一致；否则在此替换 appId / key / mchId。
 */
public class MyWxJJCGPayConfig implements WXPayConfig {

    @Override
    public String getAppID() {
        return "wx58ba279bc3d04c4a";
    }

    @Override
    public String getMchID() {
        return "1594384761";
    }

    @Override
    public String getKey() {
        return "sisy112578sisy112578sisy112578cf";
    }

    @Override
    public InputStream getCertStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int getHttpConnectTimeoutMs() {
        return 6_000;
    }

    @Override
    public int getHttpReadTimeoutMs() {
        return 8_000;
    }
}
