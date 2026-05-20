package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.constants.AiInsightDishProfitScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DIS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_QUERY_SCOPE_KIND;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_QUERY_STORE_IDS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;

/** 菜品毛利类 Tool 共用的 disId / 门店 scope 解析。 */
public final class DishProfitToolScopeSupport {

    private DishProfitToolScopeSupport() {
    }

    public record BaseArgs(
            Map<String, Object> args,
            Long departmentFatherId,
            Long disId,
            String startDate,
            String stopDate) {

        public boolean missingRequired() {
            return departmentFatherId == null || disId == null || startDate.isEmpty() || stopDate.isEmpty();
        }
    }

    public static BaseArgs parseBaseArgs(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        return new BaseArgs(
                args,
                toLong(args.get(ARG_DEPARTMENT_FATHER_ID)),
                toLong(args.get(ARG_DIS_ID)),
                argStr(args.get(ARG_START_DATE)),
                argStr(args.get(ARG_STOP_DATE)));
    }

    public record ResolvedScope(
            int disId,
            int depFatherIdInt,
            Long departmentFatherId,
            boolean groupWideMendianAggregate,
            String queryScopeKind,
            List<Integer> queryStoreIds) {
    }

    public static ResolvedScope resolveScope(Long disLong, Long dept, Map<String, Object> args) {
        int disId = disLong.intValue();
        int depFatherIdInt = dept.intValue();
        Long departmentFatherId = dept;
        String qsk = argStr(args.get(ARG_QUERY_SCOPE_KIND));
        List<Integer> qStoreIdsArg = normalizeResolvedDeptIds(args.get(ARG_QUERY_STORE_IDS));
        if (AiResolvedDataScope.QUERY_SCOPE_KIND_STORE.equals(qsk) && qStoreIdsArg.size() == 1) {
            depFatherIdInt = qStoreIdsArg.get(0);
            departmentFatherId = (long) depFatherIdInt;
        }
        boolean groupWideAgg = AiInsightDishProfitScope.isGroupWideMendianAggregateUnderDis(depFatherIdInt);
        return new ResolvedScope(
                disId, depFatherIdInt, departmentFatherId, groupWideAgg, qsk, List.copyOf(qStoreIdsArg));
    }

    public static List<Integer> normalizeResolvedDeptIds(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> col) {
            ArrayList<Integer> out = new ArrayList<>();
            for (Object x : col) {
                if (x instanceof Number n) {
                    out.add(n.intValue());
                } else if (x != null) {
                    try {
                        out.add(Integer.parseInt(x.toString().trim()));
                    } catch (Exception ignore) {
                        // skip
                    }
                }
            }
            return out;
        }
        return List.of();
    }

    public static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static String argStr(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
