package com.nongxinle.ai.metric;

/**
 * 门店 / 经营单元维度（相对登录态）；一期占位，与 YAML {@code scope_strategy.store_scope} 对齐。
 */
public enum StoreScopeStrategy {

    /** 当前会话/当前所选门店包 */
    CURRENT,

    /** 全部门店（集团对比等），二期 */
    ALL;

    public static StoreScopeStrategy fromYaml(Object raw) {
        if (raw == null) {
            return CURRENT;
        }
        String s = raw.toString().trim().toUpperCase();
        try {
            return StoreScopeStrategy.valueOf(s);
        } catch (IllegalArgumentException e) {
            return CURRENT;
        }
    }
}
