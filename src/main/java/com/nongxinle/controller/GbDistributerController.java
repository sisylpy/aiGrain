package com.nongxinle.controller;

import java.util.HashMap;
import java.util.Map;

import com.nongxinle.entity.GbDistributerEntity;
import com.nongxinle.service.GbDistributerService;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.nongxinle.utils.DateUtils.*;

/**
 * 批发商Controller
 */
@RestController
@RequestMapping("gbdistributer")
public class GbDistributerController {
    @Autowired
    private GbDistributerService gbDistributerService;



    @RequestMapping(value = "/updateDisContent", method = RequestMethod.POST)
    @ResponseBody
    public R updateDisContent (@RequestBody GbDistributerEntity dis) {
        gbDistributerService.updateById(dis);
        return R.ok();
    }


    @RequestMapping(value = "/getDisInfo/{id}")
    @ResponseBody
    public R getDisInfo(@PathVariable Integer id) {
        return R.ok().put("data", gbDistributerService.queryDistributerWithAllDepartments(id));
    }



    @RequestMapping(value = "/getDate")
    @ResponseBody
    public R getToday() {
        Map<String, Object> map = new HashMap<>();

        // day
        Map<String, Object> day = new HashMap<>();
        Map<String, Object> mapYesterday = new HashMap<>();
        mapYesterday.put("yesterdayDate", formatWhatDay(-1));
        mapYesterday.put("yesterdayStartDate", formatWhatDay(-1));
        mapYesterday.put("yesterdayStopDate", formatWhatDay(-1));
        mapYesterday.put("yesterdayString", formatWhatDayString(-1));
        mapYesterday.put("yesterdayWeek", getWeek(-1));
        day.put("yesterday", mapYesterday);

        Map<String, Object> mapToday = new HashMap<>();
        mapToday.put("todayDate", formatWhatDay(0));
        mapToday.put("todayStartDate", formatWhatDay(0));
        mapToday.put("todayStopDate", formatWhatDay(0));
        mapToday.put("todayString", formatWhatDayString(0));
        mapToday.put("todayWeek", getWeek(0));
        day.put("today", mapToday);

        // week
        Map<String, Object> week = new HashMap<>();
        Map<String, Object> lastSevenDay = new HashMap<>();
        lastSevenDay.put("lastSevenDayStartDate", formatWhatDay(-7));
        lastSevenDay.put("lastSevenDayStartDateString", formatWhatDayString(-7));
        lastSevenDay.put("lastSevenDayStopDate", formatWhatDay(-1));
        lastSevenDay.put("lastSevenDayStopDateString", formatWhatDayString(-1));
        week.put("lastSevenDay", lastSevenDay);

        Map<String, Object> thisWeek = new HashMap<>();
        thisWeek.put("thisWeekStartDate", thisWeekMonday());
        thisWeek.put("thisWeekStartString", thisWeekMondayString());
        thisWeek.put("thisWeekStopDate", thisWeekSunday());
        thisWeek.put("thisWeekStopString", thisWeekSundayString());
        week.put("thisWeek", thisWeek);

        Map<String, Object> lastWeek = new HashMap<>();
        lastWeek.put("lastWeekStartDate", getLastWeek());
        lastWeek.put("lastWeekStartString", thisWeekMondayString());
        lastWeek.put("lastWeekStopDate", thisWeekSunday());
        lastWeek.put("lastWeekStopString", thisWeekSundayString());
        week.put("lastWeek", lastWeek);

        // month
        Map<String, Object> month = new HashMap<>();
        Map<String, Object> lastThirtyDay = new HashMap<>();
        lastThirtyDay.put("lastThirtyDayStartDate", formatWhatDay(-30));
        lastThirtyDay.put("lastThirtyDayStartDateString", formatWhatDayString(-30));
        lastThirtyDay.put("lastThirtyDayStopDate", formatWhatDay(0));
        lastThirtyDay.put("lastThirtyDayStopDateString", formatWhatDayString(0));
        month.put("lastThirtyDay", lastThirtyDay);

        Map<String, Object> thisMonth = new HashMap<>();
        thisMonth.put("thisMonthStartDate", getThisMonthFirstDay());
        thisMonth.put("thisMonthStartDateString", formatWhatMonthString(0));
        thisMonth.put("thisMonthStopDate", getThisMonthLastDay());
        thisMonth.put("thisMonthStopDateString", formatWhatDayString(-1));
        month.put("thisMonth", thisMonth);

        Map<String, Object> lastMonth = new HashMap<>();
        lastMonth.put("lastMonthStartDate", getLastMonthFirstDay());
        lastMonth.put("lastMonthStartDateString", getLastMonthString());
        lastMonth.put("lastMonthStopDate", getLastMonthLastDay());
        lastMonth.put("lastMonthStopDateString", formatWhatDayString(-1));
        month.put("lastMonth", lastMonth);

        map.put("day", day);
        map.put("week", week);
        map.put("month", month);
        return R.ok().put("data", map);
    }
}
