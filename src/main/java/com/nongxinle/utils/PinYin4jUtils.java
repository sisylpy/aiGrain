/**
 * com.nongxinle.utils class
 *
 * @Author: peiyi li
 * @Date: 2019-06-06 22:30
 */

package com.nongxinle.utils;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.*;

/**
 * @author lpy
 * @date 2019-06-06 22:30
 */


public class PinYin4jUtils {
    /**
     * 将字符串转换成拼音数组
     *
     * @param src
     * @return
     */
    public static String[] stringToPinyin(String src) {
        return stringToPinyin(src, false, null);
    }

    /**
     * 将字符串转换成拼音数组
     *
     * @param src
     * @return
     */
    public static String[] stringToPinyin(String src, String separator) {

        return stringToPinyin(src, true, separator);
    }

    /**
     * 将字符串转换成拼音数组
     *
     * @param src
     * @param isPolyphone 是否查出多音字的所有拼音
     * @param separator   多音字拼音之间的分隔符
     * @return
     */
    public static String[] stringToPinyin(String src, boolean isPolyphone,
                                          String separator) {
        // 判断字符串是否为空
        if ("".equals(src) || null == src) {
            return null;
        }
        char[] srcChar = src.toCharArray();
        int srcCount = srcChar.length;
        String[] srcStr = new String[srcCount];

        for (int i = 0; i < srcCount; i++) {
            srcStr[i] = charToPinyin(srcChar[i], isPolyphone, separator);
        }
        return srcStr;
    }

    /**
     * 将单个字符转换成拼音
     *
     * @param src
     * @return
     */
    public static String charToPinyin(char src, boolean isPolyphone,
                                      String separator) {
        // 创建汉语拼音处理类
        HanyuPinyinOutputFormat defaultFormat = new HanyuPinyinOutputFormat();
        // 输出设置，大小写，音标方式
        defaultFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        defaultFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        defaultFormat.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);

        StringBuffer tempPinying = new StringBuffer();

        // 如果是中文
        if (src > 128) {
            try {
                // 转换得出结果
                String[] strs = PinyinHelper.toHanyuPinyinStringArray(src,
                        defaultFormat);

                // 是否查出多音字，默认是查出多音字的第一个字符
                if (isPolyphone && null != separator) {
                    for (int i = 0; i < strs.length; i++) {
                        tempPinying.append(strs[i]);
                        if (strs.length != (i + 1)) {
                            // 多音字之间用特殊符号间隔起来
                            tempPinying.append(separator);
                        }
                    }
                } else {
                    tempPinying.append(strs[0]);
                }

            } catch (BadHanyuPinyinOutputFormatCombination e) {
                e.printStackTrace();
            }
        } else {
            tempPinying.append(src);
        }

        return tempPinying.toString();

    }

    public static String hanziToPinyin(String hanzi) {


        return hanziToPinyin(hanzi, " ");
    }

    /**
     * 将汉字转换成拼音
     *
     * @param hanzi
     * @param separator
     * @return
     */
    public static String  hanziToPinyin(String hanzi, String separator) {

        // 创建汉语拼音处理类
        HanyuPinyinOutputFormat defaultFormat = new HanyuPinyinOutputFormat();
        // 输出设置，大小写，音标方式
        defaultFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        defaultFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        defaultFormat.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);

        String pinyinName = "";
//        Map<String, Object> pinyinMap = new HashMap<>();

        if (hanzi != null && hanzi.length() > 0 && !"null".equals(hanzi)) {
            char[] input = hanzi.trim().toCharArray();
            List<String> words = new ArrayList<>();

            try {
                for (int i = 0; i < input.length; i++) {
                    List<String> tempList = new ArrayList<>();

                    if (Character.toString(input[i]).matches(
                            "[\\u4E00-\\u9FA5]+")) {
                        String[] temp = PinyinHelper.toHanyuPinyinStringArray(
                                input[i], defaultFormat);

                        Map<String, Object> cha = new HashMap<>();
                        cha.put("p",temp[0]);
                        words.add(temp[0]);
                        pinyinName += temp[0];

                    } else {
                        //非汉字不用转拼音
                        pinyinName += Character.toString(input[i]);
                        words.add(pinyinName);
                    }
                }

            } catch (BadHanyuPinyinOutputFormatCombination e) {
                e.printStackTrace();
            }
        }


        return pinyinName;
    }

    /**
     * 将字符串数组转换成字符串
     *
     * @param str
     * @param separator 各个字符串之间的分隔符
     * @return
     */
    public static String stringArrayToString(String[] str, String separator) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < str.length; i++) {
            sb.append(str[i]);
            if (str.length != (i + 1)) {
                sb.append(separator);
            }
        }
        return sb.toString();
    }

    /**
     * 简单的将各个字符数组之间连接起来
     *
     * @param str
     * @return
     */
    public static String stringArrayToString(String[] str) {
        return stringArrayToString(str, "");
    }

    /**
     * 将字符数组转换成字符串
     *
     * @param ch
     * @param separator 各个字符串之间的分隔符
     * @return
     */
    public static String charArrayToString(char[] ch, String separator) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < ch.length; i++) {
            sb.append(ch[i]);
            if (ch.length != (i + 1)) {
                sb.append(separator);
            }
        }
        return sb.toString();
    }

    /**
     * 将字符数组转换成字符串
     *
     * @param ch
     * @return
     */
    public static String charArrayToString(char[] ch) {
        return charArrayToString(ch, " ");
    }

    /**
     * 取汉字的首字母
     *
     * @param src
     * @param isCapital 是否是大写
     * @return
     */
    public static char[] getHeadByChar(char src, boolean isCapital) {
        // 如果不是汉字直接返回
        if (src <= 128) {
            return new char[]{src};
        }
        // 获取所有的拼音
        String[] pinyingStr = PinyinHelper.toHanyuPinyinStringArray(src);
        // 创建返回对象
        int polyphoneSize = pinyingStr.length;
        char[] headChars = new char[polyphoneSize];
        int i = 0;
        // 截取首字符
        for (String s : pinyingStr) {
            char headChar = s.charAt(0);
            // 首字母是否大写，默认是小写
            if (isCapital) {
                headChars[i] = Character.toUpperCase(headChar);
            } else {
                headChars[i] = headChar;
            }
            i++;
        }

        return headChars;
    }

    /**
     * 取汉字的首字母(默认是大写)
     *
     * @param src
     * @return
     */
    public static char[] getHeadByChar(char src) {
        return getHeadByChar(src, true);
    }

    /**
     * 查找字符串首字母
     *
     * @param src
     * @return
     */
    public static String[] getHeadByString(String src) {
        return getHeadByString(src, true);
    }

    /**
     * 查找字符串首字母
     *
     * @param src
     * @param isCapital 是否大写
     * @return
     */
    public static String[] getHeadByString(String src, boolean isCapital) {
        return getHeadByString(src, isCapital, null);
    }

    public static String getEnglishKuohao(String goodsName) {
        String newNode = null;
        String allConvertNode = null;
        if(goodsName.contains("（")&& goodsName.contains("）")){
            newNode = goodsName.replaceAll("（", "(");
            allConvertNode = newNode.replace("）",")");
        }
        else if(!(goodsName.contains("（"))&& goodsName.contains("）")){
            allConvertNode = goodsName.replaceAll("）", ")");
        }
        else if(goodsName.contains("（") && !(goodsName.contains("）"))){
            newNode = goodsName.replaceAll("（", "(");
            allConvertNode = newNode;
        }

        else{
            allConvertNode = goodsName;
        }
        System.out.println(goodsName +"hanzireplaseceeee");
        System.out.println(allConvertNode +"allConvertNodereplaseceeee");
        return allConvertNode;
    }
    public static String getXiegang(String goodsName) {
        String newNode = null;
        String allConvertNode = null;

         if(goodsName.contains("/")){
             System.out.println("contaisxiegang" + goodsName);
            newNode = goodsName.replaceAll("/", "");
            allConvertNode = newNode;
        }else{
             allConvertNode = goodsName;
         }

        return allConvertNode;
    }



    public static String getHeadStringByString(String src, boolean isCapital, String separator) {

        String []  s = getHeadByString(src,isCapital,separator);
        System.out.println("src==" +src);
        StringBuffer stringBuffer = new StringBuffer();
        for (String s4 : s) {
            stringBuffer.append(s4);
        }

        return  stringBuffer.toString();

    }

    /**
     * 查找字符串首字母
     *
     * @param src
     * @param isCapital 是否大写
     * @param separator 分隔符
     * @return
     */
    public static String[] getHeadByString(String src, boolean isCapital,
                                           String separator) {
        char[] chars = src.toCharArray();
        String[] headString = new String[chars.length];
        int i = 0;
        for (char ch : chars) {

            char[] chs = getHeadByChar(ch, isCapital);
            StringBuffer sb = new StringBuffer();
            if (null != separator) {
                int j = 1;

                for (char ch1 : chs) {
                    sb.append(ch1);
                    if (j != chs.length) {
                        sb.append(separator);
                    }
                    j++;
                }
            } else {
                sb.append(chs[0]);
            }
            headString[i] = sb.toString();
            i++;
        }
        return headString;
    }

    /**
     * 11      * 将文字转为汉语拼音
     * 12      * @param chineselanguage 要转成拼音的中文
     * 13
     */
    public static String toHanyuPinyin(String ChineseLanguage) {
        char[] cl_chars = ChineseLanguage.trim().toCharArray();
        String hanyupinyin = "";
        HanyuPinyinOutputFormat defaultFormat = new HanyuPinyinOutputFormat();
        defaultFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        defaultFormat.setToneType(HanyuPinyinToneType.WITH_TONE_MARK);
        defaultFormat.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);
        try {
            for (int i = 0; i < cl_chars.length; i++) {
                if (String.valueOf(cl_chars[i]).matches("[\u4e00-\u9fa5]+")) {// 如果字符是中文,则将中文转为汉语拼音
                    hanyupinyin += PinyinHelper.toHanyuPinyinStringArray(cl_chars[i], defaultFormat)[0];
                } else {// 如果字符不是中文,则不转换
                    hanyupinyin += cl_chars[i];
                }
            }
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            System.out.println("字符不能转成汉语拼音");
            System.out.println(e);
        }
        return hanyupinyin;
    }

    public static void main(String[] args) {
//        // pin4j 简码 和 城市编码
//        String s1 = "中华人民共和国";
//        String[] headArray = getHeadByString(s1); // 获得每个汉字拼音首字母
//        System.out.println(Arrays.toString(headArray));
//
        String s2 = "猪头肉";
        System.out.println(Arrays.toString(stringToPinyin(s2, true, ",")));

        String s = hanziToPinyin(s2, "-");
        System.out.println(s);

        String s1 = "中华人民共和国";
        String headArray = getHeadStringByString(s1,false,null); // 获得每个汉字拼音首字母
        System.out.println(headArray);
//

//        HanyuPinyinOutputFormat outputFormat = new HanyuPinyinOutputFormat();
//        HanyuPinyinToneType withToneMark = HanyuPinyinToneType.WITH_TONE_MARK;
//        outputFormat.setToneType(withToneMark);
//
//        String s3 = toHanyuPinyin(s2);
//        System.out.println(s);
//        String s= hanziToPinyin(s2, ",");

//        System.out.println(map);

//        String abc = "Tom";
//        char[] chars = abc.toCharArray();
//        String  pinyinName = "";
//        for( int i = 0; i < chars.length; i++) {
//            pinyinName += Character.toString(chars[i]);
//            System.out.println(pinyinName);
//        }


    }
}