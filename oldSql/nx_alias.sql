/*
 Navicat Premium Data Transfer

 Source Server         : gpt
 Source Server Type    : MySQL
 Source Server Version : 80027 (8.0.27-0ubuntu0.20.04.1)
 Source Host           : localhost:3306
 Source Schema         : nongxinle

 Target Server Type    : MySQL
 Target Server Version : 80027 (8.0.27-0ubuntu0.20.04.1)
 File Encoding         : 65001

 Date: 11/04/2026 13:58:59
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for nx_alias
-- ----------------------------
DROP TABLE IF EXISTS `nx_alias`;
CREATE TABLE `nx_alias` (
  `nx_alias_id` int NOT NULL AUTO_INCREMENT COMMENT '别名id',
  `nx_alias_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '别名名称',
  `nx_als_goods_id` int DEFAULT NULL COMMENT '别名商品id',
  `nx_als_sort` int DEFAULT NULL COMMENT '别名排序',
  `nx_alias_pinyin` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '别名名称',
  `nx_alias_py` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '别名名称',
  PRIMARY KEY (`nx_alias_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=123 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Records of nx_alias
-- ----------------------------
BEGIN;
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (1, '红苕', 1009, NULL, NULL, NULL);
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (2, '莲菜', 1008, NULL, NULL, NULL);
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (9, '包菜', 100470, NULL, 'baocai', 'bc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (10, '落地球', 100181, NULL, 'luodiqiu', 'ldq');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (11, '蜜豆', 100086, NULL, 'midou', 'md');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (12, '干豆腐', 100126, NULL, 'gandoufu', 'gdf');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (13, '荷兰瓜', 101890, NULL, 'helangua', 'hlg');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (14, '鲜马蹄', 100049, NULL, 'xianmati', 'xmt');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (15, '鸡枞菌', 100119, NULL, 'jizongjun', 'jzj');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (16, '海带扣', 101836, NULL, 'haidaikou', 'hdk');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (17, '黄玉兰', 102106, NULL, 'huangyulan', 'hyl');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (18, '黄玉兰', 101949, NULL, 'huangyulan', 'hyl');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (19, '洗涤灵', 102128, NULL, 'xidiling', 'xdl');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (20, '5号电池', 100457, NULL, '5haodianchi', '5hdc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (21, '丹丹豆瓣酱', 100298, NULL, 'dandandoubanjiang', 'dddbj');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (22, '绿茄子', 100075, NULL, 'lüqiezi', 'lqz');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (27, '叶生菜', 100547, NULL, 'yeshengcai', 'ysc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (29, '水果萝卜', 101874, NULL, 'shuiguoluobu', 'sglb');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (30, '雪梨', 101504, NULL, 'xueli', 'xl');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (31, '干葱头', 102014, NULL, 'gancongtou', 'gct');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (32, '金耳菌', 101995, NULL, 'jinerjun', 'jej');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (33, '条纹萝卜', 100055, NULL, 'tiaowenluobu', 'twlb');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (34, '香干', 102077, NULL, 'xianggan', 'xg');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (35, '大头菜', 101677, NULL, 'datoucai', 'dtc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (36, '青豆', 102274, NULL, 'qingdou', 'qd');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (37, '柴鸡蛋', 101713, NULL, 'chaijidan', 'cjd');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (38, '香椿芽', 101856, NULL, 'xiangchunya', 'xcy');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (39, '鲜春芽', 102020, NULL, 'xianchunya', 'xcy');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (40, '蓬莱松针', 101922, NULL, 'penglaisongzhen', 'plsz');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (41, '意大利香菜', 102326, NULL, 'yidalixiangcai', 'ydlxc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (42, '咸菜疙瘩', 101677, NULL, 'xiancaigeda', 'xcgd');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (45, '小号笑脸袋', 101140, NULL, 'xiaohaoxiaoliandai', 'xhxld');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (46, '中号笑脸袋', 101139, NULL, 'zhonghaoxiaoliandai', 'zhxld');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (47, '大号笑脸袋', 100456, NULL, 'dahaoxiaoliandai', 'dhxld');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (48, '鸡胸肉', 100205, NULL, 'jixiongrou', 'jxr');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (49, '直立生菜', 100651, NULL, 'zhilishengcai', 'zlsc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (50, '大料', 100350, NULL, 'daliao', 'dl');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (51, '折耳根', 100183, NULL, 'zheergen', 'zeg');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (52, '小米椒', 100101, NULL, 'xiaomijiao', 'xmj');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (53, '绿小米椒', 100985, NULL, 'lüxiaomijiao', 'lxmj');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (54, '小花', 100172, NULL, 'xiaohua', 'xh');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (56, '土豆粉条', 102068, NULL, 'tudoufentiao', 'tdft');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (57, '青萝卜', 100043, NULL, 'qingluobu', 'qlb');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (58, '小黄瓜妞', 102029, NULL, 'xiaohuangguaniu', 'xhgn');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (59, '日本小青瓜', 101890, NULL, 'ribenxiaoqinggua', 'rbxqg');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (60, '柿子椒', 102001, NULL, 'shizijiao', 'szj');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (61, '黄么', 102393, NULL, 'huangme', 'hm');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (62, '秧草', 101937, NULL, 'yangcao', 'yc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (63, '草头', 102400, NULL, 'caotou', 'ct');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (64, '三清笋', 102034, NULL, 'sanqingsun', 'sqs');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (65, '黄辣丁', 101393, NULL, 'huanglading', 'hld');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (66, '三叶香', 102167, NULL, 'sanyexiang', 'syx');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (67, '干葱肉', 102015, NULL, 'gancongrou', 'gcr');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (68, '纸贴', 102947, NULL, 'zhitie', 'zt');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (69, '墩布', 103006, NULL, 'dunbu', 'db');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (70, '火锅头', 103037, NULL, 'huoguotou', 'hgt');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (71, '火锅头', 103038, NULL, 'huoguotou', 'hgt');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (72, '九层塔', 101919, NULL, 'jiucengta', 'jct');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (73, '四角豆', 101930, NULL, 'sijiaodou', 'sjd');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (74, '羊肉', 100235, NULL, 'yangrou', 'yr');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (75, '猪肉', 100187, NULL, 'zhurou', 'zr');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (76, '混合花', 103042, NULL, 'hunhehua', 'hhh');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (79, '7号电池', 103065, NULL, '7haodianchi', '7hdc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (80, '鲜香菇', 100109, NULL, 'xianxianggu', 'xxg');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (81, '黄瓜扭', 104240, NULL, 'huangguaniu', 'hgn');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (82, '蒜子', 100096, NULL, 'suanzi', 'sz');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (83, '去皮小干葱', 102015, NULL, 'qupixiaogancong', 'qpxgc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (86, '五彩小汤圆', 100839, NULL, 'wucaixiaotangyuan', 'wcxty');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (87, '纯胡椒面', 103816, NULL, 'chunhujiaomian', 'chjm');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (88, '生姜汁', 103314, NULL, 'shengjiangzhi', 'sjz');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (89, '贵州子弹头', 101557, NULL, 'guizhouzidantou', 'gzzdt');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (90, '一次性台布', 102155, NULL, 'yicixingtaibu', 'ycxtb');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (92, '毛白菜', 100015, NULL, 'maobaicai', 'mbc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (93, '广茄', 102039, NULL, 'guangqie', 'gq');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (94, '紫洋葱', 100093, NULL, 'ziyangcong', 'zyc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (95, '白洋葱', 100095, NULL, 'baiyangcong', 'byc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (96, '生姜', 100094, NULL, 'shengjiang', 'sj');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (97, '土芹菜', 101946, NULL, 'tuqincai', 'tqc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (98, '尖椒', 100099, NULL, 'jianjiao', 'jj');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (99, '红椒', 101025, NULL, 'hongjiao', 'hj');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (100, '木耳', 100359, NULL, 'muer', 'me');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (101, '圣女果', 101857, NULL, 'shengnüguo', 'sng');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (102, '粗芦笋', 101851, NULL, 'culusun', 'cls');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (103, '安琪酵母', 100275, NULL, 'anqijiaomu', 'aqjm');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (104, '鼎丰白醋', 101600, NULL, 'dingfengbaicu', 'dfbc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (105, '干灯笼椒', 101557, NULL, 'gandenglongjiao', 'gdlj');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (106, '葡萄干', 100553, NULL, 'putaogan', 'ptg');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (107, '熟芝麻', 100874, NULL, 'shuzhima', 'szm');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (108, '松花蛋', 100250, NULL, 'songhuadan', 'shd');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (109, '孜然', 100351, NULL, 'ziran', 'zr');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (110, '水芹菜', 101946, NULL, 'shuiqincai', 'sqc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (111, '无核枣', 103551, NULL, 'wuhezao', 'whz');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (112, '西安辣椒面', 101190, NULL, 'xianlajiaomian', 'xaljm');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (113, '白胡椒面', 103119, NULL, 'baihujiaomian', 'bhjm');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (114, '芫荽', 100003, NULL, 'yansui', 'ys');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (115, '小柿子', 101857, NULL, 'xiaoshizi', 'xsz');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (116, '猪腰子', 102809, NULL, 'zhuyaozi', 'zyz');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (117, '藕', 100039, NULL, 'ou', 'o');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (119, '细棒碴', 102259, NULL, 'xibangcha', 'xbc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (120, '大棒碴', 103184, NULL, 'dabangcha', 'dbc');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (121, '五花肉', 100191, NULL, 'wuhuarou', 'whr');
INSERT INTO `nx_alias` (`nx_alias_id`, `nx_alias_name`, `nx_als_goods_id`, `nx_als_sort`, `nx_alias_pinyin`, `nx_alias_py`) VALUES (122, '秋木耳', 101115, NULL, 'qiumuer', 'qme');
COMMIT;

SET FOREIGN_KEY_CHECKS = 1;
