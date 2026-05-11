package com.nongxinle.ai.dto.business;

import java.util.ArrayList;
import java.util.List;

/**
 * 集团经营概览正文：问题门店 Top 列表（数据缺失优先，其次经营异常）。
 */
public final class AiGroupOverviewStoreBrief {

    public static final int MAX_PREVIEW_LINES = 3;

    private AiGroupOverviewStoreBrief() {
    }

    /**
     * @return 有问题门店时返回「需要优先关注的门店：」引导的多行文案；若两类均为空则返回 {@code null}
     */
    public static String formatPriorityBrief(List<AiOverviewStoreIssueItem> dataMissing,
            List<AiOverviewStoreIssueItem> attentionStores) {
        List<AiOverviewStoreIssueItem> head = new ArrayList<>(MAX_PREVIEW_LINES);
        if (dataMissing != null) {
            for (AiOverviewStoreIssueItem item : dataMissing) {
                if (head.size() >= MAX_PREVIEW_LINES) {
                    break;
                }
                head.add(item);
            }
        }
        if (attentionStores != null) {
            for (AiOverviewStoreIssueItem item : attentionStores) {
                if (head.size() >= MAX_PREVIEW_LINES) {
                    break;
                }
                head.add(item);
            }
        }
        if (head.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("需要优先关注的门店：");
        int i = 1;
        for (AiOverviewStoreIssueItem row : head) {
            sb.append('\n').append(i++).append(". ").append(row.getStoreName()).append("：");
            String r = row.getReason();
            sb.append(r == null ? "" : r.trim()).append('；');
        }
        return sb.toString().trim();
    }

    /** 集团广角且两类问题门店均为空、且经营看板有效时使用 */
    public static String noIssuesLine() {
        return "当前没有识别到明显异常门店。";
    }

    /**
     * 正文用：本轮在权限范围内可见、但台账/画像等未满足经营看板完整统计的门店（仅列店名，不展开原因）。
     */
    public static String formatStoresWithoutRevenueBrief(List<AiOverviewStoreIssueItem> dataMissing) {
        if (dataMissing == null || dataMissing.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("暂无日营收记录的门店：");
        int i = 1;
        for (AiOverviewStoreIssueItem row : dataMissing) {
            if (row == null || row.getStoreName() == null || row.getStoreName().isBlank()) {
                continue;
            }
            sb.append('\n').append(i++).append(". ").append(row.getStoreName().trim());
        }
        if (i <= 1) {
            return "";
        }
        return sb.toString().trim();
    }
}
