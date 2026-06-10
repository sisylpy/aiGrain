package com.nongxinle.ai.identity;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.Map;

/**
 * GOODS identity DB lookup 的业务范围：与 {@link com.nongxinle.ai.graph.business.PurchaseOverviewToolExecutor}
 * 写入 {@code ARG_DIS_ID} 的口径对齐（分销商商品目录 scope）。
 */
public final class BusinessEntityIdentityScopeSupport {

    private BusinessEntityIdentityScopeSupport() {}

    /**
     * @param distributerIdHint 已组装的 Tool args {@code disId}（优先）；否则读 resolved org/data scope。
     */
    public static Integer resolveGoodsLookupDisId(
            AiResolvedQueryContext ctx, Integer distributerIdHint) {
        if (distributerIdHint != null && distributerIdHint > 0) {
            return distributerIdHint;
        }
        if (ctx == null) {
            return null;
        }
        AiResolvedOrgScope org = ctx.getOrgScope();
        if (org != null && org.getDistributerId() != null) {
            long dis = org.getDistributerId();
            if (dis > 0 && dis <= Integer.MAX_VALUE) {
                return (int) dis;
            }
        }
        AiResolvedDataScope data = ctx.getDataScope();
        if (data != null && data.getQueryDistributerId() != null && data.getQueryDistributerId() > 0) {
            return data.getQueryDistributerId();
        }
        return null;
    }

    public static Integer disIdFromToolArgs(Map<String, Object> toolArgs) {
        if (toolArgs == null) {
            return null;
        }
        return parsePositiveInt(toolArgs.get(AiBusinessToolIds.ARG_DIS_ID));
    }

    private static Integer parsePositiveInt(Object raw) {
        if (raw instanceof Number n) {
            int v = n.intValue();
            return v > 0 ? v : null;
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                int v = Integer.parseInt(s.trim());
                return v > 0 ? v : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
