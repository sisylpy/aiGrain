package com.nongxinle.ai.identity;

import com.nongxinle.ai.semantic.intake.grounding.EntityExistence;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbDistributerGoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 业务实体 DB 存在性探测 SSOT：DISH / GOODS 只读同名匹配，不改 Mapper/SQL。 */
@Component
@RequiredArgsConstructor
public class BusinessEntityExistenceLookup {

    public static final String CLARIFICATION_GOODS_NOT_FOUND =
            "未找到匹配的库存原料，请确认名称是否正确，或说出更完整的原料名。";

    private static final String CLARIFICATION_GOODS_AMBIGUOUS =
            "找到多个同名库存原料，请说更完整的原料名。";

    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDistributerFoodService gbDistributerFoodService;

    public EntityExistence probeDish(int disId, String entityName) {
        if (!StringUtils.hasText(entityName)) {
            return EntityExistence.NOT_FOUND;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("foodName", entityName.trim());
        List<GbDistributerFoodEntity> foods = gbDistributerFoodService.queryFoodByParams(map);
        Set<Integer> ids = collectFoodIds(foods);
        return toExistence(ids);
    }

    public EntityExistence probeGoods(int disId, String entityName) {
        if (!StringUtils.hasText(entityName)) {
            return EntityExistence.NOT_FOUND;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        String hint = entityName.trim();
        map.put(containsHan(hint) ? "searchStr" : "searchPinyin", hint);
        List<GbDistributerGoodsEntity> hits = gbDistributerGoodsService.queryGbDisGoodsQuickSearchStr(map);
        Set<Integer> ids = collectGoodsIds(hits);
        return toExistence(ids);
    }

    public GoodsNameLookupResult lookupGoodsByName(int disId, String entityName) {
        if (!StringUtils.hasText(entityName)) {
            return GoodsNameLookupResult.notFound(null, CLARIFICATION_GOODS_NOT_FOUND, disId, null, 0);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        String hint = entityName.trim();
        String searchKey = containsHan(hint) ? "searchStr" : "searchPinyin";
        map.put(searchKey, hint);
        List<GbDistributerGoodsEntity> hits = gbDistributerGoodsService.queryGbDisGoodsQuickSearchStr(map);
        int rawHitCount = hits == null ? 0 : hits.size();
        if (hits == null || hits.isEmpty()) {
            return GoodsNameLookupResult.notFound(
                    hint, CLARIFICATION_GOODS_NOT_FOUND, disId, searchKey + "=" + hint, rawHitCount);
        }
        LinkedHashMap<Integer, String> ids = new LinkedHashMap<>();
        for (GbDistributerGoodsEntity e : hits) {
            if (e != null && e.getGbDistributerGoodsId() != null) {
                ids.putIfAbsent(
                        e.getGbDistributerGoodsId(),
                        e.getGbDgGoodsName() == null ? "" : e.getGbDgGoodsName().trim());
            }
        }
        if (ids.isEmpty()) {
            return GoodsNameLookupResult.notFound(
                    hint, CLARIFICATION_GOODS_NOT_FOUND, disId, searchKey + "=" + hint, rawHitCount);
        }
        if (ids.size() == 1) {
            Map.Entry<Integer, String> only = ids.entrySet().iterator().next();
            return GoodsNameLookupResult.unique(
                    hint, only.getKey(), only.getValue(), disId, searchKey + "=" + hint, rawHitCount);
        }
        List<EntityIdentityCandidate> candidates = new ArrayList<>();
        for (Map.Entry<Integer, String> e : ids.entrySet()) {
            candidates.add(
                    EntityIdentityCandidate.builder()
                            .entityId(e.getKey())
                            .canonicalName(e.getValue())
                            .build());
        }
        return GoodsNameLookupResult.ambiguous(
                hint, candidates, CLARIFICATION_GOODS_AMBIGUOUS, disId, searchKey + "=" + hint, ids.size());
    }

    public GoodsIdLookupResult lookupGoodsById(int disGoodsId) {
        if (disGoodsId <= 0) {
            return GoodsIdLookupResult.notFound();
        }
        GbDistributerGoodsEntity entity = gbDistributerGoodsService.queryObject(disGoodsId);
        if (entity == null || entity.getGbDistributerGoodsId() == null) {
            return GoodsIdLookupResult.notFound();
        }
        String canonical =
                StringUtils.hasText(entity.getGbDgGoodsName())
                        ? entity.getGbDgGoodsName().trim()
                        : null;
        return GoodsIdLookupResult.found(entity.getGbDistributerGoodsId(), canonical);
    }

    private static EntityExistence toExistence(Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return EntityExistence.NOT_FOUND;
        }
        return ids.size() == 1 ? EntityExistence.UNIQUE : EntityExistence.AMBIGUOUS;
    }

    private static Set<Integer> collectFoodIds(List<GbDistributerFoodEntity> foods) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        if (foods == null) {
            return ids;
        }
        for (GbDistributerFoodEntity food : foods) {
            if (food != null && food.getGbDistributerFoodId() != null) {
                ids.add(food.getGbDistributerFoodId());
            }
        }
        return ids;
    }

    private static Set<Integer> collectGoodsIds(List<GbDistributerGoodsEntity> hits) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        if (hits == null) {
            return ids;
        }
        for (GbDistributerGoodsEntity e : hits) {
            if (e != null && e.getGbDistributerGoodsId() != null) {
                ids.add(e.getGbDistributerGoodsId());
            }
        }
        return ids;
    }

    private static boolean containsHan(String s) {
        if (!StringUtils.hasText(s)) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    public record GoodsNameLookupResult(
            EntityIdentityResolutionStatus status,
            String userMention,
            Integer disGoodsId,
            String canonicalName,
            List<EntityIdentityCandidate> candidates,
            String clarificationMessage,
            int lookupDisId,
            String lookupSearchParam,
            int lookupHitCount) {

        static GoodsNameLookupResult unique(
                String userMention,
                int disGoodsId,
                String canonicalName,
                int lookupDisId,
                String lookupSearchParam,
                int lookupHitCount) {
            return new GoodsNameLookupResult(
                    EntityIdentityResolutionStatus.OK,
                    userMention,
                    disGoodsId,
                    canonicalName,
                    List.of(),
                    null,
                    lookupDisId,
                    lookupSearchParam,
                    lookupHitCount);
        }

        static GoodsNameLookupResult notFound(
                String userMention, String message, int lookupDisId, String lookupSearchParam, int lookupHitCount) {
            return new GoodsNameLookupResult(
                    EntityIdentityResolutionStatus.NOT_FOUND,
                    userMention,
                    null,
                    null,
                    List.of(),
                    message,
                    lookupDisId,
                    lookupSearchParam,
                    lookupHitCount);
        }

        static GoodsNameLookupResult ambiguous(
                String userMention,
                List<EntityIdentityCandidate> candidates,
                String message,
                int lookupDisId,
                String lookupSearchParam,
                int lookupHitCount) {
            return new GoodsNameLookupResult(
                    EntityIdentityResolutionStatus.NEED_CLARIFICATION,
                    userMention,
                    null,
                    null,
                    candidates,
                    message,
                    lookupDisId,
                    lookupSearchParam,
                    lookupHitCount);
        }
    }

    public record GoodsIdLookupResult(
            EntityIdentityResolutionStatus status, Integer disGoodsId, String canonicalName) {

        static GoodsIdLookupResult found(int disGoodsId, String canonicalName) {
            return new GoodsIdLookupResult(EntityIdentityResolutionStatus.OK, disGoodsId, canonicalName);
        }

        static GoodsIdLookupResult notFound() {
            return new GoodsIdLookupResult(EntityIdentityResolutionStatus.NOT_FOUND, null, null);
        }
    }
}
