package com.nongxinle.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 按一级分类查询商品请求参数
 * 
 * @author lpy
 */
@Data
@Schema(description = "按一级分类查询商品请求参数")
public class NxGoodsByGreatGrandIdDTO {

    @Schema(description = "一级分类ID (greatGrandId)", example = "1")
    private Integer greatGrandId;

    @Schema(description = "部门ID", example = "1")
    private Integer depId;

    @Schema(description = "批发商ID", example = "1")
    private Integer disId;

    @Schema(description = "每页数量", example = "15")
    private Integer limit;

    @Schema(description = "当前页码", example = "1")
    private Integer page;
}
