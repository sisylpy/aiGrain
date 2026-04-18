package com.nongxinle.controller;

/**
 * 
 *
 * @author lpy
 * @date 10-07 09:12
 */

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.nongxinle.entity.GbDistributerAliasEntity;
import com.nongxinle.service.GbDistributerAliasService;
import com.nongxinle.utils.R;


@RestController
@RequestMapping("gbdistributeralias")
public class GbDistributerAliasController {
	@Autowired
	private GbDistributerAliasService gbDistributerAliasService;


	@RequestMapping(value = "/disDeleteAlias/{id}")
	@ResponseBody
	public R disDeleteAlias(@PathVariable Integer id) {
	    gbDistributerAliasService.delete(id);
	    return R.ok();
	}
	@RequestMapping(value = "/updateDisAlias", method = RequestMethod.POST)
	@ResponseBody
	public R updateDisAlias (@RequestBody GbDistributerAliasEntity alias) {
	    gbDistributerAliasService.update(alias);
	    return R.ok().put("data", alias);
	}
	
	@RequestMapping(value = "/saveDisAlias", method = RequestMethod.POST)
	@ResponseBody
	public R saveDisAlias (@RequestBody GbDistributerAliasEntity distributerAliasEntity ) {
	    gbDistributerAliasService.save(distributerAliasEntity);
	    return R.ok().put("data", distributerAliasEntity);
	}
	
}
