package com.nongxinle.utils;

import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 日期处理
 */
public class DateUtils {

    // 汉字数字
    private static final String[] numerals = {"零", "壹", "貳", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    // 单位
    private static final String[] units = {"", "拾", "佰", "仟"};
    private static final String[] bigUnits = {"", "万", "亿"};
    private static final String[] decimals = {"角", "分"};

    /**
     * 时间格式(yyyy-MM-dd)
     */
    public final static String DATE_PATTERN = "yyyy-MM-dd";
    public final static String DATE_NUMBER_PATTERN = "yyyyMMdd";

    /**
     * 时间格式(yyyy-MM-dd HH:mm:ss)
     */
    public final static String FULL_TIME_PATTERN = "yyyy-MM-dd HH:mm";

    public final static String YEAR_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
    public final static String DATE_TIME_PATTERN = "MM-dd HH:mm";
    public final static String ONLY_DATE_PATTERN = "MM-dd";
    public final static String MONTH_TIME_PATTERN = "MM";
    public final static String Year_TIME_PATTERN = "yyyy";
    public final static String ONLY_HAO_PATTERN = "dd";


    public static String format(Date date) {
        return format(date, DATE_PATTERN);
    }

    public static String format(Date date, String pattern) {
        if (date != null) {
            SimpleDateFormat df = new SimpleDateFormat(pattern);
            return df.format(date);
        }
        return null;
    }


    public static Boolean getIfCanReceiveTime() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        if (hour == 0 && minute < 5) {
            System.out.println("当前时间是零点");
            return false;
        } else {
            System.out.println("当前时间不是零点");
            return true;
        }
    }





    public static String getWillPayDate(int weeksAfter,int dayOfWeek) {
        Calendar calendar = Calendar.getInstance();
        // 添加指定的周数
        calendar.add(Calendar.WEEK_OF_YEAR, weeksAfter);
        // 设置指定的星期几
        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(calendar.getTime());
    }


    public static String formatFullTime() {
        return format(new Date(), FULL_TIME_PATTERN);
    }

    public static String formatWhatFullTime(int what) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, what);
        Date time = calendar.getTime();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(FULL_TIME_PATTERN);
        String format = simpleDateFormat.format(time);
        return format;
    }


    public static String formatWhatDayString(int what) {

//        Date whatDay = calendarDay(what).getTime();
//
//        SimpleDateFormat dateFormat2 = new SimpleDateFormat(DATE_PATTERN);
//        String format1 = dateFormat2.format(whatDay);
//        String date = format1.replace("-", "月");
        Calendar cal = Calendar.getInstance();
        Date date = calendarDay(what).getTime();

//        Date date=new Date();//现在的日期
        cal.setTime(date);
        Integer year = cal.get(Calendar.YEAR);//获取年
        Integer month = cal.get(Calendar.MONTH) + 1;//获取月（月份从0开始，如果按照中国的习惯，需要加一）
        Integer day = cal.get(Calendar.DATE);//获取月（月份从0开始，如果按照中国的习惯，需要加一）

        Integer day_moneth = cal.get(Calendar.DAY_OF_MONTH);//获取日（月中的某一天）
        Integer day_week = cal.get(Calendar.DAY_OF_WEEK);//获取一周内的某一天


        return month + "月" + day + "日";
    }

    public static String formatDayNumber(int what) {

        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(ONLY_DATE_PATTERN);
        String format1 = dateFormat2.format(whatDay);
        return format1;
    }
    public static String formatWhatDay(int what) {

        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(DATE_PATTERN);
        String format1 = dateFormat2.format(whatDay);
        return format1;
    }

    public static String afterWhatDay(String specifiedDay,int what) {
        Calendar c = Calendar.getInstance();
        Date date=null;
        try {
            date = new SimpleDateFormat("yy-MM-dd").parse(specifiedDay);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        c.setTime(date);
        int day=c.get(Calendar.DATE);
        c.set(Calendar.DATE, day + what);

        String dayAfter = new SimpleDateFormat("yyyy-MM-dd").format(c.getTime());
        return dayAfter;
//
//        Date whatDay = calendarDay(what).getTime();
//        SimpleDateFormat dateFormat2 = new SimpleDateFormat(DATE_PATTERN);
//        String format1 = dateFormat2.format(whatDay);
//        return format1;
    }


    public static String formatWhatDayMinute(int what) {
        Date whatDay = calendarDay(0).getTime();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("HH");
        SimpleDateFormat dateFormat2 = new SimpleDateFormat("mm");
        String hour = dateFormat1.format(whatDay);
        String minute = dateFormat2.format(whatDay);
        BigDecimal hourMinute = new BigDecimal(hour).multiply(new BigDecimal(60));
        BigDecimal totalMinute = hourMinute.add(new BigDecimal(minute));
        System.out.println("totoamii------------" + totalMinute);
        BigDecimal printMinute = totalMinute.add(new BigDecimal(what));
        System.out.println("printMinuteprintMinuteprintMinute" + printMinute);

        return printMinute.toString();
    }

    public static int getNowMinute() {

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        System.out.println("xianzhaihourr===" + hour);
        hour = hour * 60;
        int minute = calendar.get(Calendar.MINUTE) ;
        int total = hour + minute;
        return total;
    }
    public static String formatWhatOnlyDay(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(ONLY_DATE_PATTERN);
        String format1 = dateFormat2.format(whatDay);
        return format1;
    }
    public static String formatWhatOnlyHao(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(ONLY_HAO_PATTERN);
        String format1 = dateFormat2.format(whatDay);
        return format1;
    }

    public static String formatWhatDayTime(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(DATE_TIME_PATTERN);
        String format1 = dateFormat2.format(whatDay);
        return format1;
    }

    public static String formatWhatMonth(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(MONTH_TIME_PATTERN);
        String format1 = dateFormat2.format(whatDay);
        return format1;
    }
    public static String formatWhatMonthString(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(MONTH_TIME_PATTERN);
        String format1 = dateFormat2.format(whatDay);
        String hanzi  = "";
        if(format1.equals("01")){
            hanzi  = "一";
        }
        if(format1.equals("02")){
            hanzi  = "二";
        }  if(format1.equals("03")){
            hanzi  = "三";
        }  if(format1.equals("04")){
            hanzi  = "四";
        }  if(format1.equals("05")){
            hanzi  = "五";
        }  if(format1.equals("06")){
            hanzi  = "六";
        }  if(format1.equals("07")){
            hanzi  = "七";
        }  if(format1.equals("08")){
            hanzi  = "八";
        }  if(format1.equals("09")){
            hanzi  = "九";
        }  if(format1.equals("10")){
            hanzi  = "十";
        }  if(format1.equals("11")){
            hanzi  = "十一";
        }  if(format1.equals("12")){
            hanzi  = "十二";
        }
        return hanzi + "月";
    }



    public static String getNextMonth() {
        Date whatDay = calendarDay(0).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(MONTH_TIME_PATTERN);
        String format1 = dateFormat2.format(whatDay);
        if (format1.equals("12")) {
            format1 = "01";
        } else {
            BigDecimal bigDecimal = new BigDecimal(format1);
            BigDecimal aaa = bigDecimal.add(new BigDecimal("1"));

            if (aaa.compareTo(new BigDecimal("10")) == -1) {
                format1 = "0" + aaa.toString();
            } else {
                format1 = aaa.toString();
            }
        }
        return format1;
    }

    public static String getLastMonth() {
        Date whatDay = calendarDay(0).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(MONTH_TIME_PATTERN);
        String format1 = dateFormat2.format(whatDay);
        if (format1.equals("01")) {
            format1 = "12";
        } else {
            BigDecimal bigDecimal = new BigDecimal(format1);
            BigDecimal aaa = bigDecimal.subtract(new BigDecimal("1"));

            if (aaa.compareTo(new BigDecimal("10")) == -1) {
                format1 = "0" + aaa.toString();
            } else {
                format1 = aaa.toString();
            }
        }
        return format1;
    }

    //todo
    public static String getLastMonthFirstDay() {
        Calendar c = Calendar.getInstance();
        //当前日期设置为指定日期
        c.setTime(new Date());
        //指定日期月份减去一
        c.add(Calendar.MONTH, -1);
        //指定日期月份减去一后的 最大天数
        c.set(Calendar.DATE, c.getActualMinimum(Calendar.DATE));
        //获取上给月最后一天的日期
        Date lastDateOfPrevMonth = c.getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(DATE_PATTERN);
        String format11 = dateFormat2.format(lastDateOfPrevMonth);
        return format11;
    }

    //todo
    public static String getLastMonthLastDay() {
        Calendar c = Calendar.getInstance();
        //当前日期设置为指定日期
        c.setTime(new Date());
        //指定日期月份减去一
        c.add(Calendar.MONTH, -1);
        //指定日期月份减去一后的 最大天数
        c.set(Calendar.DATE, c.getActualMaximum(Calendar.DATE));
        //获取上给月最后一天的日期
        Date lastDateOfPrevMonth = c.getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(DATE_PATTERN);
        String format11 = dateFormat2.format(lastDateOfPrevMonth);

        return format11;

    }


    public static int getWeekDate(String date) {
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN);
        int weekOfYear;
        try {
            Date date1 = format.parse(date);
            Calendar calendar = Calendar.getInstance();

            calendar.setFirstDayOfWeek(Calendar.MONDAY);

            calendar.setTime(date1);
            weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR);

        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
        return weekOfYear;
    }

    public static String getLastMonthString() {
        Date whatDay = calendarDay(0).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(MONTH_TIME_PATTERN);
        String format1 = dateFormat2.format(whatDay);
        String hanzi = "";
        if (format1.equals("01")) {
            format1 = "12";
        } else {
            BigDecimal bigDecimal = new BigDecimal(format1);
            BigDecimal aaa = bigDecimal.subtract(new BigDecimal("1"));
            format1 = aaa.toString();
        }
        if(format1.equals("1")){
            hanzi  = "一";
        }
        if(format1.equals("2")){
            hanzi  = "二";
        }  if(format1.equals("3")){
            hanzi  = "三";
        }  if(format1.equals("4")){
            hanzi  = "四";
        }  if(format1.equals("5")){
            hanzi  = "五";
        }  if(format1.equals("6")){
            hanzi  = "六";
        }  if(format1.equals("7")){
            hanzi  = "七";
        }  if(format1.equals("8")){
            hanzi  = "八";
        }  if(format1.equals("9")){
            hanzi  = "九";
        }  if(format1.equals("10")){
            hanzi  = "十";
        }  if(format1.equals("11")){
            hanzi  = "十一";
        }  if(format1.equals("12")){
            hanzi  = "十二";
        }
        return hanzi + "月";
    }


    public static String getLastTwoMonth() {
        Date whatDay = calendarDay(0).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat("MM"); // 假设 MONTH_TIME_PATTERN 为 "MM"
        String format1 = dateFormat2.format(whatDay);
        int month = Integer.parseInt(format1);

        // 减去 2 个月，并处理跨年情况
        month -= 2;
        if (month <= 0) {
            month += 12; // 跨年时月份从上一年补 12 个月
        }

        // 格式化为两位数月份
        String result = month < 10 ? "0" + month : String.valueOf(month);

        System.out.println("最后两个月是：" + result);
        return result;
    }



    public static String formatWhatYear(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(Year_TIME_PATTERN);
        String format1 = dateFormat2.format(whatDay);
        return format1;
    }

    public static String formatWhatDate(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat("MM-dd");
        String format1 = dateFormat2.format(whatDay);
        return format1;
    }

    public static String getWeek(int what) {

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, what);

        Date date = new Date();
        String[] weekDays = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        int w = cal.get(Calendar.DAY_OF_WEEK) - 1;
        if (w < 0)
            w = 0;
        return weekDays[w];

    }

    public static Integer getWeekOfYear(int what) {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        int week = cal.get(Calendar.WEEK_OF_YEAR);
        return week + what;
    }

    public static String getJustHao(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat("dd");
        return dateFormat2.format(whatDay);
    }


    public static String changeDateToWeek(String datetime) {
        String rq = datetime;
        String[] weekDays = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        Date date = null;
        try {
            date = new SimpleDateFormat("yyyy-MM-dd").parse(rq);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int w = cal.get(Calendar.DAY_OF_WEEK) - 1 < 0 ? 0 : cal.get(Calendar.DAY_OF_WEEK) - 1;
        String week= weekDays[w];
        return week;


    }

    /**
     * 获取两个日期相差的月数
     */
    public static int getMonthDiff(String d1, String d2) {
        System.out.println(d1 + d2);
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN);

        try {
            c1.setTime(format.parse(d1));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        try {
            c1.setTime(format.parse(d2));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        int year1 = c1.get(Calendar.YEAR);
        int year2 = c2.get(Calendar.YEAR);
        int month1 = c1.get(Calendar.MONTH);
        int month2 = c2.get(Calendar.MONTH);
        int day1 = c1.get(Calendar.DAY_OF_MONTH);
        int day2 = c2.get(Calendar.DAY_OF_MONTH);
        // 获取年的差值?
        int yearInterval = year1 - year2;
        // 如果 d1的 月-日 小于 d2的 月-日 那么 yearInterval-- 这样就得到了相差的年数
        if (month1 < month2 || month1 == month2 && day1 < day2) {
            yearInterval--;
        }
        // 获取月数差值
        int monthInterval = (month1 + 12) - month2;
        if (day1 < day2) {
            monthInterval--;
        }
        monthInterval %= 12;
        int monthsDiff = Math.abs(yearInterval * 12 + monthInterval);
        return monthsDiff;
    }


    public static String getMonthOfLastDate() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

        Calendar ca = Calendar.getInstance();
//设置最后一天
        ca.set(Calendar.DAY_OF_MONTH, ca.getActualMaximum(Calendar.DAY_OF_MONTH));
//最后一天格式化
        return format.format(ca.getTime());
    }


    public static String formatWhatTime(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat("HH:mm");
        return dateFormat2.format(whatDay);
    }

    public static String formatWhatHour(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat("HH");
        return dateFormat2.format(whatDay);
    }


    public static String formatWhatMinute(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat("mm");
        return dateFormat2.format(whatDay);
    }


    public static String formatWhatYearDayTime(int what) {
        Date whatDay = calendarDay(what).getTime();
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(YEAR_DATE_TIME_PATTERN);
        return dateFormat2.format(whatDay);
    }


    public static Integer getHowManyDaysInPeriod(String stopDate, String startDate) {
        String dateFormat = "yyyy-MM-dd";
        SimpleDateFormat format = new SimpleDateFormat(dateFormat);

        if (stopDate.equals(startDate)) {
            return 0;
        } else {
            try {
                Date start = format.parse(startDate);
                Date stop = format.parse(stopDate);
                return (int) ((stop.getTime() - start.getTime()) / 1000 / 60 / 60 / 24);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }


    public static Integer getTwoDaysEarly(String startDate, String stopDate) {
        String dateFormat = "yyyy-MM-dd";
        SimpleDateFormat format = new SimpleDateFormat(dateFormat);
        System.out.println("startdata" + startDate);
        System.out.println("stopDate" + stopDate);

        if (stopDate.equals(startDate)) {
            return 0;
        } else {
            try {
                long startTime = format.parse(startDate).getTime();
                long stopTime = format.parse(stopDate).getTime();
                if (stopTime - startTime > 0) {
                    return 1;
                } else {
                    return -1;
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    public static int getMondayPlus() {
        Calendar cd = Calendar.getInstance();

        int dayOfWeek = cd.get(7) - 1;

        if (dayOfWeek == 1) {
            return 0;
        }

        return (1 - dayOfWeek);
    }


    public static Map<String, String> getLastWeek() {
        Map<String, String> map = new HashMap<String, String>();
        Calendar cal = Calendar.getInstance();
        int n = cal.get(Calendar.DAY_OF_WEEK) - 1;
        if (n == 0) {
            n = 7;
        }
        cal.add(Calendar.DATE, -(7 + (n - 1)));// 上周一的日期
        SimpleDateFormat dateFormat2 = new SimpleDateFormat(DATE_PATTERN);

        Date monday = cal.getTime();
        map.put("monday", dateFormat2.format(monday));
        int monMonth = cal.get(Calendar.MONTH) + 1;
        String monm = String.valueOf(monMonth) + '月';
        int monHao = cal.get(Calendar.DATE);
        String monh = String.valueOf(monHao) + "日";
        map.put("mondayString", monm + monh);


        cal.add(Calendar.DATE, 1);
        Date tuesday = cal.getTime();
        map.put("tuesday", dateFormat2.format(tuesday));
        int tueMonth = cal.get(Calendar.MONTH) + 1;
        String tuem = String.valueOf(tueMonth) + '月';
        int tueHao = cal.get(Calendar.DATE);
        String tueh = String.valueOf(tueHao) + "日";
        map.put("tuesdayString", tuem + tueh);

        cal.add(Calendar.DATE, 1);
        Date wednesday = cal.getTime();
        map.put("wednesday", dateFormat2.format(wednesday));
        int wedMonth = cal.get(Calendar.MONTH) + 1;
        String wedm = String.valueOf(wedMonth) + '月';
        int wedHao = cal.get(Calendar.DATE);
        String wedh = String.valueOf(wedHao) + "日";
        map.put("wednesdayString", wedm + wedh);

        cal.add(Calendar.DATE, 1);
        Date thursday = cal.getTime();
        map.put("thursday", dateFormat2.format(thursday));
        int thuMonth = cal.get(Calendar.MONTH) + 1;
        String thum = String.valueOf(thuMonth) + '月';
        int thuHao = cal.get(Calendar.DATE);
        String thuh = String.valueOf(thuHao) + "日";
        map.put("thursdayString", thum + thuh);

        cal.add(Calendar.DATE, 1);
        Date friday = cal.getTime();
        map.put("friday", dateFormat2.format(friday));
        int friMonth = cal.get(Calendar.MONTH) + 1;
        String frim = String.valueOf(friMonth) + '月';
        int friHao = cal.get(Calendar.DATE);
        String frih = String.valueOf(friHao) + "日";
        map.put("fridayString", frim + frih);

        cal.add(Calendar.DATE, 1);
        Date saturday = cal.getTime();
        map.put("saturday", dateFormat2.format(saturday));
        int satMonth = cal.get(Calendar.MONTH) + 1;
        String satm = String.valueOf(satMonth) + '月';
        int satHao = cal.get(Calendar.DATE);
        String sath = String.valueOf(satHao) + "日";
        map.put("saturdayString", satm + sath);

        cal.add(Calendar.DATE, 1);
        Date sunday = cal.getTime();
        map.put("sunday", dateFormat2.format(sunday));
        int sunMonth = cal.get(Calendar.MONTH) + 1;
        String sunm = String.valueOf(sunMonth) + '月';
        int sunHao = cal.get(Calendar.DATE);
        String sunh = String.valueOf(sunHao) + "日";
        map.put("sundayString", sunm + sunh);
        return map;
    }


    public static String getThisMonthFirstDay() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        return new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
    }

    public static String getThisMonthLastDay() {
        Calendar cal = Calendar.getInstance();

        System.out.println (new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime()));
        cal.roll(Calendar.DAY_OF_MONTH, 0);
        int i = cal.get(Calendar.DATE);
        return new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime()) ;
    }

    public static String thisWeekMonday() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");//设置日期格式
        Calendar cld = Calendar.getInstance(Locale.CHINA);
        cld.setFirstDayOfWeek(Calendar.MONDAY);//以周一为首日
        cld.setTimeInMillis(System.currentTimeMillis());//当前时间

        cld.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);//周一
        System.out.println(df.format(cld.getTime()));

        return df.format(cld.getTime());
    }
//    public static String thisWeekMondayString() {
//        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");//设置日期格式
//        Calendar cld = Calendar.getInstance(Locale.CHINA);
//        cld.setFirstDayOfWeek(Calendar.MONDAY);//以周一为首日
//        cld.setTimeInMillis(System.currentTimeMillis());//当前时间
//
//        cld.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);//周一
//
//        Date whatDay = calendarDay(0).getTime();
//        SimpleDateFormat dateFormat2 = new SimpleDateFormat(MONTH_TIME_PATTERN);
//        String format = dateFormat2.format(whatDay);
//        SimpleDateFormat hao = new SimpleDateFormat("dd");
//        String format1 = hao.format(cld.getTime());
//        System.out.println(" formatformatformatformat" + format);
//        System.out.println(format1);
//        return format + "月" + format1 + "日";
//    }

    public static String thisWeekMondayString() {
        SimpleDateFormat df = new SimpleDateFormat("M月d日");
        Calendar cld = Calendar.getInstance(Locale.CHINA);
        cld.setFirstDayOfWeek(Calendar.MONDAY);

        // 获取当前日期
        Date today = new Date();
        cld.setTime(today);

        // 计算到本周一的偏移量
        int dayOfWeek = cld.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;

        // 设置为本周一
        cld.add(Calendar.DAY_OF_MONTH, -daysFromMonday);

        System.out.println(df.format(cld.getTime()));
        return df.format(cld.getTime());
    }


    public static String thisWeekSunday() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");//设置日期格式
        Calendar cld = Calendar.getInstance(Locale.CHINA);
        cld.setFirstDayOfWeek(Calendar.MONDAY);//以周一为首日
        cld.setTimeInMillis(System.currentTimeMillis());//当前时间

        cld.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);//周日
        System.out.println(df.format(cld.getTime()));
        return df.format(cld.getTime());
    }

    public static String thisWeekSundayString() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");//设置日期格式
        Calendar cld = Calendar.getInstance(Locale.CHINA);
        cld.setFirstDayOfWeek(Calendar.MONDAY);//以周一为首日
        cld.setTimeInMillis(System.currentTimeMillis());//当前时间

        cld.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);//周日
        SimpleDateFormat month = new SimpleDateFormat("MM");
        String format = month.format(cld.getTime());
        SimpleDateFormat hao = new SimpleDateFormat("dd");
        String format1 = hao.format(cld.getTime());
        System.out.println(format1);

        return format + "月" + format1 + "日";
    }


    public static Integer getPreviousWeekSunday() {
        int weeks = -1;
        int mondayPlus = getMondayPlus();
        GregorianCalendar currentDate = new GregorianCalendar();
        currentDate.add(5, mondayPlus + weeks);
        Date monday = currentDate.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat(YEAR_DATE_TIME_PATTERN);
        String preMonday = sdf.format(monday) + " 00:00";
        String today = formatWhatYearDayTime(0);
        long time = 0;
        try {
            time = sdf.parse(preMonday).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long time1 = 0;
        try {
            time1 = sdf.parse(today).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        if (time1 == time || time1 > time) {
            System.out.println("ok");
            return 1;

        } else {
            System.out.println("no");
            return -1;
        }
    }

    //
    public static String getTimeStamp() {
        StringBuilder sb = new StringBuilder();
        long l = System.currentTimeMillis() / 1000;
        return sb.append(l).toString();
    }

    public static Long changeStringToTime(String dateString) {
        String dateFormat = "yyyy-MM-dd";
        SimpleDateFormat format = new SimpleDateFormat(dateFormat);
        try {
            return format.parse(dateString).getTime() / 1000;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return 0L;
    }
    public static String changeStringToDate(String dateString, int what) {
        String dateFormat = "yyyy-MM-dd";
        String format1 ="";
        SimpleDateFormat format = new SimpleDateFormat(dateFormat);
        try {
            Date parse = format.parse(dateString);
            System.out.println("parseparseparseparseparse" +  parse);
            long time = parse.getTime() +  1000 * 60 * 60 * 24 * what;
            System.out.println("timetime" + time);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            format1 = simpleDateFormat.format(time);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return format1;
    }
    public static String convertDoubleToChineseCurrency(double num) {
        if (num < 0) {
            return "负" + convertDoubleToChineseCurrency(-num);
        }

        if (num > 99999999999999.99) {
            return "金额超出范围";
        }

        long numLong = Math.round(num * 100); // 将金额转换为整数，单位为分
        long integerPart = numLong / 100;     // 整数部分
        int decimalPart = (int) (numLong % 100); // 小数部分

        String integerStr = convertIntegerToChinese(integerPart);
        String decimalStr = convertDecimalToChinese(decimalPart);

        if (integerPart == 0 && decimalPart == 0) {
            return "零元整";
        } else if (decimalPart == 0) {
            return integerStr + "元整";
        } else if (integerPart == 0) {
            return decimalStr;
        } else {
            return integerStr + "元" + decimalStr;
        }
    }

    private static String convertIntegerToChinese(long num) {
        StringBuilder result = new StringBuilder();
        int unitPos = 0; // 大单位位置
        boolean zero = false; // 标记零
        while (num > 0) {
            int section = (int) (num % 10000); // 每四位一组
            if (zero) {
                result.insert(0, numerals[0]);
            }
            String sectionChinese = convertSectionToChinese(section);
            if (section != 0) {
                result.insert(0, sectionChinese + bigUnits[unitPos]);
            }
            zero = section == 0 && result.length() > 0;
            num /= 10000;
            unitPos++;
        }
        return result.toString();
    }

    private static String convertSectionToChinese(int section) {
        StringBuilder sectionChinese = new StringBuilder();
        int unitPos = 0; // 小单位位置
        boolean zero = true; // 标记零
        while (section > 0) {
            int digit = section % 10;
            if (digit == 0) {
                if (!zero) {
                    zero = true;
                    sectionChinese.insert(0, numerals[0]);
                }
            } else {
                zero = false;
                sectionChinese.insert(0, numerals[digit] + units[unitPos]);
            }
            unitPos++;
            section /= 10;
        }
        return sectionChinese.toString();
    }

    private static String convertDecimalToChinese(int decimal) {
        StringBuilder result = new StringBuilder();
        int jiao = decimal / 10;
        int fen = decimal % 10;
        if (jiao == 0 && fen == 0) {
            return "";
        }
        if (jiao != 0) {
            result.append(numerals[jiao]).append(decimals[0]);
        }
        if (fen != 0) {
            result.append(numerals[fen]).append(decimals[1]);
        }
        return result.toString();
    }

    private static Calendar calendarDay(int what) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, what);
        return calendar;
    }

    public static String getTimeDiff(String date) {
        System.out.println(date);
        if (ObjectUtils.isEmpty(date)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        System.out.println("dfdd");
        try {
            SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            Date parse = mDateFormat.parse(date);
            System.out.println(parse);
            Date now = new Date();
            // 这样得到的差值是微秒级别
            long diff = now.getTime() - parse.getTime();
            System.out.println(diff);
            // 只能精确到日 无法具细到年 月 不能确定一个月具体多少天 不能确定一年具体多少天
            // 获取日
            long day = diff / (1000 * 60 * 60 * 24);
            System.out.println(day);
            diff = diff % (1000 * 60 * 60 * 24);
            if (day > 0) {
                System.out.println(day);
                sb.append(day).append("天");
                System.out.println(sb.append(day).append("天"));
            }
            // 获取时
            long hour = diff / (1000 * 60 * 60);
            diff = diff % (1000 * 60 * 60);
            if (hour > 0) {
                sb.append(hour).append("时");
            }
            // 获取分
            long min = diff / (1000 * 60);
            diff = diff % (1000 * 60);
            if (min > 0) {
                sb.append(min).append("分");
            }
            // 获取秒
            long sec = diff / 1000;
            if (sec > 0) {
                sb.append(sec).append("秒");
            }
            System.out.println(sb.toString());
            return sb.toString();
        } catch (ParseException e) {
            return "";
        }
    }


}
