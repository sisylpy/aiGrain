package com.nongxinle.service;

import com.nongxinle.dto.GbAiGoodsAddAnalyzeRequest;
import com.nongxinle.dto.GbAiGoodsAddConfirmRequest;
import com.nongxinle.utils.R;

public interface GbAiGoodsAddService {

    R analyze(GbAiGoodsAddAnalyzeRequest req);

    R confirm(GbAiGoodsAddConfirmRequest req);
}
