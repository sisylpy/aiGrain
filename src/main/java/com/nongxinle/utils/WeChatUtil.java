package com.nongxinle.utils;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;

/**
 * 微信工具类
 */
public class WeChatUtil {

    private static final int CONNECT_TIMEOUT = 10000; // 连接超时 10秒
    private static final int READ_TIMEOUT = 10000;    // 读取超时 10秒

    public static String httpRequest(String requestUrl, String requestMethod, String output) {
        System.out.println("requestUrl-====" + requestUrl);
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(requestUrl);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod(requestMethod);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            if (null != output) {
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(output.getBytes("utf-8"));
                outputStream.close();
            }

            // 从输入流读取返回内容
            InputStream inputStream = connection.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "utf-8");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String str;
            StringBuilder buffer = new StringBuilder();
            while ((str = bufferedReader.readLine()) != null) {
                buffer.append(str);
            }
            bufferedReader.close();
            inputStreamReader.close();
            inputStream.close();
            return buffer.toString();
        } catch (Exception e) {
            System.err.println("微信HTTP请求失败: " + e.getMessage());
            e.printStackTrace();
            return null; // 返回null而不是空字符串，方便调用方判断
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
