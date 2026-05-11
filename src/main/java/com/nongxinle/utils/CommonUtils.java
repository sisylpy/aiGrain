/**
 * com.nongxinle.utils class
 *
 * @Author: peiyi li
 * @Date: 2020-05-23 09:34
 */

package com.nongxinle.utils;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

/**
 *@author lpy
 *@date 2020-05-23 09:34
 */


public class CommonUtils {


    public static String generatePickNumber (int which){

        Random random = new Random();
        int wxCountAuto = random.nextInt(which) + 1;
        String sWhich = String.valueOf(wxCountAuto);
        System.out.println("999999999wxCountAuto=="+ wxCountAuto);
        int rannum= (int)(random.nextDouble()*(999-100 + 1))+ 100;
        String s1 = new StringBuilder(sWhich).append(rannum).toString();
        return s1;
    }


    public static String generateUUID (){
        return UUID.randomUUID().toString().replace("-", "")
                 .substring(0, 32);
    }

    public static String generateBillTradeNo (String areaCode){
        Random random = new Random();
        int rannum= (int)(random.nextDouble()*(99999-10000 + 1))+ 10000;
        String s1 = new StringBuilder(areaCode).append(rannum).toString();
        return s1;
    }

    public static String generateOutTradeNo (){

        String s = new SimpleDateFormat("yyyyMMdd").format(new Date()).toString();

        String substring = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 24);
        StringBuilder sb = new StringBuilder(s);
        String s1 = new StringBuilder(substring).append(sb).toString();
        return s1;
    }


    public static String MD5(String md5) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest
                    .getInstance("MD5");
            byte[] array = md.digest(md5.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100)
                        .substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
        }
        return null;
    }

    /**
     * 每箱数量（库 varchar / 接口字符串），解析为 BigDecimal；空或非法返回 null。
     */
    public static BigDecimal parseItemsPerCartonBigDecimal(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isPositiveItemsPerCarton(String raw) {
        BigDecimal bd = parseItemsPerCartonBigDecimal(raw);
        return bd != null && bd.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * JSON / Object 转为可入库的每箱数量字符串；无法识别返回 null。
     */
    public static String normalizeItemsPerCartonString(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            return s.isEmpty() ? null : s;
        }
        if (raw instanceof Number) {
            try {
                return new BigDecimal(raw.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 通用数字字符串解析；null、空串、非法格式返回 defaultValue。
     */
    public static BigDecimal parseBigDecimalOrDefault(String raw, BigDecimal defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean isStrictlyPositiveDecimalString(String raw) {
        BigDecimal bd = parseBigDecimalOrDefault(raw, BigDecimal.ZERO);
        return bd.compareTo(BigDecimal.ZERO) > 0;
    }

}
