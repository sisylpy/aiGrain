package com.nongxinle.ai.orchestration;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 解析「扩充农鑫目录」：在用户确认二级后，由模型给出新增品名父节点（库 level=2）与 SKU（库 level=3）的 JSON。
 */
public final class GoodsCatalogExpandLlmParser {

    public record ParsedExpand(
            String level3Name,
            String level4DisplayName,
            String level4StandardName,
            String level4Detail,
            boolean structuredOk
    ) {
        public static ParsedExpand invalid() {
            return new ParsedExpand(null, null, null, null, false);
        }
    }

    private GoodsCatalogExpandLlmParser() {
    }

    public static ParsedExpand parse(String raw) {
        if (StrUtil.isBlank(raw)) {
            return ParsedExpand.invalid();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int fence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && fence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, fence).trim();
            }
        }
        if (!trimmed.startsWith("{")) {
            return ParsedExpand.invalid();
        }
        try {
            JSONObject o = JSONUtil.parseObj(trimmed);
            String l3 = StrUtil.trimToEmpty(o.getStr("level3Name", ""));
            String l4n = StrUtil.trimToEmpty(o.getStr("level4DisplayName", ""));
            String l4s = StrUtil.trimToEmpty(o.getStr("level4StandardName", ""));
            String l4d = StrUtil.trimToEmpty(o.getStr("level4Detail", ""));
            if (l3.isEmpty() || l4n.isEmpty() || l4s.isEmpty()) {
                return ParsedExpand.invalid();
            }
            if (l3.length() > 64 || l4n.length() > 64 || l4s.length() > 64) {
                return ParsedExpand.invalid();
            }
            if (l4d.length() > 500) {
                l4d = l4d.substring(0, 500);
            }
            return new ParsedExpand(l3, l4n, l4s, l4d.isEmpty() ? null : l4d, true);
        } catch (Exception e) {
            return ParsedExpand.invalid();
        }
    }
}
