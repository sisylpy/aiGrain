package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseGoodsDetailListService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.afterWhatDay;
import static com.nongxinle.utils.DateUtils.getHowManyDaysInPeriod;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseGoodsDetailListServiceImpl implements GbDistributerPurchaseGoodsDetailListService {

    private final GbDistributerPurchaseGoodsService gbDpgService;
    private final GbDistributerGoodsService gbDistributerGoodsService;

    @Override
    public Map<String, Object> buildPurGoodsDetailList(Integer disGoodsId, String startDate, String stopDate) {
        Integer howManyDaysInPeriod = 0;
        if (!startDate.equals(stopDate)) {
            howManyDaysInPeriod = getHowManyDaysInPeriod(stopDate, startDate);
        }
        int periodDays = howManyDaysInPeriod == null ? 0 : howManyDaysInPeriod;
        Map<String, Object> mapResult = new HashMap<>();

        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("disGoodsId", disGoodsId);
        queryMap.put("startDate", startDate);
        queryMap.put("stopDate", stopDate);
        queryMap.put("dayuStatus", 2);
        queryMap.put("typeNotEqual", 9);
        GbDistributerGoodsEntity disGoodsForQuery = gbDistributerGoodsService.queryObject(disGoodsId);
        if (disGoodsForQuery != null && disGoodsForQuery.getGbDgDistributerId() != null) {
            queryMap.put("disId", disGoodsForQuery.getGbDgDistributerId());
        }

        log.debug("查询商品map{}", queryMap);
        log.debug("开始查询商品列表...");
        List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntityList =
                gbDpgService.queryPurchaseGoodsWithDetailByParams(queryMap);

        List<Map<String, Object>> purchaseDayValue = new ArrayList<>();

        if (periodDays > 0) {
            for (int i = 0; i < periodDays + 1; i++) {
                Map<String, Object> mapEvery = new HashMap<>();

                String whichDay;
                if (i == 0) {
                    whichDay = startDate;
                } else {
                    whichDay = afterWhatDay(startDate, i);
                }
                Map<String, Object> mapDay = new HashMap<>();
                mapDay.put("date", whichDay);
                mapDay.put("disGoodsId", disGoodsId);
                mapDay.put("typeNotEqual", 9);
                Integer integer1 = gbDpgService.queryGbPurchaseGoodsCount(mapDay);
                mapEvery.put("date", whichDay);
                int cnt = integer1 == null ? 0 : integer1;
                if (cnt > 0) {
                    Double subTotal = gbDpgService.queryPurchaseGoodsSubTotal(mapDay);
                    mapEvery.put("purSubtotal", String.format("%.1f", subTotal));
                } else {
                    mapEvery.put("purSubtotal", 0);
                }
                purchaseDayValue.add(mapEvery);
            }
        }

        mapResult.put("arr", purchaseGoodsEntityList);
        mapResult.put("itemList", purchaseDayValue);
        return mapResult;
    }
}
