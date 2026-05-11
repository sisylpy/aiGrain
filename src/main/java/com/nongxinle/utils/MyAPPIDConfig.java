package com.nongxinle.utils;

import com.github.wxpay.sdk.WXPayConfig;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 特鲜送采购等业务使用的微信支付 V2 配置（统一下单 JSAPI）。
 * 注意：Maven 上的 wxpay-sdk 0.0.3 中 {@link WXPayConfig} 为接口，不含 IWXPayDomain。
 */
public class MyAPPIDConfig implements WXPayConfig {

    @Override
    public String getAppID() {
        return getTexiansongCaigouAppId();
    }

    public String getTexiansongCaigouAppId() {
        return "wx58ba279bc3d04c4a";
    }

    public String getTexiansongCaigouScreat() {
        return "07bcf1a46323e6c05fcf4404ddd0582f";
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
