package com.nongxinle.utils;

/**
 * 商品类型
 */
public class NxJrdhTypeUtils {

    public final static Integer NX_JRDH_USER_ADMIN_NX_PURCHASER = 1; //nx采购员
    public final static Integer NX_JRDH_USER_ADMIN_GB_PURCHASER = 2; //gb采购员
    public final static Integer NX_JRDH_USER_ADMIN_NX_SELLER = 3; //nx和gb卖家
//    public final static Integer NX_JRDH_USER_ADMIN_GB_SELLER = 4; //gb卖家

    public static Integer getNxJrdhUserAdminNxPurchaser() {
        return NX_JRDH_USER_ADMIN_NX_PURCHASER;
    }

    public static Integer getNxJrdhUserAdminGbPurchaser() {
        return NX_JRDH_USER_ADMIN_GB_PURCHASER;
    }

    public static Integer getNxJrdhUserAdminNxSeller() {
        return NX_JRDH_USER_ADMIN_NX_SELLER;
    }
//    public static Integer getNxJrdhUserAdminGbSeller() {
//        return NX_JRDH_USER_ADMIN_GB_SELLER;
//    }
}
