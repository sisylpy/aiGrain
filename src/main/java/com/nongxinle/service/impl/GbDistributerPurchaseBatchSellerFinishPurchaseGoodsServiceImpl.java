package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerPurchaseBatchSellerFinishPurchaseGoodsService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatFullTime;
import static com.nongxinle.utils.DateUtils.formatWhatDay;
import static com.nongxinle.utils.DateUtils.formatWhatMonth;
import static com.nongxinle.utils.DateUtils.formatWhatYear;
import static com.nongxinle.utils.DateUtils.formatWhatYearDayTime;
import static com.nongxinle.utils.DateUtils.getWeek;
import static com.nongxinle.utils.DateUtils.getWeekOfYear;

@Service
@RequiredArgsConstructor
public class GbDistributerPurchaseBatchSellerFinishPurchaseGoodsServiceImpl
        implements GbDistributerPurchaseBatchSellerFinishPurchaseGoodsService {

    private final GbDistributerPurchaseGoodsService gbDPGService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final GbDistributerPurchaseBatchService gbDPBService;

    @Override
    public void sellerFinishPurchaseGoodsBatchGb(GbDistributerPurchaseBatchEntity batchEntity) {
        List<GbDistributerPurchaseGoodsEntity> nxDPBEntities = batchEntity.getGbDPGEntities();
        for (GbDistributerPurchaseGoodsEntity purGoods : nxDPBEntities) {
            purGoods.setGbDpgPayType(batchEntity.getGbDpbPayType());
            purGoods.setGbDpgSupplierFinishDate(formatWhatDay(0));
            purGoods.setGbDpgPurchaseDate(formatWhatDay(0));
            purGoods.setGbDpgPurchaseMonth(formatWhatMonth(0));
            purGoods.setGbDpgPurchaseYear(formatWhatYear(0));
            purGoods.setGbDpgPurchaseFullTime(formatWhatYearDayTime(0));
            purGoods.setGbDpgPurchaseWeek(getWeek(0));
            purGoods.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
            gbDPGService.updateById(purGoods);
            Map<String, Object> map = new HashMap<>();
            map.put("purGoodsId", purGoods.getGbDistributerPurchaseGoodsId());
            List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
            if (gbDepartmentOrdersEntities.size() > 0) {
                for (GbDepartmentOrdersEntity gbDepartmentOrdersEntity : gbDepartmentOrdersEntities) {
                    gbDepartmentOrdersEntity.setGbDoBuyStatus(4);
                    gbDepartmentOrdersEntity.setGbDoStatus(2);
                    gbDepartmentOrdersService.update(gbDepartmentOrdersEntity);
                }
            }
        }
        batchEntity.setGbDpbStatus(2);
        batchEntity.setGbDpbSellerReplyFullTime(formatFullTime());
        gbDPBService.updateById(batchEntity);
    }
}
