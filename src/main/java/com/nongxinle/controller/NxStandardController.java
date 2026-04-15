package com.nongxinle.controller;

import com.nongxinle.entity.NxStandardEntity;
import com.nongxinle.service.NxStandardService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 农鑫商品规格Controller
 */
@RestController
@RequestMapping("nxstandard")
@Tag(name = "农鑫商品规格", description = "商品规格（规格名称、价格、单位等）的增删改查")
public class NxStandardController {

    @Autowired
    private NxStandardService nxStandardService;

    /**
     * 删除规格
     */
    @Operation(summary = "删除规格", description = "根据规格ID删除指定的商品规格")
    @RequestMapping(value = "/deleteStandard/{id}", method = RequestMethod.POST)
    public R deleteStandard(@PathVariable @Parameter(description = "规格ID") Integer id) {
        nxStandardService.removeById(id);
        return R.ok();
    }

    /**
     * 保存规格
     */
    @Operation(summary = "新增规格", description = "创建新的商品规格记录")
    @RequestMapping(value = "/saveNxStandard", method = RequestMethod.POST)
    public R saveNxStandard(@RequestBody @Parameter(description = "规格信息") NxStandardEntity standard) {
        nxStandardService.save(standard);
        return R.ok().put("data", standard);
    }

    /**
     * 根据商品ID查询规格列表
     */
    @Operation(summary = "查询商品规格列表", description = "根据商品ID查询该商品下的所有规格记录")
    @RequestMapping("/list/{nxGoodsId}")
    public R list(@PathVariable @Parameter(description = "商品ID") Integer nxGoodsId) {
        List<NxStandardEntity> nxStandardList = nxStandardService.queryList(nxGoodsId);
        return R.ok().put("data", nxStandardList);
    }

    /**
     * 获取规格详情
     */
    @Operation(summary = "获取规格详情", description = "根据规格ID获取单个规格的详细信息")
    @RequestMapping("/info/{nxStandardId}")
    public R info(@PathVariable("nxStandardId") @Parameter(description = "规格ID") Integer nxStandardId) {
        NxStandardEntity nxStandard = nxStandardService.getById(nxStandardId);
        return R.ok().put("nxStandard", nxStandard);
    }

    /**
     * 更新规格
     */
    @Operation(summary = "更新规格", description = "根据规格ID更新规格信息")
    @RequestMapping(value = "/updateStandard", method = RequestMethod.POST)
    public R updateStandard(@RequestBody @Parameter(description = "规格信息") NxStandardEntity nxStandard) {
        nxStandardService.updateById(nxStandard);
        return R.ok();
    }
}
