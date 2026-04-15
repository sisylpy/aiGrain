package com.nongxinle.controller;

import com.nongxinle.entity.GbAiRestaurantProfileEntity;
import com.nongxinle.service.GbAiRestaurantProfileService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


/**
 * AI餐厅画像
 *
 * @author lpy
 * @date 2026-04-11
 */
@RestController
@RequestMapping("gbairestaurantprofile")
@Tag(name = "AI餐厅画像接口")
public class GbAiRestaurantProfileController {

    @Autowired
    private GbAiRestaurantProfileService gbAiRestaurantProfileService;

    /**
     * 获取部门AI画像
     *
     * @param departmentId 部门ID
     * @return AI餐厅画像信息
     */
    @Operation(summary = "获取部门AI画像", description = "根据部门ID获取该部门的AI餐厅画像信息")
    @GetMapping("/info/{departmentId}")
    public R info(@Parameter(description = "部门ID") @PathVariable("departmentId") Long departmentId) {
        GbAiRestaurantProfileEntity profile = gbAiRestaurantProfileService.getByDepartmentId(departmentId);
        return R.ok().put("data", profile);
    }

    /**
     * 创建或更新部门AI画像
     *
     * @param profile 画像信息
     * @return 保存结果
     */
    @Operation(summary = "创建或更新部门AI画像", description = "根据部门ID创建或更新该部门的AI餐厅画像")
    @PostMapping("/saveProfile")
    public R saveProfile(@RequestBody GbAiRestaurantProfileEntity profile) {
        gbAiRestaurantProfileService.saveOrUpdateProfile(profile);
        return R.ok();
    }

}
