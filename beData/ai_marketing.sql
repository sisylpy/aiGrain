/*
 Navicat Premium Data Transfer

 Source Server         : gpt
 Source Server Type    : MySQL
 Source Server Version : 80027 (8.0.27-0ubuntu0.20.04.1)
 Source Host           : localhost:3306
 Source Schema         : ai_marketing

 Target Server Type    : MySQL
 Target Server Version : 80027 (8.0.27-0ubuntu0.20.04.1)
 File Encoding         : 65001

 Date: 23/04/2026 11:11:38
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for gb_ai_conversation
-- ----------------------------
DROP TABLE IF EXISTS `gb_ai_conversation`;
CREATE TABLE `gb_ai_conversation` (
  `gb_ai_conversation_id` bigint NOT NULL AUTO_INCREMENT,
  `gb_ai_conversation_department_id` bigint NOT NULL,
  `gb_ai_conversation_distributer_id` bigint DEFAULT NULL,
  `gb_ai_conversation_title` varchar(200) DEFAULT NULL COMMENT 'å¯¹è¯æ ‡é¢˜',
  `gb_ai_conversation_status` tinyint DEFAULT '1' COMMENT '1=è¿›è¡Œä¸­ 0=å·²ç»“æŸ',
  `gb_ai_conversation_type` int DEFAULT '0' COMMENT '对话类型: 0=普通聊天, 1=促销活动/销售额, 2=公众号相关',
  `gb_ai_conversation_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `gb_ai_conversation_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `gb_ai_conversation_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`gb_ai_conversation_id`),
  KEY `idx_gb_ai_conv_department` (`gb_ai_conversation_department_id`),
  KEY `idx_gb_ai_conv_distributer` (`gb_ai_conversation_distributer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AIå¯¹è¯ä¼šè¯';

-- ----------------------------
-- Records of gb_ai_conversation
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_ai_coupon_plan
-- ----------------------------
DROP TABLE IF EXISTS `gb_ai_coupon_plan`;
CREATE TABLE `gb_ai_coupon_plan` (
  `gb_ai_coupon_plan_id` bigint NOT NULL AUTO_INCREMENT,
  `gb_ai_coupon_plan_department_id` bigint NOT NULL,
  `gb_ai_coupon_plan_distributer_id` bigint DEFAULT NULL,
  `gb_ai_coupon_plan_conversation_id` bigint DEFAULT NULL COMMENT 'æ¥æºå¯¹è¯ID',
  `gb_ai_coupon_plan_plan_name` varchar(200) NOT NULL COMMENT 'æ–¹æ¡ˆåç§°',
  `gb_ai_coupon_plan_strategy_tag` varchar(50) DEFAULT NULL COMMENT 'ç­–ç•¥æ ‡ç­¾',
  `gb_ai_coupon_plan_description` text COMMENT 'æ–¹æ¡ˆè¯´æ˜Ž',
  `gb_ai_coupon_plan_coupon_config` json DEFAULT NULL COMMENT 'ä¼˜æƒ åˆ¸é…ç½®',
  `gb_ai_coupon_plan_risk_warning` text COMMENT 'é£Žé™©æç¤º',
  `gb_ai_coupon_plan_status` tinyint DEFAULT '0' COMMENT '0=è‰ç¨¿ 1=å·²å‘å¸ƒ 2=å·²è¿‡æœŸ',
  `gb_ai_coupon_plan_publish_time` datetime DEFAULT NULL COMMENT 'å‘å¸ƒæ—¶é—´',
  `gb_ai_coupon_plan_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `gb_ai_coupon_plan_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`gb_ai_coupon_plan_id`),
  KEY `idx_gb_ai_cp_department` (`gb_ai_coupon_plan_department_id`),
  KEY `idx_gb_ai_cp_distributer` (`gb_ai_coupon_plan_distributer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AIç”Ÿæˆçš„ä¼˜æƒ åˆ¸æ–¹æ¡ˆ';

-- ----------------------------
-- Records of gb_ai_coupon_plan
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_ai_daily_revenue
-- ----------------------------
DROP TABLE IF EXISTS `gb_ai_daily_revenue`;
CREATE TABLE `gb_ai_daily_revenue` (
  `gb_ai_daily_revenue_id` bigint NOT NULL AUTO_INCREMENT,
  `gb_ai_daily_revenue_department_id` bigint NOT NULL,
  `gb_ai_daily_revenue_distributer_id` bigint DEFAULT NULL,
  `gb_ai_daily_revenue_record_date` date NOT NULL COMMENT 'è®°å½•æ—¥æœŸ',
  `gb_ai_daily_revenue_dine_in_revenue` decimal(12,2) DEFAULT '0.00' COMMENT 'å ‚é£Ÿè¥ä¸šé¢',
  `gb_ai_daily_revenue_dine_in_orders` int DEFAULT '0' COMMENT 'å ‚é£Ÿè®¢å•æ•°',
  `gb_ai_daily_revenue_dine_in_customers` int DEFAULT '0' COMMENT 'å ‚é£Ÿé¡¾å®¢æ•°',
  `gb_ai_daily_revenue_takeout_revenue` decimal(12,2) DEFAULT '0.00' COMMENT 'å¤–å–è¥ä¸šé¢',
  `gb_ai_daily_revenue_takeout_orders` int DEFAULT '0' COMMENT 'å¤–å–è®¢å•æ•°',
  `gb_ai_daily_revenue_platform_fee` decimal(10,2) DEFAULT '0.00' COMMENT 'å¹³å°æŠ½æˆ',
  `gb_ai_daily_revenue_weekday` tinyint DEFAULT NULL COMMENT 'æ˜ŸæœŸå‡ ',
  `gb_ai_daily_revenue_holiday` varchar(50) DEFAULT NULL COMMENT 'èŠ‚å‡æ—¥åç§°',
  `gb_ai_daily_revenue_gross_revenue` decimal(12,2) GENERATED ALWAYS AS ((`gb_ai_daily_revenue_dine_in_revenue` + `gb_ai_daily_revenue_takeout_revenue`)) STORED COMMENT 'æ€»è¥ä¸šé¢',
  `gb_ai_daily_revenue_net_revenue` decimal(12,2) GENERATED ALWAYS AS (((`gb_ai_daily_revenue_dine_in_revenue` + `gb_ai_daily_revenue_takeout_revenue`) - `gb_ai_daily_revenue_platform_fee`)) STORED COMMENT 'å‡€æ”¶å…¥',
  `gb_ai_daily_revenue_notes` varchar(500) DEFAULT NULL COMMENT 'å¤‡æ³¨',
  `gb_ai_daily_revenue_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `gb_ai_daily_revenue_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`gb_ai_daily_revenue_id`),
  UNIQUE KEY `uk_gb_ai_dr_dep_date` (`gb_ai_daily_revenue_department_id`,`gb_ai_daily_revenue_record_date`),
  KEY `idx_record_date` (`gb_ai_daily_revenue_record_date`),
  KEY `idx_gb_ai_dr_date` (`gb_ai_daily_revenue_record_date`),
  KEY `idx_gb_ai_dr_department` (`gb_ai_daily_revenue_department_id`),
  KEY `idx_gb_ai_dr_distributer` (`gb_ai_daily_revenue_distributer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ—¥è¥ä¸šé¢è¡¨';

-- ----------------------------
-- Records of gb_ai_daily_revenue
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_ai_knowledge
-- ----------------------------
DROP TABLE IF EXISTS `gb_ai_knowledge`;
CREATE TABLE `gb_ai_knowledge` (
  `gb_ai_knowledge_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gb_ai_knowledge_uuid` varchar(64) DEFAULT NULL COMMENT 'UUID唯一标识',
  `gb_ai_knowledge_type` int DEFAULT '0' COMMENT '对话类型: 0=通用, 1=促销, 2=公众号',
  `gb_ai_knowledge_category` varchar(50) DEFAULT NULL COMMENT '知识大类: coupon/menu/decorate/staff...',
  `gb_ai_knowledge_title` varchar(255) NOT NULL COMMENT '知识标题',
  `gb_ai_knowledge_summary` varchar(500) DEFAULT NULL COMMENT '摘要（1-3句话）',
  `gb_ai_knowledge_content` text NOT NULL COMMENT '详细内容（支持富文本/Markdown）',
  `gb_ai_knowledge_source_url` varchar(500) DEFAULT NULL COMMENT '原文来源URL',
  `gb_ai_knowledge_tags` varchar(500) DEFAULT NULL COMMENT '标签列表: 母亲节,满减,节日营销,低成本',
  `gb_ai_knowledge_author` varchar(100) DEFAULT NULL COMMENT '专家/作者名称',
  `gb_ai_knowledge_origin` varchar(200) DEFAULT NULL COMMENT '来源: 钱多多/刘一刀/王装修...',
  `gb_ai_knowledge_effect_rating` int DEFAULT '0' COMMENT '效果评分 1-5',
  `gb_ai_knowledge_effect_cases` int DEFAULT '0' COMMENT '应用案例数',
  `gb_ai_knowledge_effect_note` varchar(500) DEFAULT NULL COMMENT '效果说明',
  `gb_ai_knowledge_suitable_restaurant` varchar(500) DEFAULT NULL COMMENT '适用餐厅类型',
  `gb_ai_knowledge_suitable_budget` varchar(100) DEFAULT NULL COMMENT '适用预算范围',
  `gb_ai_knowledge_suitable_season` varchar(100) DEFAULT NULL COMMENT '适用季节',
  `gb_ai_knowledge_view_count` int DEFAULT '0' COMMENT '查看次数',
  `gb_ai_knowledge_use_count` int DEFAULT '0' COMMENT '被推荐/使用次数',
  `gb_ai_knowledge_status` int DEFAULT '1' COMMENT '状态: 0=草稿, 1=启用, 2=下架',
  `gb_ai_knowledge_publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `gb_ai_knowledge_create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `gb_ai_knowledge_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`gb_ai_knowledge_id`),
  UNIQUE KEY `gb_ai_knowledge_uuid` (`gb_ai_knowledge_uuid`),
  KEY `idx_category` (`gb_ai_knowledge_category`),
  KEY `idx_type` (`gb_ai_knowledge_type`),
  KEY `idx_tags` (`gb_ai_knowledge_tags`),
  KEY `idx_status` (`gb_ai_knowledge_status`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识库主表';

-- ----------------------------
-- Records of gb_ai_knowledge
-- ----------------------------
BEGIN;
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (1, 'a5e6cd1c-35a2-11f1-b140-525400a8d614', 1, 'coupon', '满减券', '设置满100减20，适合提升客单价，顾客为了达到满减门槛会多点菜。', '【什么是满减券】\n\n满减券是最常见的优惠券形式，核心逻辑是\"花得越多，省得越多\"。\n\n【设置技巧】\n\n1. 满减门槛 = 客单价 × 1.2~1.5\n   - 客单价80元的餐厅，设置100-120元门槛\n   - 门槛太高，顾客达不到；门槛太低，利润被压缩\n\n2. 减多少合适\n   - 满100减20（8折）= 利润空间约60%\n   - 满100减30（7折）= 利润空间约50%，慎用\n   - 新店引流可用更大幅度\n\n3. 设置上限\n   - 最高减50或100，避免极端大单\n\n【适用场景】\n- 提升客单价\n- 吸引中等消费群体\n- 日常工作日促销', NULL, '满减,提升客单,日常', '钱多多', '钱多多', 5, 156, '提升客单价效果明显，平均提升15-25%', '中高端餐厅', '中预算', '全年适用', 1, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:57:04');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (2, 'a5e6d1c4-35a2-11f1-b140-525400a8d614', 1, 'coupon', '折扣券', '8折优惠，适合清理库存，带动滞销菜品销售。', '【什么是折扣券】\n\n直接打折，按原价的一定比例收费。\n\n【设置技巧】\n\n1. 折扣力度\n   - 8折 = 收80%，让利20%\n   - 7折 = 收70%，让利30%，利润较薄\n   - 5折 = 收50%，基本不赚钱，引流专用\n\n2. 限品类 vs 全场\n   - 限品类：指定滞销菜/库存菜打5折\n   - 全场：新品推广期，全场8折\n\n3. 限时折扣\n   - 每天下午2-5点，客流低谷期打折\n   - 晚市结束前1小时，部分菜打折\n\n【适用场景】\n- 清理库存\n- 带动滞销菜\n- 客流低谷期填充', NULL, '折扣,清库存,滞销品', '钱多多', '钱多多', 4, 89, '清理库存效果显著，但要注意毛利控制', '所有类型', '低预算', '淡季/库存积压时', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (3, 'a5e6d44f-35a2-11f1-b140-525400a8d614', 1, 'coupon', '赠品券', '消费送小菜/饮品，提升顾客满意度，增加惊喜感。', '【什么是赠品券】\n\n消费满一定金额，赠送指定商品。\n\n【设置技巧】\n\n1. 赠品选择\n   - 低成本高感知：小菜、饮品、甜品\n   - 成本控制在客单价的3-5%\n   - 例：客单价80元，赠品成本2-4元\n\n2. 包装形式\n   - 实物赠品券：下次使用\n   - 当场赠送：现场领用\n\n3. 菜品关联\n   - 点了主菜，送配菜\n   - 点了米饭，送例汤\n\n【适用场景】\n- 提升满意度\n- 增加复购\n- 引导新客尝试新品', NULL, '赠品,满意度,复购', '钱多多', '钱多多', 4, 78, '顾客反馈良好，但赠品选择很重要', '所有类型', '低预算', '全年适用', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (4, 'a5e6d870-35a2-11f1-b140-525400a8d614', 1, 'coupon', '节日限定券', '节日专属优惠券，情感营销，让顾客感受到专属福利。', '【什么是节日限定券】\n\n只在特定节日发行的专属优惠券。\n\n【设置技巧】\n\n1. 节日分类\n   - 传统节日：春节、中秋、端午\n   - 法定节日：五一、十一、元旦\n   - 情感节日：情人节、母亲节、七夕\n   - 餐饮节日：火锅节、烧烤节\n\n2. 设计要点\n   - 券面设计要有节日氛围\n   - 名称要应景：如\"母亲节感恩券\"\n   - 有效期要合理（节日前3天-节日当天）\n\n3. 额度设置\n   - 比普通券力度稍大（体现专属）\n   - 例：普通8折，节日75折\n\n【适用场景】\n- 节日营销\n- 情感营销\n- 提升节日客流', NULL, '节日,情感,母亲节,父亲节', '钱多多', '钱多多', 5, 134, '节日营销必备，配合情感文案效果翻倍', '所有类型', '中预算', '节日前后', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (5, 'a5e6db29-35a2-11f1-b140-525400a8d614', 1, 'coupon', '裂变券', '分享得券，老客带新客，适合拉新获客。', '【什么是裂变券】\n\n老顾客分享给新顾客，双方都能获得优惠。\n\n【设置技巧】\n\n1. 双向奖励\n   - 老客：分享成功，双方得20元券\n   - 新客：被分享，立减20\n\n2. 分享门槛\n   - 分享到微信好友/朋友圈\n   - 新客需到店消费才能激活\n\n3. 数量限制\n   - 老客最多得3张裂变券\n   - 避免过度让利\n\n【适用场景】\n- 拉新获客\n- 激活老客\n- 社交传播', NULL, '裂变,拉新,社交', '钱多多', '钱多多', 4, 67, '拉新效果不错，但需要配合好的传播素材', '连锁店/有粉丝基础的店', '中预算', '全年适用', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (6, 'a5e6dd24-35a2-11f1-b140-525400a8d614', 1, 'coupon', '代金券', '直接抵现金，如50元代金券卖40元，刺激充值。', '【什么是代金券】\n\n面值固定，购买时打折，到店抵现金。\n\n【设置技巧】\n\n1. 面值与售价\n   - 50元代金券卖40元（8折）\n   - 100元代金券卖75元（75折）\n   - 面值越大，折扣力度可越大\n\n2. 使用规则\n   - 不找零：避免顾客产生\"亏了\"感\n   - 限用1张/人/次：避免叠加\n   - 限具体日期：引导非高峰消费\n\n3. 销售通路\n   - 店内收银台\n   - 外卖平台\n   - 抖音/美团直播', NULL, '代金券,充值,现金流', '钱多多', '钱多多', 4, 92, '快速回笼现金，但要控制发行量', '所有类型', '无需预算（提前收款）', '全年适用', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (7, 'a5e6de7d-35a2-11f1-b140-525400a8d614', 1, 'coupon', '套餐券', '打包多个菜品，优惠出售，适合推新品或推套餐。', '【什么是套餐券】\n\n将多个菜品组合，按优惠价销售。\n\n【设置技巧】\n\n1. 套餐设计\n   - 引流套餐：1-2道爆品 + 1道利润菜\n   - 家庭套餐：2-4人份，涵盖主食+菜+饮品\n   - 商务套餐：快捷+体面\n\n2. 定价逻辑\n   - 套餐总价 = 各菜品之和 × 0.8~0.85\n   - 让顾客感觉\"划算\"但不失毛利\n\n3. 限时限购\n   - 新套餐上线，前100份半价\n   - 每人限购2张\n\n【适用场景】\n- 推广新品\n- 提升人均\n- 家庭聚餐', NULL, '套餐,组合,新品', '钱多多', '钱多多', 4, 103, '提升客单价利器，套餐设计是关键', '所有类型', '中预算', '全年适用', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (8, 'a5e6df84-35a2-11f1-b140-525400a8d614', 1, 'coupon', '会员专享券', '会员专属优惠，培养用户粘性，提升复购率。', '【什么是会员专享券】\n\n只有会员才能领取和使用的优惠券。\n\n【设置技巧】\n\n1. 会员体系\n   - 注册会员：得首单券\n   - 消费积分：100积分换10元券\n   - 升级会员：更高折扣\n\n2. 专享力度\n   - 比非会员多5-10%优惠\n   - 专享日：如周三会员日\n\n3. 发放频率\n   - 新客：首单礼\n   - 老客：月度券/生日券\n   - 流失客：召回券', NULL, '会员,复购,粘性', '钱多多', '钱多多', 5, 145, '会员体系核心工具，忠实顾客复购率提升30%+', '有会员基础的店', '中预算', '全年适用', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (9, 'a5e6e08e-35a2-11f1-b140-525400a8d614', 1, 'coupon', '新客专享券', '首次到店顾客专属优惠，降低首次消费门槛。', '【什么是新客专享券】\n\n专门针对从未消费过的新顾客。\n\n【设置技巧】\n\n1. 额度设置\n   - 力度要大：首次消费5折/满100减50\n   - 但要限制使用条件\n\n2. 防刷机制\n   - 需手机号验证\n   - 需到店扫码核销\n   - 限首次\n\n3. 发放渠道\n   - 抖音/美团新客专享\n   - 线下地推扫码\n   - 老客推荐', NULL, '新客,首单,拉新', '钱多多', '钱多多', 4, 88, '拉新必备，但要与老客优惠区分', '新开业/想扩大客源的店', '中预算', '全年适用', 1, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 21:06:01');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (10, 'a5e6e185-35a2-11f1-b140-525400a8d614', 1, 'coupon', '满额赠券', '消费满一定金额，赠送下次可用的优惠券。', '【什么是满额赠券】\n\n本次消费达标，赠送下次可用的券。\n\n【设置技巧】\n\n1. 门槛设置\n   - 满200送50元下次券\n   - 门槛=客单价×2~3\n\n2. 赠送券额度\n   - 赠券价值 = 消费金额的20-25%\n   - 50元消费，送10元下次券\n\n3. 有效期\n   - 15-30天，足够复访\n   - 有效期太短，顾客压力大\n\n【适用场景】\n- 提升复购\n- 锁定回头客\n- 培养消费习惯', NULL, '满额赠,复购,锁客', '钱多多', '钱多多', 4, 76, '锁客效果好，但要和会员券区分', '所有类型', '低预算', '全年适用', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (11, 'a5e6e27e-35a2-11f1-b140-525400a8d614', 1, 'coupon', '限时秒杀券', '限量抢购，制造紧迫感，适合激活沉睡顾客。', '【什么是限时秒杀券】\n\n在限定时间内限量抢购的优惠券。\n\n【设置技巧】\n\n1. 抢购时间\n   - 抖音直播：晚上8点高峰期\n   - 社群：午休前、下班前\n\n2. 数量控制\n   - 每次100-200张\n   - 营造\"抢\"的感觉\n\n3. 优惠力度\n   - 必须够大：1元秒杀、5折券\n   - 引流为主，不求盈利\n\n【适用场景】\n- 直播引流\n- 社群激活\n- 新店开业', NULL, '秒杀,限量,紧迫感', '钱多多', '钱多多', 4, 59, '引流效果强，但要注意成本控制', '有直播/社群基础的店', '低预算（引流为主）', '特定活动期', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (12, 'a5e6e3b4-35a2-11f1-b140-525400a8d614', 1, 'coupon', '积分兑换券', '消费积累积分，积分兑换优惠券或礼品。', '【什么是积分兑换券】\n\n消费换积分，积分换优惠。\n\n【设置技巧】\n\n1. 积分规则\n   - 1元=1积分\n   - 100积分=1元（无感）\n   - 1000积分=10元（有价值）\n\n2. 兑换选项\n   - 积分+钱：1000积分+20元=换30元券\n   - 纯积分：5000积分换50元券\n\n3. 积分清零\n   - 每年清零一次\n   - 刺激兑换\n\n【适用场景】\n- 提升消费频次\n- 增加粘性\n- 老客维护', NULL, '积分,粘性,老客', '钱多多', '钱多多', 3, 45, '效果较慢，是长期工程', '有会员体系的店', '低预算', '全年适用', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (13, 'a5e6e4cd-35a2-11f1-b140-525400a8d614', 1, 'coupon', '生日专享券', '顾客生日当月/当天专属优惠，情感营销利器。', '【什么是生日专享券】\n\n顾客生日时发放的专属优惠券。\n\n【设置技巧】\n\n1. 发放时间\n   - 生日当月发券\n   - 生日当天最佳（可推送提醒）\n\n2. 优惠力度\n   - 生日专属：8折/满100减30\n   - 附赠：长寿面/蛋糕/甜品\n\n3. 使用条件\n   - 到店出示证明\n   - 需提前预约\n   - 限本人使用\n\n【适用场景】\n- 情感营销\n- 提升口碑\n- 吸引家庭聚餐', NULL, '生日,情感,家庭', '钱多多', '钱多多', 5, 112, '情感价值极高，口碑传播效果好', '所有类型', '中预算', '全年适用', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (14, 'a5e6e5cc-35a2-11f1-b140-525400a8d614', 1, 'coupon', '充值赠券', '充值会员卡，赠送金额或优惠券，快速回笼资金。', '【什么是充值赠券】\n\n顾客充值到会员卡，赠送一定金额或券。\n\n【设置技巧】\n\n1. 充值档次\n   - 充值500送50（9折）\n   - 充值1000送150（85折）\n   - 充值2000送400（8折）\n\n2. 赠送形式\n   - 直接送余额：最实用\n   - 送券包：多张券组合\n\n3. 风控措施\n   - 设置充值上限\n   - 退款政策要明确\n\n【适用场景】\n- 快速回笼资金\n- 锁定顾客\n- 提升客单价', NULL, '充值,现金流,锁客', '钱多多', '钱多多', 5, 134, '现金流利器，但要让顾客觉得值', '所有类型', '无需预算（提前收款）', '全年适用', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (15, 'a5e6e6c9-35a2-11f1-b140-525400a8d614', 1, 'coupon', '节日氛围券', '配合节日主题设计的特色优惠券，如春节红包券、中秋月饼券。', '【什么是节日氛围券】\n\n结合节日文化元素的特色优惠券。\n\n【设置技巧】\n\n1. 券面设计\n   - 红包造型：新年/中秋\n   - 心形：情人节\n   - 康乃馨：母亲节\n\n2. 玩法设计\n   - 拆红包：随机金额\n   - 集五福：集齐换大礼\n   - 节日许愿：满足愿望\n\n3. 传播设计\n   - 适合朋友圈分享\n   - 引导顾客打卡拍照\n\n【适用场景】\n- 节日营销\n- 社交传播\n- 制造话题', NULL, '节日,氛围,社交,话题', '钱多多', '钱多多', 4, 67, '社交传播效果好，适合制造话题', '有社交传播需求的店', '中预算', '节日前后', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (16, 'a5e6e7b8-35a2-11f1-b140-525400a8d614', 1, 'coupon', '任务完成券', '完成指定任务后获得的优惠券，如打卡、评价、分享。', '【什么是任务完成券】\n\n完成特定任务才能获得的优惠券。\n\n【设置技巧】\n\n1. 任务类型\n   - 打卡任务：连续3天消费送30元券\n   - 评价任务：完成5星好评送10元券\n   - 分享任务：分享3次送20元券\n   - 攒卡任务：集齐6张不同节气卡换50元券\n\n2. 任务设计\n   - 任务要有意义，不要强人所难\n   - 奖励要大于付出\n\n3. 进度展示\n   - 小程序/公众号展示任务进度\n   - 适时提醒顾客还有任务没完成\n\n【适用场景】\n- 提升复购\n- 增加互动\n- 收集评价', NULL, '任务,打卡,评价,互动', '钱多多', '钱多多', 4, 56, '互动效果好，但执行成本较高', '有小程序/公众号的店', '中预算', '全年适用', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (17, 'a5e6e8b5-35a2-11f1-b140-525400a8d614', 1, 'coupon', '组合优惠券', '多种优惠叠加使用，如满减+折扣+赠品组合。', '【什么是组合优惠券】\n\n将多种优惠形式打包成一个券包。\n\n【设置技巧】\n\n1. 组合设计\n   - 入门券：50元代金券（卖40元）\n   - 进阶券：100元代金券+8折券+赠品券\n   - 豪华券：全单8折+赠品+免排队\n\n2. 销售策略\n   - 打包销售，折扣更大\n   - 限时销售，制造紧迫感\n\n3. 使用规则\n   - 券包内多张券\n   - 各自有使用条件\n   - 设置过期时间\n\n【适用场景】\n- 大促活动\n- 会员福利\n- 节日营销', NULL, '组合,套餐,福利', '钱多多', '钱多多', 4, 78, '感知价值高，销售转化好', '所有类型', '中预算', '大促/节日', 0, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-11 20:33:30');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (18, 'a5e6e9b0-35a2-11f1-b140-525400a8d614', 1, 'coupon', '定向发放券', '针对特定顾客群体精准发放的优惠券。', '【什么是定向发放券】\n\n只发给特定顾客的专属券。\n\n【设置技巧】\n\n1. 人群定向\n   - 流失顾客：30天未到店\n   - 高价值顾客：月消费前100名\n   - 沉默顾客：只来过1次\n   - 特殊顾客：军人/教师/医护\n\n2. 发放方式\n   - 短信/微信推送\n   - 店员手动发放\n   - 到店扫码\n\n3. 额度设计\n   - 流失召回：力度要大，如5折\n   - VIP维护：体现尊贵，如专属8折\n   - 职业优惠：8.8折致敬\n\n【适用场景】\n- 精准营销\n- 流失召回\n- VIP维护', NULL, '定向,精准,VIP,召回', '钱多多', '钱多多', 5, 92, '精准投放，ROI最高，但需要数据支持', '有顾客数据基础的店', '中预算', '全年适用', 2, 0, 1, NULL, '2026-04-11 20:33:30', '2026-04-18 20:29:37');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (22, 'd9b3374b-16fa-424a-bbe5-b90fb56ebd09', 1, 'coupon', '内功为本，工具为器，坚持为道', '本文是一期餐饮营销主题节目的总结讲义，核心观点有三：其一，营销本质是双刃剑，能放大好的经营也能暴露短板，扎实的产品品质与优质消费体验是开展营销的前提；其二，再好的营销工具也需要\"内功\"支撑，没有口味和体验基础，活动效果只会事倍功半；其三，当前餐饮竞争激烈，内功好的老板也需与时俱进学习数字化营销工具，坚持做、不断迭代，假以时日必有收效。', '那么今天这一次节目我们去做一些总结\n我觉得我们需要有一个类似武功新法的这个手段\n那现在讲一下为什么我们会把那个金庸老先生的降龙十八掌\n穿插结合起来\n我会觉得他有非常多的共通之处\n因为你想金庸老先生描述的降龙十八掌\n无论给郭靖也好还是给乔峰也好\n讲的都是在对战强敌的时候\n降龙十八掌作为一个决定武功\n他是在不同的场景底下遇到各种各样的对手\n遇到各种各样的情况\n然后使用相应的拳法来应对\n那这个就像极了\n我们餐饮企业在做各种各样的营销活动的时候\n也处于不同的场景\n不同的餐厅的情况\n不同的需求\n我们有针对性的设计\n有效的行资有效的营销活动\n因为我还是想要强调一个基本的概念\n不是所有的营销活动\n都会给我们餐厅带来正向的财务资料\n如果你没有做对\n实际上这样的营销\n一些不合适的营销活动恰恰\n反而倒过来会伤害你的品牌\n伤害你的财务盈利的能力\n营销本质上来说\n它是一把双刃剑\n它能够把你好的东西放大\n它同样它也能把你的坏的东西放大\n最近的西北贾国荣\n西北贾国荣和罗永豪之间的PK\n实际上就很好的诠释了这个话题\n你说它有流量吗\n是非常有流量\n但是这种流量带来的会是什么呢\n我想这个答案\n各位聪明的餐饮老板\n你应该明白\n我的意思\n这因为他们有这样的底层的关联性营销\n又是一把双刃剑\n所以今天我在给大家说总结的时候\n其实想要反复强调一个话题\n也就是说\n就像降龙十八掌\n这个武功发挥他的威力\n他其实是非常需要身后的魅力\n他才能发挥得出来\n你放在不同的人身上\n他打出来的效果就还不一样\n同样的道理\n这些会员营销活动\n即便是设计的再好\n如果你没有\n这个扎实的菜品的口味\n如果你没有过硬的\n客人到店的消费体验\n那么\n即便是灾好的营销活动\n在对路的营销活动\n他达到的效果\n也都事倍而公办的\n所以\n心法总结\n你首先要做好一个基础\n也就是说优秀的消费体验\n扎实的产品工底\n然后一颗诚心诚意\n服务消费者的一颗心\n这些其实是就好比\n练降龙十八掌\n你必须\n要有一个好的内功的工底\n道理是一样\n现在是一个酒香也怕巷子深的年代\n所以你有很好的消费体验\n你有扎实的菜品工底\n你也要学会做营销\n你也要\n很会利用\n像我们E16营会员那样优秀的工具\n来帮你放大\n有时候\n你的消费体验很好\n但是你不会利用工具\n你还停留在一个\n传统的一个方式方法当中\n那么\n那你也会\n可能也会遇到一些\n尴尬的场景\n实际上在我身边也有不少这样的例子\n我们可以观察得到\n正因为这样\n我们需要\n能够面向未来\n我们也会看到餐饮业的竞争\n其实是变得比较的激烈\n因为的\n共求的关系\n因为我们经济的下行等等原因\n所以即便你的内功很好\n我也高度建议你\n你要与时俱进\n你要学习这样的工具\n你要有针对性的去打\n一开始打得不好\n其实也没有多大的关系\n坚持做这件事情就会变得很重要\n就像郭靖练武功\n人家的资质也不好\n对不对\n但坚持做\n他一样可以\n变得很厉害\n这个道理跟我们\n把营销做成一个\n长时间坚持做\n一开始做活动做得很简单\n很粗暴\n有时候效果也不够好\n没关系\n一定要坚持做\n同时\n你要\n你要保持旺盛的学习心\n你要经常的去琢磨\n这样\n这个营销活动假以时日\n一定会能发挥出很好的效果\n当然\n跟谁学武功\n非常非常的重要\n那第二个部分\n其实我也想给我们的\n服务商\n在\n易石软件的授权服务商\n给一些\n非常重要的建议\n就跟我们餐饮\n行业当中的品牌来说\n实际上\n餐饮业的竞争\n在我看来是一个低水平的裂度的竞争\n还不够\n还不算是一个高水平的竞争\n\n', '', '餐饮营销 会员运营 营销方法论 营销双刃剑 内功与工具 消费体验 数字化转型 餐饮经营 课程总结', '', '', 0, 0, '', '', '', '', 4, 0, 1, NULL, '2026-04-18 20:17:11', '2026-04-18 20:34:33');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (23, '73011e65-2441-4428-9b98-4f2c61763560', 0, 'coupon', '餐饮营销体系：开业营销与微信公众号粉丝基建的核心心法', '本文为餐饮营销课程讲座记录，重点讲解两招：①\"御天六龙\"即开业营销，需同时考量老板现金流、团队磨合期体验、折扣设计及后续经营衔接等五六个维度，周年庆可复用但力度减半；②\"潜龙勿用\"即关注有礼，核心是引导客人关注微信公众号，以年费300元换取每月4次零成本批量推送能力，强调\"地基要趁生意好时打\"，关注礼与开卡礼二选一，推荐优先做关注礼。', '研究了很多易经上的事情\n易经的卦其实\n复含了哲学的原因\n它没那么悬\n它实际上它\n我会认为它非常有道理\n中国的一代的哲学\n这些哲学被金庸老先生\n做成了一个\n一个很有\n很炫的叫降龙十八掌\n这样的一个东西\n我们来看看\n事实上来说这些东西都挺有意思\n我们来看看\n我们正好18个营销活动的类型\n很完美的\n卸合了意境背后的精髓\n现在看第一个其实你们很熟悉\n我把它叫十层六龙\n它是开业营销活动\n这个效果我们都其实已经看到了\n在中间的精髓\n是在我们面面俱到的\n考虑了各种因素\n比如老板的现金流的问题\n团队磨合的问题\n在磨合期\n顾客的感受不太好的问题\n我们的打折的问题\n然后后续经营的时候\n为什么客户会\n接受\n就是不是这么大力\n折扣的时候\n我们的副作用的问题\n我们都考虑了\n这样的开业营销活动\n我会认为在至少\n考虑了五六个方面的\n管理问题和诉求的时候\n我们有机的把它拿捏在一起\n它就是一个非常棒的\n一个营销活动\n什么时候我们可能还会用得到\n我会说明年的这个时候\n周年庆典\n只不过我们的力度\n不需要那么大\n我们可以搞一个\n两倍储值\n当餐免单\n不用考虑一倍\n实际上它的背后是一倍储值\n\n当餐免单\n我们后面可以搞两倍\n三倍就没问题\n不要太多\n我把降龙十八长\n分成了启示片\n就是我们在准备阶段\n我们应该做什么事情\n阶段的营销活动\n哪些事情生意比较好\n你是在一个瞬间当中\n你应该要坚持\n做的营销活动是什么\n然后如果一不小心\n假如你接手了一个盘子\n是一个逆境当中\n这个餐厅快不行了\n逆境当中我们应该\n用一些营销活动\n所以比较恰好的一点是\n我们启示的部分有\n六种营销活动\n顺境有 6 章，\n逆境有 6 章\n三个有六十八长\n正好三六一十八章\n第二关注有礼\n关注有礼是一个什么样的状态\n关注什么东西\n实际上\n我们相约的就是我们的\n微信公众话\n虽然现在看的人不太多\n但是\n它是为数\n极少的\n批量的主动触发客人的管道\n要把它当成这样看\n也就是说\n我并不需要写出\n多少本彩的公众号\n写文章\n也不要太当回事\n但是\n它是一个很理想的通知管道\n而且不用花钱\n当然也不是一点钱都不花\n微信公众话\n一年的认证份有300块钱\n微信收的\n那300块钱给一个通道\n让我可以推消息给客人\n这个部分\n你可以怎么理解\n如果我们的\n大量的客人都关注了\n我们的微信公众话\n这就意味著我一个月可以骚扰他四次\n我一个月不就可以发\n四篇文章吗\n一个礼拜做一个活动\n如果你愿意的话\n一个礼拜做一个活动\n你都可以推到这些关注的粉丝身上\n它是一个不要钱的管道\n这管道重要不重要\n假如你有个\n几万个粉丝\n关注你的\n发一次活动\n不要钱就可以发起\n做片语\n营销活动永远是做片语\n我一下子通知了十万人\n和我一下子通知了百人\n所以这就是威力不一样的地方\n那我会说关注\n重要不重要\n很重要对吧\n那我们拿点好处给人家可以吗\n可以了\n所以关注有礼的目标是\n引导客人关注微信公众话\n打通与客人\n零成本沟通的管道\n所以这叫潜龙勿用\n你要低调\n隐忍把这个根本\n这个底子打扎实\n因为还是个话\n只有100个人能通知到\n和十万个人能通知到\n发一次同样的活动\n效果肯定不一样\n这是个底子的事情\n等到天上开始下雨了\n你才想到要做这件事情\n来不及了\n现在生意还不错\n努力的让每个客人\n成为我们的会员\n努力的让每个客人\n都关注我们的公众号\n那么为此\n我们送一杯饮料可以吗\n随便\n而且我认为\n应该送一个个\n个人好的东西\n他只供你想用\n因为我希望一张桌子\n十个人都关注\n如果你关注了八个\n我就送你八份甜品\n不要送大份的\n这个很重要\n越早做越好\n所以我一直在追你们\n这个地基的事情\n如果做了关注礼\n开卡礼不要做\n如果做了开卡礼\n关注礼就不要做\n我认为关注礼\n开卡礼好\n道理其实是一样的', '', '餐饮营销` `会员运营`  `开业营销` `微信公众号运营` `粉丝基建` `关注有礼` `营销体系` `餐饮老板` `讲座记录', '', NULL, 0, 0, NULL, NULL, NULL, NULL, 0, 0, 1, NULL, '2026-04-18 20:23:57', '2026-04-18 20:23:57');
INSERT INTO `gb_ai_knowledge` (`gb_ai_knowledge_id`, `gb_ai_knowledge_uuid`, `gb_ai_knowledge_type`, `gb_ai_knowledge_category`, `gb_ai_knowledge_title`, `gb_ai_knowledge_summary`, `gb_ai_knowledge_content`, `gb_ai_knowledge_source_url`, `gb_ai_knowledge_tags`, `gb_ai_knowledge_author`, `gb_ai_knowledge_origin`, `gb_ai_knowledge_effect_rating`, `gb_ai_knowledge_effect_cases`, `gb_ai_knowledge_effect_note`, `gb_ai_knowledge_suitable_restaurant`, `gb_ai_knowledge_suitable_budget`, `gb_ai_knowledge_suitable_season`, `gb_ai_knowledge_view_count`, `gb_ai_knowledge_use_count`, `gb_ai_knowledge_status`, `gb_ai_knowledge_publish_time`, `gb_ai_knowledge_create_time`, `gb_ai_knowledge_update_time`) VALUES (24, 'c6d0db76-9e47-486d-a4d1-aa42d693fb3a', 0, 'coupon', '会员管理系统的本质价值与企业微信熟客群基建', '本文为餐饮营销课程讲座续篇，深入讲解体系内三招要点。其一，开卡礼——当微信公众号不可用时的备选方案，核心目标是将每位买单客人转化为会员，其深层价值在于建立客户行为数据档案：了解客人消费频次、爱点什么菜、何时应主动推荐，而非仅做储值。其二，会员数据用于削峰填谷：将价格敏感客人引导至低谷时段，实现资源精准匹配。其三，入群有礼——推荐使用企业微信群而非个人微信群，因企业微信提供永久有效的群活码，单店可建最多9个500人熟客群，配合小份招牌菜作为入群奖励。此三招与前篇微信公众号（低频批量）形成\"月+日\"双频触达体系，共同构成降龙十八掌的沟通底座', '会员系统本质是客户行为管理系统，不止是储值——要知道客人来过几次、爱点什么菜、该推荐什么\n会员数据价值：把价格敏感客人引导到低谷时段，实现资源优化匹配\n企业微信替代个人微信做熟客群：群活码永久有效、无需频繁换码、单店最多9个500人群\n入群有礼送招牌菜小份即可，关键是建立高频日触达管道\n三管齐下：微信公众号（月频/批量）+ 企业微信（日频/日常）= 降龙十八掌沟通体系打通', '', '', '', NULL, 0, 0, NULL, NULL, NULL, NULL, 0, 0, 1, NULL, '2026-04-18 20:26:31', '2026-04-18 20:26:31');
COMMIT;

-- ----------------------------
-- Table structure for gb_ai_knowledge_tag
-- ----------------------------
DROP TABLE IF EXISTS `gb_ai_knowledge_tag`;
CREATE TABLE `gb_ai_knowledge_tag` (
  `gb_ai_knowledge_tag_id` bigint NOT NULL AUTO_INCREMENT,
  `gb_ai_knowledge_tag_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `gb_ai_knowledge_tag_color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签颜色',
  `gb_ai_knowledge_tag_type` int DEFAULT '0' COMMENT '标签类型: 0=通用, 1=节日, 2=方法',
  `gb_ai_knowledge_tag_count` int DEFAULT '0' COMMENT '使用次数',
  `gb_ai_knowledge_tag_status` int DEFAULT '1' COMMENT '状态: 0=禁用, 1=启用',
  `gb_ai_knowledge_tag_create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`gb_ai_knowledge_tag_id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of gb_ai_knowledge_tag
-- ----------------------------
BEGIN;
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (1, '母亲节', '#FF6B6B', 1, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (2, '父亲节', '#FF6B6B', 1, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (3, '情人节', '#FF6B6B', 1, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (4, '中秋节', '#FFA500', 1, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (5, '春节', '#FF0000', 1, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (6, '元旦', '#00FF00', 1, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (7, '五一', '#FFD700', 1, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (8, '国庆', '#FF0000', 1, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (9, '满减', '#4ECDC4', 2, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (10, '折扣', '#4ECDC4', 2, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (11, '赠品', '#4ECDC4', 2, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (12, '代金券', '#4ECDC4', 2, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (13, '套餐', '#4ECDC4', 2, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (14, '裂变', '#4ECDC4', 2, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (15, '低成本', '#95E1D3', 3, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (16, '高回报', '#95E1D3', 3, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (17, '提升客单', '#95E1D3', 3, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (18, '拉新', '#AA96DA', 4, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (19, '复购', '#AA96DA', 4, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (20, '清库存', '#AA96DA', 4, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (21, '锁客', '#AA96DA', 4, 0, 1, '2026-04-11 20:33:31');
INSERT INTO `gb_ai_knowledge_tag` (`gb_ai_knowledge_tag_id`, `gb_ai_knowledge_tag_name`, `gb_ai_knowledge_tag_color`, `gb_ai_knowledge_tag_type`, `gb_ai_knowledge_tag_count`, `gb_ai_knowledge_tag_status`, `gb_ai_knowledge_tag_create_time`) VALUES (22, '节日', '#AA96DA', 4, 0, 1, '2026-04-11 20:33:31');
COMMIT;

-- ----------------------------
-- Table structure for gb_ai_knowledge_usage
-- ----------------------------
DROP TABLE IF EXISTS `gb_ai_knowledge_usage`;
CREATE TABLE `gb_ai_knowledge_usage` (
  `gb_ai_knowledge_usage_id` bigint NOT NULL AUTO_INCREMENT,
  `gb_ai_knowledge_id` bigint DEFAULT NULL COMMENT '知识ID',
  `gb_ai_knowledge_usage_department_id` bigint DEFAULT NULL COMMENT '使用餐厅ID',
  `gb_ai_knowledge_usage_conversation_id` bigint DEFAULT NULL COMMENT '对话ID',
  `gb_ai_knowledge_usage_result` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结果: 推荐/采纳/拒绝',
  `gb_ai_knowledge_usage_effect` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '效果反馈: 好/中/差',
  `gb_ai_knowledge_usage_feedback` text COLLATE utf8mb4_unicode_ci COMMENT '用户反馈详情',
  `gb_ai_knowledge_usage_create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`gb_ai_knowledge_usage_id`),
  KEY `idx_knowledge_id` (`gb_ai_knowledge_id`),
  KEY `idx_department_id` (`gb_ai_knowledge_usage_department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records of gb_ai_knowledge_usage
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_ai_memory
-- ----------------------------
DROP TABLE IF EXISTS `gb_ai_memory`;
CREATE TABLE `gb_ai_memory` (
  `gb_ai_memory_id` bigint NOT NULL AUTO_INCREMENT,
  `gb_ai_memory_conversation_id` bigint DEFAULT NULL COMMENT '来源对话ID',
  `gb_ai_memory_type` int DEFAULT '0' COMMENT '记忆类型: 0=普通记忆, 1=促销活动/销售额, 2=公众号相关',
  `gb_ai_memory_department_id` bigint NOT NULL,
  `gb_ai_memory_distributer_id` bigint DEFAULT NULL,
  `gb_ai_memory_category` varchar(100) DEFAULT '' COMMENT '记忆分类',
  `gb_ai_memory_title` varchar(200) NOT NULL COMMENT 'è®°å¿†æ ‡é¢˜',
  `gb_ai_memory_content` text NOT NULL COMMENT 'è®°å¿†å†…å®¹',
  `gb_ai_memory_summary` varchar(255) DEFAULT NULL COMMENT '记忆内容摘要',
  `gb_ai_memory_importance` tinyint DEFAULT '5' COMMENT 'é‡è¦ç¨‹åº¦',
  `gb_ai_memory_source_conversation_id` bigint DEFAULT NULL COMMENT 'æ¥æºå¯¹è¯ID',
  `gb_ai_memory_tags` varchar(500) DEFAULT NULL COMMENT 'æ ‡ç­¾',
  `gb_ai_memory_last_used_time` datetime DEFAULT NULL COMMENT 'æœ€åŽä½¿ç”¨æ—¶é—´',
  `gb_ai_memory_use_count` int DEFAULT '0' COMMENT 'ä½¿ç”¨æ¬¡æ•°',
  `gb_ai_memory_status` int DEFAULT '0' COMMENT '状态: 0=活跃, 1=归档, 2=删除',
  `gb_ai_memory_is_expired` tinyint DEFAULT '0' COMMENT 'æ˜¯å¦è¿‡æœŸ',
  `gb_ai_memory_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `gb_ai_memory_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `gb_ai_memory_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`gb_ai_memory_id`),
  KEY `idx_gb_ai_mem_category` (`gb_ai_memory_category`),
  KEY `idx_gb_ai_mem_department` (`gb_ai_memory_department_id`),
  KEY `idx_gb_ai_mem_distributer` (`gb_ai_memory_distributer_id`),
  KEY `idx_gb_ai_mem_tags` ((cast(`gb_ai_memory_tags` as char(500) charset latin1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç»éªŒè®°å¿† L2';

-- ----------------------------
-- Records of gb_ai_memory
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_ai_message
-- ----------------------------
DROP TABLE IF EXISTS `gb_ai_message`;
CREATE TABLE `gb_ai_message` (
  `gb_ai_message_id` bigint NOT NULL AUTO_INCREMENT,
  `gb_ai_message_type` int DEFAULT '0' COMMENT '消息类型: 0=普通消息, 1=促销活动/销售额, 2=公众号相关',
  `gb_ai_message_conversation_id` bigint NOT NULL COMMENT 'å¯¹è¯ID',
  `gb_ai_message_role` varchar(20) NOT NULL COMMENT 'user/assistant/system',
  `gb_ai_message_content` text NOT NULL COMMENT 'æ¶ˆæ¯å†…å®¹',
  `gb_ai_message_token_count` int DEFAULT '0' COMMENT 'Tokenæ¶ˆè€—æ•°',
  `gb_ai_message_memory_extracted` tinyint DEFAULT '0' COMMENT 'æ˜¯å¦å·²æå–è®°å¿†',
  `gb_ai_message_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `gb_ai_message_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`gb_ai_message_id`),
  KEY `idx_gb_ai_message_conversation` (`gb_ai_message_conversation_id`),
  KEY `idx_gb_ai_message_conv_created` (`gb_ai_message_conversation_id`,`gb_ai_message_create_time`),
  KEY `idx_gb_ai_msg_conversation` (`gb_ai_message_conversation_id`),
  KEY `idx_gb_ai_msg_conv_created` (`gb_ai_message_conversation_id`,`gb_ai_message_create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å¯¹è¯æ¶ˆæ¯è®°å½•';

-- ----------------------------
-- Records of gb_ai_message
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_ai_restaurant_profile
-- ----------------------------
DROP TABLE IF EXISTS `gb_ai_restaurant_profile`;
CREATE TABLE `gb_ai_restaurant_profile` (
  `gb_ai_restaurant_profile_id` bigint NOT NULL AUTO_INCREMENT,
  `gb_ai_restaurant_profile_department_id` bigint NOT NULL,
  `gb_ai_restaurant_profile_distributer_id` bigint DEFAULT NULL,
  `gb_ai_restaurant_profile_restaurant_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'é¤åŽ…åç§°',
  `gb_ai_restaurant_profile_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'åœ°å€',
  `gb_ai_restaurant_profile_longitude` decimal(10,6) DEFAULT NULL COMMENT 'ç»åº¦',
  `gb_ai_restaurant_profile_latitude` decimal(10,6) DEFAULT NULL COMMENT 'çº¬åº¦',
  `gb_ai_restaurant_profile_business_district` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å•†åœˆ',
  `gb_ai_restaurant_profile_business_hours` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'è¥ä¸šæ—¶é—´',
  `gb_ai_restaurant_profile_cuisine_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'èœç³»',
  `gb_ai_restaurant_profile_avg_price` decimal(10,2) DEFAULT NULL COMMENT 'å®¢å•ä»·',
  `gb_ai_restaurant_profile_seat_count` int DEFAULT NULL COMMENT 'åº§ä½æ•°',
  `gb_ai_restaurant_profile_business_stage` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ç»è¥é˜¶æ®µ',
  `gb_ai_restaurant_profile_follower_count` int DEFAULT '0' COMMENT 'ç²‰ä¸æ•°',
  `gb_ai_restaurant_profile_daily_customers` int DEFAULT NULL COMMENT 'æ—¥å‡å®¢æµé‡',
  `gb_ai_restaurant_profile_daily_revenue` decimal(12,2) DEFAULT NULL COMMENT 'æ—¥å‡è¥ä¸šé¢',
  `gb_ai_restaurant_profile_target_age_range` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ç›®æ ‡å¹´é¾„æ®µ',
  `gb_ai_restaurant_profile_target_consumer` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ç›®æ ‡å®¢ç¾¤',
  `gb_ai_restaurant_profile_nearby_competitor_count` int DEFAULT NULL COMMENT 'é™„è¿‘ç«žå“æ•°',
  `gb_ai_restaurant_profile_market_saturation` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å¸‚åœºé¥±å’Œåº¦',
  `gb_ai_restaurant_profile_competitive_advantage` text COLLATE utf8mb4_unicode_ci COMMENT 'ç«žäº‰ä¼˜åŠ¿',
  `gb_ai_restaurant_profile_competitor_analysis` text COLLATE utf8mb4_unicode_ci COMMENT 'ç«žå“åˆ†æž',
  `gb_ai_restaurant_profile_competitor_analyzed_time` datetime DEFAULT NULL COMMENT 'ç«žäº‰åˆ†æžæ›´æ–°æ—¶é—´',
  `gb_ai_restaurant_profile_boss_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'è€æ¿ç§°å‘¼',
  `gb_ai_restaurant_profile_boss_style` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ²Ÿé€šé£Žæ ¼',
  `gb_ai_restaurant_profile_risk_preference` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'é£Žé™©åå¥½',
  `gb_ai_restaurant_profile_decision_speed` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å†³ç­–é€Ÿåº¦',
  `gb_ai_restaurant_profile_cost_sensitive` tinyint DEFAULT '1' COMMENT 'æˆæœ¬æ•æ„Ÿåº¦',
  `gb_ai_restaurant_profile_kitchen_capacity` tinyint DEFAULT NULL COMMENT 'åŽåŽ¨æ‰¿åŽ‹èƒ½åŠ›',
  `gb_ai_restaurant_profile_staff_count` int DEFAULT NULL COMMENT 'å‘˜å·¥äººæ•°',
  `gb_ai_restaurant_profile_rent_monthly` decimal(10,2) DEFAULT NULL COMMENT 'æœˆç§Ÿé‡‘',
  `gb_ai_restaurant_profile_last_chat_time` datetime DEFAULT NULL COMMENT 'æœ€åŽå¯¹è¯æ—¶é—´',
  `gb_ai_restaurant_profile_total_chat_count` int DEFAULT '0' COMMENT 'ç´¯è®¡å¯¹è¯æ¬¡æ•°',
  `gb_ai_restaurant_profile_summary` text COLLATE utf8mb4_unicode_ci COMMENT 'AIç»´æŠ¤çš„æ‘˜è¦',
  `gb_ai_restaurant_profile_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `gb_ai_restaurant_profile_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `gb_ai_restaurant_profile_monthly_wage` decimal(10,2) DEFAULT NULL COMMENT 'æœˆå·¥èµ„æ€»è®¡',
  `gb_ai_restaurant_profile_monthly_fixed_cost` decimal(10,2) DEFAULT NULL COMMENT 'æœˆå›ºå®šæ”¯å‡º',
  PRIMARY KEY (`gb_ai_restaurant_profile_id`),
  UNIQUE KEY `uk_gb_ai_restaurant_profile_restaurant` (`gb_ai_restaurant_profile_department_id`),
  UNIQUE KEY `uk_gb_ai_rp_restaurant` (`gb_ai_restaurant_profile_department_id`),
  KEY `idx_gb_ai_rp_department` (`gb_ai_restaurant_profile_department_id`),
  KEY `idx_gb_ai_rp_distributer` (`gb_ai_restaurant_profile_distributer_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='é¤åŽ…ç”»åƒ L1';

-- ----------------------------
-- Records of gb_ai_restaurant_profile
-- ----------------------------
BEGIN;
INSERT INTO `gb_ai_restaurant_profile` (`gb_ai_restaurant_profile_id`, `gb_ai_restaurant_profile_department_id`, `gb_ai_restaurant_profile_distributer_id`, `gb_ai_restaurant_profile_restaurant_name`, `gb_ai_restaurant_profile_address`, `gb_ai_restaurant_profile_longitude`, `gb_ai_restaurant_profile_latitude`, `gb_ai_restaurant_profile_business_district`, `gb_ai_restaurant_profile_business_hours`, `gb_ai_restaurant_profile_cuisine_type`, `gb_ai_restaurant_profile_avg_price`, `gb_ai_restaurant_profile_seat_count`, `gb_ai_restaurant_profile_business_stage`, `gb_ai_restaurant_profile_follower_count`, `gb_ai_restaurant_profile_daily_customers`, `gb_ai_restaurant_profile_daily_revenue`, `gb_ai_restaurant_profile_target_age_range`, `gb_ai_restaurant_profile_target_consumer`, `gb_ai_restaurant_profile_nearby_competitor_count`, `gb_ai_restaurant_profile_market_saturation`, `gb_ai_restaurant_profile_competitive_advantage`, `gb_ai_restaurant_profile_competitor_analysis`, `gb_ai_restaurant_profile_competitor_analyzed_time`, `gb_ai_restaurant_profile_boss_name`, `gb_ai_restaurant_profile_boss_style`, `gb_ai_restaurant_profile_risk_preference`, `gb_ai_restaurant_profile_decision_speed`, `gb_ai_restaurant_profile_cost_sensitive`, `gb_ai_restaurant_profile_kitchen_capacity`, `gb_ai_restaurant_profile_staff_count`, `gb_ai_restaurant_profile_rent_monthly`, `gb_ai_restaurant_profile_last_chat_time`, `gb_ai_restaurant_profile_total_chat_count`, `gb_ai_restaurant_profile_summary`, `gb_ai_restaurant_profile_create_time`, `gb_ai_restaurant_profile_update_time`, `gb_ai_restaurant_profile_monthly_wage`, `gb_ai_restaurant_profile_monthly_fixed_cost`) VALUES (1, 1, 1, '冲冲冲', '国贸 11 号', NULL, NULL, '国贸', '10:00-22:00', '快餐', 50.00, 12, '成熟期', 0, 100, 1200.00, '25-40', '工作餐', NULL, NULL, NULL, NULL, NULL, 'sisy', '细节型', '稳健型', '快（当场决定）', 1, 3, 5, 8000.00, NULL, 0, NULL, '2026-04-16 14:19:57', '2026-04-21 10:47:41', 15000.00, 800.00);
INSERT INTO `gb_ai_restaurant_profile` (`gb_ai_restaurant_profile_id`, `gb_ai_restaurant_profile_department_id`, `gb_ai_restaurant_profile_distributer_id`, `gb_ai_restaurant_profile_restaurant_name`, `gb_ai_restaurant_profile_address`, `gb_ai_restaurant_profile_longitude`, `gb_ai_restaurant_profile_latitude`, `gb_ai_restaurant_profile_business_district`, `gb_ai_restaurant_profile_business_hours`, `gb_ai_restaurant_profile_cuisine_type`, `gb_ai_restaurant_profile_avg_price`, `gb_ai_restaurant_profile_seat_count`, `gb_ai_restaurant_profile_business_stage`, `gb_ai_restaurant_profile_follower_count`, `gb_ai_restaurant_profile_daily_customers`, `gb_ai_restaurant_profile_daily_revenue`, `gb_ai_restaurant_profile_target_age_range`, `gb_ai_restaurant_profile_target_consumer`, `gb_ai_restaurant_profile_nearby_competitor_count`, `gb_ai_restaurant_profile_market_saturation`, `gb_ai_restaurant_profile_competitive_advantage`, `gb_ai_restaurant_profile_competitor_analysis`, `gb_ai_restaurant_profile_competitor_analyzed_time`, `gb_ai_restaurant_profile_boss_name`, `gb_ai_restaurant_profile_boss_style`, `gb_ai_restaurant_profile_risk_preference`, `gb_ai_restaurant_profile_decision_speed`, `gb_ai_restaurant_profile_cost_sensitive`, `gb_ai_restaurant_profile_kitchen_capacity`, `gb_ai_restaurant_profile_staff_count`, `gb_ai_restaurant_profile_rent_monthly`, `gb_ai_restaurant_profile_last_chat_time`, `gb_ai_restaurant_profile_total_chat_count`, `gb_ai_restaurant_profile_summary`, `gb_ai_restaurant_profile_create_time`, `gb_ai_restaurant_profile_update_time`, `gb_ai_restaurant_profile_monthly_wage`, `gb_ai_restaurant_profile_monthly_fixed_cost`) VALUES (2, 4, 2, '不是', '', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL, NULL, 0, NULL, '2026-04-18 19:50:40', '2026-04-18 19:50:40', NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for gb_dep_food
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_food`;
CREATE TABLE `gb_dep_food` (
  `gb_dep_food_id` int NOT NULL AUTO_INCREMENT COMMENT '门店菜品id',
  `gb_df_dep_id` int DEFAULT NULL COMMENT '所属门店id',
  `gb_df_food_id` int DEFAULT NULL COMMENT '菜品id（关联gb_distributer_food）',
  `gb_df_dep_father_id` int DEFAULT NULL COMMENT '门店菜品分类父id',
  `gb_df_food_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品价格',
  `gb_df_status` tinyint DEFAULT NULL COMMENT '状态',
  `gb_df_distributer_id` int DEFAULT NULL COMMENT '配送商id',
  `gb_df_nx_food_id` int DEFAULT NULL COMMENT '老系统食品id',
  PRIMARY KEY (`gb_dep_food_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Records of gb_dep_food
-- ----------------------------
BEGIN;
INSERT INTO `gb_dep_food` (`gb_dep_food_id`, `gb_df_dep_id`, `gb_df_food_id`, `gb_df_dep_father_id`, `gb_df_food_price`, `gb_df_status`, `gb_df_distributer_id`, `gb_df_nx_food_id`) VALUES (1, 3, 2, 1, '20', 0, 1, NULL);
COMMIT;

-- ----------------------------
-- Table structure for gb_dep_food_goods_sales
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_food_goods_sales`;
CREATE TABLE `gb_dep_food_goods_sales` (
  `gb_dep_food_goods_sales_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_DFGS_dep_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DFGS_dep_father_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DFGS_food_sales_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_food_goods_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_goods_amount` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_settle_id` int DEFAULT NULL,
  `gb_DFGS_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_full_Date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_dis_goods_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_revenue_weekday` tinyint DEFAULT NULL COMMENT 'æ˜ŸæœŸå‡ ',
  `gb_DFGS_revenue_holiday` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'èŠ‚å‡æ—¥åç§°',
  PRIMARY KEY (`gb_dep_food_goods_sales_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_dep_food_goods_sales
-- ----------------------------
BEGIN;
INSERT INTO `gb_dep_food_goods_sales` (`gb_dep_food_goods_sales_id`, `gb_DFGS_dep_id`, `gb_DFGS_dep_father_id`, `gb_DFGS_food_sales_id`, `gb_DFGS_food_goods_id`, `gb_DFGS_goods_amount`, `gb_DFGS_settle_id`, `gb_DFGS_month`, `gb_DFGS_full_Date`, `gb_DFGS_dis_goods_id`, `gb_DFGS_revenue_weekday`, `gb_DFGS_revenue_holiday`) VALUES (1, 3, 1, 1, 1, '6.5', NULL, '2026-04', '2026-04-23', 2, 4, '');
INSERT INTO `gb_dep_food_goods_sales` (`gb_dep_food_goods_sales_id`, `gb_DFGS_dep_id`, `gb_DFGS_dep_father_id`, `gb_DFGS_food_sales_id`, `gb_DFGS_food_goods_id`, `gb_DFGS_goods_amount`, `gb_DFGS_settle_id`, `gb_DFGS_month`, `gb_DFGS_full_Date`, `gb_DFGS_dis_goods_id`, `gb_DFGS_revenue_weekday`, `gb_DFGS_revenue_holiday`) VALUES (2, 3, 1, 2, 1, '5', NULL, '2026-04', '2026-04-21', 2, 2, '');
INSERT INTO `gb_dep_food_goods_sales` (`gb_dep_food_goods_sales_id`, `gb_DFGS_dep_id`, `gb_DFGS_dep_father_id`, `gb_DFGS_food_sales_id`, `gb_DFGS_food_goods_id`, `gb_DFGS_goods_amount`, `gb_DFGS_settle_id`, `gb_DFGS_month`, `gb_DFGS_full_Date`, `gb_DFGS_dis_goods_id`, `gb_DFGS_revenue_weekday`, `gb_DFGS_revenue_holiday`) VALUES (3, 3, 1, 3, 1, '6', NULL, '2026-04', '2026-04-22', 2, 3, '');
COMMIT;

-- ----------------------------
-- Table structure for gb_dep_food_sales
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_food_sales`;
CREATE TABLE `gb_dep_food_sales` (
  `gb_dep_food_sales_id` int NOT NULL AUTO_INCREMENT COMMENT '门店菜品销售id',
  `gb_dfs_dep_id` int DEFAULT NULL COMMENT '所属门店id',
  `gb_dfs_food_id` int DEFAULT NULL COMMENT '菜品id',
  `gb_dfs_dep_father_id` int DEFAULT NULL COMMENT '门店菜品分类父id',
  `gb_dfs_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售数量',
  `gb_dfs_settle_id` int DEFAULT NULL COMMENT '结算id',
  `gb_dfs_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售月份',
  `gb_dfs_full_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '完整日期',
  `gb_dfs_user_id` int DEFAULT NULL COMMENT '用户id',
  `gb_dfs_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售年份',
  `gb_dfs_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '小计金额',
  `gb_dfs_distributer_id` int DEFAULT NULL COMMENT '配送商id',
  `gb_dfs_revenue_weekday` tinyint DEFAULT NULL COMMENT 'æ˜ŸæœŸå‡ ',
  `gb_dfs_revenue_holiday` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'èŠ‚å‡æ—¥åç§°',
  PRIMARY KEY (`gb_dep_food_sales_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Records of gb_dep_food_sales
-- ----------------------------
BEGIN;
INSERT INTO `gb_dep_food_sales` (`gb_dep_food_sales_id`, `gb_dfs_dep_id`, `gb_dfs_food_id`, `gb_dfs_dep_father_id`, `gb_dfs_amount`, `gb_dfs_settle_id`, `gb_dfs_month`, `gb_dfs_full_date`, `gb_dfs_user_id`, `gb_dfs_year`, `gb_dfs_subtotal`, `gb_dfs_distributer_id`, `gb_dfs_revenue_weekday`, `gb_dfs_revenue_holiday`) VALUES (1, 3, 2, 1, '13', NULL, '2026-04', '2026-04-23', NULL, '2026', '260', 1, 4, '');
INSERT INTO `gb_dep_food_sales` (`gb_dep_food_sales_id`, `gb_dfs_dep_id`, `gb_dfs_food_id`, `gb_dfs_dep_father_id`, `gb_dfs_amount`, `gb_dfs_settle_id`, `gb_dfs_month`, `gb_dfs_full_date`, `gb_dfs_user_id`, `gb_dfs_year`, `gb_dfs_subtotal`, `gb_dfs_distributer_id`, `gb_dfs_revenue_weekday`, `gb_dfs_revenue_holiday`) VALUES (2, 3, 2, 1, '10', NULL, '2026-04', '2026-04-21', NULL, '2026', '200', 1, 2, '');
INSERT INTO `gb_dep_food_sales` (`gb_dep_food_sales_id`, `gb_dfs_dep_id`, `gb_dfs_food_id`, `gb_dfs_dep_father_id`, `gb_dfs_amount`, `gb_dfs_settle_id`, `gb_dfs_month`, `gb_dfs_full_date`, `gb_dfs_user_id`, `gb_dfs_year`, `gb_dfs_subtotal`, `gb_dfs_distributer_id`, `gb_dfs_revenue_weekday`, `gb_dfs_revenue_holiday`) VALUES (3, 3, 2, 1, '12', NULL, '2026-04', '2026-04-22', NULL, '2026', '240', 1, 3, '');
COMMIT;

-- ----------------------------
-- Table structure for gb_department
-- ----------------------------
DROP TABLE IF EXISTS `gb_department`;
CREATE TABLE `gb_department` (
  `gb_department_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门id',
  `gb_department_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门名称',
  `gb_department_father_id` int DEFAULT NULL COMMENT '订货部门上级id',
  `gb_department_type` tinyint DEFAULT NULL COMMENT '订货部门类型',
  `gb_department_sub_amount` int DEFAULT NULL COMMENT '订货部门子部门数量',
  `gb_department_dis_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `gb_department_file_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_department_is_group_dep` tinyint DEFAULT NULL COMMENT '是客户吗',
  `gb_department_print_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_department_show_weeks` tinyint DEFAULT '1',
  `gb_department_settle_type` tinyint DEFAULT NULL,
  `gb_department_attr_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '客户简称',
  `gb_department_route_id` int DEFAULT NULL,
  `gb_department_settle_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_department_settle_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_department_settle_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算周',
  `gb_department_settle_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_department_settle_times` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_department_settle_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_department_dep_settle_id` int DEFAULT NULL COMMENT '结算月',
  `gb_department_level` int DEFAULT NULL COMMENT '加盟级别',
  `gb_department_sort` int DEFAULT NULL COMMENT '排序',
  `gb_department_print_set` int DEFAULT NULL COMMENT '排序',
  `gb_department_name_py` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门名称拼音',
  `gb_department_latitude` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_department_longitude` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_department_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_department
-- ----------------------------
BEGIN;
INSERT INTO `gb_department` (`gb_department_id`, `gb_department_name`, `gb_department_father_id`, `gb_department_type`, `gb_department_sub_amount`, `gb_department_dis_id`, `gb_department_file_path`, `gb_department_is_group_dep`, `gb_department_print_name`, `gb_department_show_weeks`, `gb_department_settle_type`, `gb_department_attr_name`, `gb_department_route_id`, `gb_department_settle_full_time`, `gb_department_settle_date`, `gb_department_settle_week`, `gb_department_settle_month`, `gb_department_settle_times`, `gb_department_settle_year`, `gb_department_dep_settle_id`, `gb_department_level`, `gb_department_sort`, `gb_department_print_set`, `gb_department_name_py`, `gb_department_latitude`, `gb_department_longitude`) VALUES (1, '冲冲冲', 0, 1, 1, 1, NULL, 1, NULL, 1, NULL, '冲冲冲', NULL, '2026-04-16 14:19', '2026-04-16', '16', '04', '0', '2026', NULL, NULL, NULL, 0, 'ccc', NULL, NULL);
INSERT INTO `gb_department` (`gb_department_id`, `gb_department_name`, `gb_department_father_id`, `gb_department_type`, `gb_department_sub_amount`, `gb_department_dis_id`, `gb_department_file_path`, `gb_department_is_group_dep`, `gb_department_print_name`, `gb_department_show_weeks`, `gb_department_settle_type`, `gb_department_attr_name`, `gb_department_route_id`, `gb_department_settle_full_time`, `gb_department_settle_date`, `gb_department_settle_week`, `gb_department_settle_month`, `gb_department_settle_times`, `gb_department_settle_year`, `gb_department_dep_settle_id`, `gb_department_level`, `gb_department_sort`, `gb_department_print_set`, `gb_department_name_py`, `gb_department_latitude`, `gb_department_longitude`) VALUES (3, '后厨', 1, 1, 0, 1, NULL, 0, 'ApplyHalfPanel', 1, NULL, '后厨', NULL, '2026-04-16 14:39', '2026-04-16', '16', '04', '0', '2026', -1, 1, NULL, NULL, 'hc', NULL, NULL);
INSERT INTO `gb_department` (`gb_department_id`, `gb_department_name`, `gb_department_father_id`, `gb_department_type`, `gb_department_sub_amount`, `gb_department_dis_id`, `gb_department_file_path`, `gb_department_is_group_dep`, `gb_department_print_name`, `gb_department_show_weeks`, `gb_department_settle_type`, `gb_department_attr_name`, `gb_department_route_id`, `gb_department_settle_full_time`, `gb_department_settle_date`, `gb_department_settle_week`, `gb_department_settle_month`, `gb_department_settle_times`, `gb_department_settle_year`, `gb_department_dep_settle_id`, `gb_department_level`, `gb_department_sort`, `gb_department_print_set`, `gb_department_name_py`, `gb_department_latitude`, `gb_department_longitude`) VALUES (4, '不是', 0, 1, 1, 2, NULL, 1, NULL, 1, NULL, '不是', NULL, '2026-04-18 19:50', '2026-04-18', '16', '04', '0', '2026', NULL, NULL, NULL, 0, 'bs', NULL, NULL);
INSERT INTO `gb_department` (`gb_department_id`, `gb_department_name`, `gb_department_father_id`, `gb_department_type`, `gb_department_sub_amount`, `gb_department_dis_id`, `gb_department_file_path`, `gb_department_is_group_dep`, `gb_department_print_name`, `gb_department_show_weeks`, `gb_department_settle_type`, `gb_department_attr_name`, `gb_department_route_id`, `gb_department_settle_full_time`, `gb_department_settle_date`, `gb_department_settle_week`, `gb_department_settle_month`, `gb_department_settle_times`, `gb_department_settle_year`, `gb_department_dep_settle_id`, `gb_department_level`, `gb_department_sort`, `gb_department_print_set`, `gb_department_name_py`, `gb_department_latitude`, `gb_department_longitude`) VALUES (5, '不是部门一', 4, 1, 0, 2, NULL, 0, NULL, 1, NULL, '不是', NULL, '2026-04-18 19:50', '2026-04-18', '16', '04', '0', '2026', NULL, NULL, NULL, 0, 'bsbmy', NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for gb_department_bill
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_bill`;
CREATE TABLE `gb_department_bill` (
  `gb_department_bill_id` int NOT NULL AUTO_INCREMENT,
  `gb_DB_dis_id` int DEFAULT NULL,
  `gb_DB_dep_id` int DEFAULT NULL,
  `gb_DB_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_status` tinyint DEFAULT NULL,
  `gb_DB_time` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_issue_user_id` int DEFAULT NULL,
  `gb_DB_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_trade_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_print_times` int DEFAULT NULL,
  `gb_DB_day` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '星期',
  `gb_DB_issue_order_type` tinyint DEFAULT NULL,
  `gb_DB_issue_dep_id` int DEFAULT NULL,
  `gb_DB_order_amount` int DEFAULT NULL,
  `gb_DB_confirm_goods_user_id` int DEFAULT NULL,
  `gb_DB_confirm_price_user_id` int DEFAULT NULL,
  `gb_DB_confirm_settle_user_id` int DEFAULT NULL,
  `gb_DB_confirm_goods_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_confirm_price_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_confirm_settle_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_dep_settle_id` int DEFAULT NULL,
  `gb_DB_issue_nx_dis_id` int DEFAULT NULL,
  `gb_DB_selling_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_gb_supplier_payment_id` int DEFAULT NULL,
  `gb_DB_wx_out_trade_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_will_pay_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_dep_father_id` int DEFAULT NULL,
  `gb_DB_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_set_auto_goods` tinyint DEFAULT NULL,
  `gb_DB_pay_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_user_coupon_id` int DEFAULT NULL,
  `gb_DB_user_coupon_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_return_order_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_return_order_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_bill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_department_bill
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_department_dis_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_dis_goods`;
CREATE TABLE `gb_department_dis_goods` (
  `gb_department_dis_goods_id` int NOT NULL AUTO_INCREMENT,
  `gb_DDG_department_father_id` int DEFAULT NULL,
  `gb_DDG_department_id` int DEFAULT NULL,
  `gb_DDG_dis_goods_id` int DEFAULT NULL,
  `gb_DDG_dis_goods_father_id` int DEFAULT NULL,
  `gb_DDG_dep_goods_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_pinyin` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_py` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_standardname` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_detail` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_brand` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_place` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_gb_department_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_DDG_goods_type` tinyint DEFAULT NULL,
  `gb_DDG_nx_distributer_id` int DEFAULT NULL COMMENT '商品库父类id',
  `gb_DDG_nx_distributer_goods_id` int DEFAULT NULL,
  `gb_DDG_inventory_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_inventory_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_stock_total_weight` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_stock_total_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_gb_supplier_id` int DEFAULT NULL COMMENT '指定供货商id',
  `gb_DDG_prepare_total_weight` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_show_standard_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_show_standard_id` int DEFAULT NULL,
  `gb_DDG_show_standard_scale` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_level_price` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '加盟级别商品价格',
  `gb_DDG_prepare_status` int DEFAULT NULL,
  `gb_DDG_selling_price` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '加盟级别商品价格',
  `gb_DDG_show_standard_weight` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_price` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_quantity` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_standard` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_print_standard` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_gb_dis_id` int DEFAULT NULL,
  `gb_DDG_dep_goods_pull_off` int DEFAULT NULL,
  `gb_DDG_dep_goods_status` int DEFAULT NULL,
  `gb_DDG_dis_goods_grand_id` int DEFAULT NULL,
  `gb_DDG_dis_goods_great_id` int DEFAULT NULL,
  `gb_DDG_order_goods_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_price_level` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_department_dis_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_department_dis_goods
-- ----------------------------
BEGIN;
INSERT INTO `gb_department_dis_goods` (`gb_department_dis_goods_id`, `gb_DDG_department_father_id`, `gb_DDG_department_id`, `gb_DDG_dis_goods_id`, `gb_DDG_dis_goods_father_id`, `gb_DDG_dep_goods_name`, `gb_DDG_dep_goods_pinyin`, `gb_DDG_dep_goods_py`, `gb_DDG_dep_goods_standardname`, `gb_DDG_dep_goods_detail`, `gb_DDG_dep_goods_brand`, `gb_DDG_dep_goods_place`, `gb_DDG_gb_department_id`, `gb_DDG_goods_type`, `gb_DDG_nx_distributer_id`, `gb_DDG_nx_distributer_goods_id`, `gb_DDG_inventory_date`, `gb_DDG_inventory_full_time`, `gb_DDG_stock_total_weight`, `gb_DDG_stock_total_subtotal`, `gb_DDG_gb_supplier_id`, `gb_DDG_prepare_total_weight`, `gb_DDG_show_standard_name`, `gb_DDG_show_standard_id`, `gb_DDG_show_standard_scale`, `gb_DDG_level_price`, `gb_DDG_prepare_status`, `gb_DDG_selling_price`, `gb_DDG_show_standard_weight`, `gb_DDG_order_price`, `gb_DDG_order_date`, `gb_DDG_order_remark`, `gb_DDG_order_quantity`, `gb_DDG_order_standard`, `gb_DDG_print_standard`, `gb_DDG_order_weight`, `gb_DDG_gb_dis_id`, `gb_DDG_dep_goods_pull_off`, `gb_DDG_dep_goods_status`, `gb_DDG_dis_goods_grand_id`, `gb_DDG_dis_goods_great_id`, `gb_DDG_order_goods_name`, `gb_DDG_order_price_level`) VALUES (1, 1, 3, 1, 1, '圆白菜', 'yuanbaicai', 'ybc', '斤', NULL, NULL, NULL, NULL, 2, NULL, NULL, NULL, NULL, '0.0', '0.0', NULL, NULL, '斤', -1, '-1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '颗', NULL, NULL, 1, NULL, NULL, 2, 3, NULL, NULL);
INSERT INTO `gb_department_dis_goods` (`gb_department_dis_goods_id`, `gb_DDG_department_father_id`, `gb_DDG_department_id`, `gb_DDG_dis_goods_id`, `gb_DDG_dis_goods_father_id`, `gb_DDG_dep_goods_name`, `gb_DDG_dep_goods_pinyin`, `gb_DDG_dep_goods_py`, `gb_DDG_dep_goods_standardname`, `gb_DDG_dep_goods_detail`, `gb_DDG_dep_goods_brand`, `gb_DDG_dep_goods_place`, `gb_DDG_gb_department_id`, `gb_DDG_goods_type`, `gb_DDG_nx_distributer_id`, `gb_DDG_nx_distributer_goods_id`, `gb_DDG_inventory_date`, `gb_DDG_inventory_full_time`, `gb_DDG_stock_total_weight`, `gb_DDG_stock_total_subtotal`, `gb_DDG_gb_supplier_id`, `gb_DDG_prepare_total_weight`, `gb_DDG_show_standard_name`, `gb_DDG_show_standard_id`, `gb_DDG_show_standard_scale`, `gb_DDG_level_price`, `gb_DDG_prepare_status`, `gb_DDG_selling_price`, `gb_DDG_show_standard_weight`, `gb_DDG_order_price`, `gb_DDG_order_date`, `gb_DDG_order_remark`, `gb_DDG_order_quantity`, `gb_DDG_order_standard`, `gb_DDG_print_standard`, `gb_DDG_order_weight`, `gb_DDG_gb_dis_id`, `gb_DDG_dep_goods_pull_off`, `gb_DDG_dep_goods_status`, `gb_DDG_dis_goods_grand_id`, `gb_DDG_dis_goods_great_id`, `gb_DDG_order_goods_name`, `gb_DDG_order_price_level`) VALUES (2, 1, 3, 2, 4, '猪肉后臀尖', 'zhurouhoutunjian', 'zrhtj', '斤', NULL, NULL, NULL, NULL, 2, NULL, NULL, NULL, NULL, '0.0', '0.0', NULL, NULL, '斤', -1, '-1', NULL, NULL, NULL, '', NULL, NULL, NULL, NULL, '斤', NULL, NULL, 1, NULL, NULL, 5, 6, NULL, NULL);
INSERT INTO `gb_department_dis_goods` (`gb_department_dis_goods_id`, `gb_DDG_department_father_id`, `gb_DDG_department_id`, `gb_DDG_dis_goods_id`, `gb_DDG_dis_goods_father_id`, `gb_DDG_dep_goods_name`, `gb_DDG_dep_goods_pinyin`, `gb_DDG_dep_goods_py`, `gb_DDG_dep_goods_standardname`, `gb_DDG_dep_goods_detail`, `gb_DDG_dep_goods_brand`, `gb_DDG_dep_goods_place`, `gb_DDG_gb_department_id`, `gb_DDG_goods_type`, `gb_DDG_nx_distributer_id`, `gb_DDG_nx_distributer_goods_id`, `gb_DDG_inventory_date`, `gb_DDG_inventory_full_time`, `gb_DDG_stock_total_weight`, `gb_DDG_stock_total_subtotal`, `gb_DDG_gb_supplier_id`, `gb_DDG_prepare_total_weight`, `gb_DDG_show_standard_name`, `gb_DDG_show_standard_id`, `gb_DDG_show_standard_scale`, `gb_DDG_level_price`, `gb_DDG_prepare_status`, `gb_DDG_selling_price`, `gb_DDG_show_standard_weight`, `gb_DDG_order_price`, `gb_DDG_order_date`, `gb_DDG_order_remark`, `gb_DDG_order_quantity`, `gb_DDG_order_standard`, `gb_DDG_print_standard`, `gb_DDG_order_weight`, `gb_DDG_gb_dis_id`, `gb_DDG_dep_goods_pull_off`, `gb_DDG_dep_goods_status`, `gb_DDG_dis_goods_grand_id`, `gb_DDG_dis_goods_great_id`, `gb_DDG_order_goods_name`, `gb_DDG_order_price_level`) VALUES (3, 1, 3, 3, 7, '海天5度白醋', 'haitian5dubaicu', 'ht5dbc', '桶', NULL, NULL, NULL, NULL, 2, NULL, NULL, NULL, NULL, '0.0', '0.0', NULL, NULL, '桶', -1, '-1', NULL, NULL, NULL, '1.9L', NULL, NULL, NULL, NULL, '箱', NULL, NULL, 1, NULL, NULL, 8, 9, NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for gb_department_goods_daily
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_daily`;
CREATE TABLE `gb_department_goods_daily` (
  `gb_department_goods_daily_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgd_gb_distributer_id` int DEFAULT NULL,
  `gb_dgd_gb_department_id` int DEFAULT NULL,
  `gb_dgd_gb_department_father_id` int DEFAULT NULL,
  `gb_dgd_gb_dis_goods_id` int DEFAULT NULL,
  `gb_dgd_gb_dis_goods_father_id` int DEFAULT NULL,
  `gb_dgd_gb_dep_dis_goods_id` int DEFAULT NULL,
  `gb_dgd_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_rest_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_loss_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_waste_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_return_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_day` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_produce_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_rest_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_loss_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_waste_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_return_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_profit_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_sales_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_after_profit_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_sell_clear_hour` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_sell_clear_minute` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_last_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_last_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_fresh_rate` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_task_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_last_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_status` tinyint DEFAULT NULL,
  `gb_dgd_gb_dis_goods_grand_id` int DEFAULT NULL,
  `gb_dgd_gb_dis_goods_great_grand_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_goods_daily_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_department_goods_daily
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_department_goods_stock
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_stock`;
CREATE TABLE `gb_department_goods_stock` (
  `gb_department_goods_stock_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgs_gb_distributer_id` int DEFAULT NULL,
  `gb_dgs_gb_department_id` int DEFAULT NULL,
  `gb_dgs_gb_department_father_id` int DEFAULT NULL,
  `gb_dgs_gb_dis_goods_id` int DEFAULT NULL,
  `gb_dgs_gb_dis_goods_father_id` int DEFAULT NULL,
  `gb_dgs_gb_dep_dis_goods_id` int DEFAULT NULL,
  `gb_dgs_gb_department_order_id` int DEFAULT NULL,
  `gb_dgs_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次数量',
  `gb_dgs_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次单价',
  `gb_dgs_selling_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '销售单价',
  `gb_dgs_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次成本',
  `gb_dgs_rest_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '剩余数量',
  `gb_dgs_rest_weight_show_standard` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '剩余数量显示规格',
  `gb_dgs_rest_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次剩余成本',
  `gb_dgs_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次日期',
  `gb_dgs_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_out_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '出库日期',
  `gb_dgs_out_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '出库时间',
  `gb_dgs_out_hour` int DEFAULT NULL COMMENT '出货小时',
  `gb_dgs_receive_user_id` int DEFAULT NULL COMMENT '接收用户',
  `gb_dgs_status` tinyint DEFAULT NULL COMMENT '批次状态',
  `gb_dgs_gb_pur_goods_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgs_gb_price_goods_id` int DEFAULT NULL COMMENT '价格异常商品id',
  `gb_dgs_gb_price_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '价格异常价格',
  `gb_dgs_gb_price_subtotal_scale` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '价格异常价格',
  `gb_dgs_gb_goods_stock_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgs_gb_from_department_id` int DEFAULT NULL COMMENT '出库部门id',
  `gb_dgs_week` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次周',
  `gb_dgs_month` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次月',
  `gb_dgs_year` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次年',
  `gb_dgs_time_stamp` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '时间戳',
  `gb_dgs_warn_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_waste_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_warn_time_quantum_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '报警时间段名称',
  `gb_dgs_waste_time_quantum_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '废弃时间段名称',
  `gb_dgs_do_waste_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '执行废弃时间',
  `gb_dgs_inventory_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_inventory_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_inventory_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库周',
  `gb_dgs_inventory_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgs_inventory_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgs_dep_settle_id` int DEFAULT NULL,
  `gb_dgs_from_dep_settle_id` int DEFAULT NULL COMMENT '出货部门settleId',
  `gb_dgs_return_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '退货数量',
  `gb_dgs_return_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '退货成本',
  `gb_dgs_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgs_produce_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '制作成本',
  `gb_dgs_loss_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '损耗数量',
  `gb_dgs_loss_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '损耗成本',
  `gb_dgs_waste_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgs_waste_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgs_between_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '利润单价',
  `gb_dgs_profit_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '利润小计',
  `gb_dgs_profit_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '利润重量',
  `gb_dgs_selling_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '销售小计',
  `gb_dgs_weight_goods_id` int DEFAULT NULL COMMENT '备货商品id',
  `gb_dgs_after_profit_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '销售利润',
  `gb_dgs_cost_rate` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '成本率',
  `gb_dgs_rest_weight_show_standard_name` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '剩余数量显示规格',
  `gb_dgs_nx_distributer_id` int DEFAULT NULL COMMENT '备货商品id',
  `gb_dgs_produce_selling_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '销售小计',
  `gb_dgs_gb_dis_goods_grand_id` int DEFAULT NULL,
  `gb_dgs_gb_dis_goods_great_id` int DEFAULT NULL,
  `gb_dgs_stars` int DEFAULT NULL,
  `gb_dgs_nx_supplier_id` int DEFAULT NULL COMMENT '备货商品id',
  `gb_dgs_pur_user_id` int DEFAULT NULL COMMENT '备货商品id',
  PRIMARY KEY (`gb_department_goods_stock_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_department_goods_stock
-- ----------------------------
BEGIN;
INSERT INTO `gb_department_goods_stock` (`gb_department_goods_stock_id`, `gb_dgs_gb_distributer_id`, `gb_dgs_gb_department_id`, `gb_dgs_gb_department_father_id`, `gb_dgs_gb_dis_goods_id`, `gb_dgs_gb_dis_goods_father_id`, `gb_dgs_gb_dep_dis_goods_id`, `gb_dgs_gb_department_order_id`, `gb_dgs_weight`, `gb_dgs_price`, `gb_dgs_selling_price`, `gb_dgs_subtotal`, `gb_dgs_rest_weight`, `gb_dgs_rest_weight_show_standard`, `gb_dgs_rest_subtotal`, `gb_dgs_date`, `gb_dgs_full_time`, `gb_dgs_out_date`, `gb_dgs_out_full_time`, `gb_dgs_out_hour`, `gb_dgs_receive_user_id`, `gb_dgs_status`, `gb_dgs_gb_pur_goods_id`, `gb_dgs_gb_price_goods_id`, `gb_dgs_gb_price_subtotal`, `gb_dgs_gb_price_subtotal_scale`, `gb_dgs_gb_goods_stock_id`, `gb_dgs_gb_from_department_id`, `gb_dgs_week`, `gb_dgs_month`, `gb_dgs_year`, `gb_dgs_time_stamp`, `gb_dgs_warn_full_time`, `gb_dgs_waste_full_time`, `gb_dgs_warn_time_quantum_name`, `gb_dgs_waste_time_quantum_name`, `gb_dgs_do_waste_full_time`, `gb_dgs_inventory_full_time`, `gb_dgs_inventory_date`, `gb_dgs_inventory_week`, `gb_dgs_inventory_month`, `gb_dgs_inventory_year`, `gb_dgs_dep_settle_id`, `gb_dgs_from_dep_settle_id`, `gb_dgs_return_weight`, `gb_dgs_return_subtotal`, `gb_dgs_produce_weight`, `gb_dgs_produce_subtotal`, `gb_dgs_loss_weight`, `gb_dgs_loss_subtotal`, `gb_dgs_waste_weight`, `gb_dgs_waste_subtotal`, `gb_dgs_between_price`, `gb_dgs_profit_subtotal`, `gb_dgs_profit_weight`, `gb_dgs_selling_subtotal`, `gb_dgs_weight_goods_id`, `gb_dgs_after_profit_subtotal`, `gb_dgs_cost_rate`, `gb_dgs_rest_weight_show_standard_name`, `gb_dgs_nx_distributer_id`, `gb_dgs_produce_selling_subtotal`, `gb_dgs_gb_dis_goods_grand_id`, `gb_dgs_gb_dis_goods_great_id`, `gb_dgs_stars`, `gb_dgs_nx_supplier_id`, `gb_dgs_pur_user_id`) VALUES (1, 1, 3, 1, 1, 1, 1, NULL, '3', '1', NULL, '3.0', '3', NULL, '3.0', '2026-04-23', '2026-04-23 11:05', NULL, NULL, NULL, NULL, 0, 1, NULL, NULL, NULL, NULL, NULL, '星期四', '04', '2026', '1776913559', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2, 3, NULL, -1, 1);
INSERT INTO `gb_department_goods_stock` (`gb_department_goods_stock_id`, `gb_dgs_gb_distributer_id`, `gb_dgs_gb_department_id`, `gb_dgs_gb_department_father_id`, `gb_dgs_gb_dis_goods_id`, `gb_dgs_gb_dis_goods_father_id`, `gb_dgs_gb_dep_dis_goods_id`, `gb_dgs_gb_department_order_id`, `gb_dgs_weight`, `gb_dgs_price`, `gb_dgs_selling_price`, `gb_dgs_subtotal`, `gb_dgs_rest_weight`, `gb_dgs_rest_weight_show_standard`, `gb_dgs_rest_subtotal`, `gb_dgs_date`, `gb_dgs_full_time`, `gb_dgs_out_date`, `gb_dgs_out_full_time`, `gb_dgs_out_hour`, `gb_dgs_receive_user_id`, `gb_dgs_status`, `gb_dgs_gb_pur_goods_id`, `gb_dgs_gb_price_goods_id`, `gb_dgs_gb_price_subtotal`, `gb_dgs_gb_price_subtotal_scale`, `gb_dgs_gb_goods_stock_id`, `gb_dgs_gb_from_department_id`, `gb_dgs_week`, `gb_dgs_month`, `gb_dgs_year`, `gb_dgs_time_stamp`, `gb_dgs_warn_full_time`, `gb_dgs_waste_full_time`, `gb_dgs_warn_time_quantum_name`, `gb_dgs_waste_time_quantum_name`, `gb_dgs_do_waste_full_time`, `gb_dgs_inventory_full_time`, `gb_dgs_inventory_date`, `gb_dgs_inventory_week`, `gb_dgs_inventory_month`, `gb_dgs_inventory_year`, `gb_dgs_dep_settle_id`, `gb_dgs_from_dep_settle_id`, `gb_dgs_return_weight`, `gb_dgs_return_subtotal`, `gb_dgs_produce_weight`, `gb_dgs_produce_subtotal`, `gb_dgs_loss_weight`, `gb_dgs_loss_subtotal`, `gb_dgs_waste_weight`, `gb_dgs_waste_subtotal`, `gb_dgs_between_price`, `gb_dgs_profit_subtotal`, `gb_dgs_profit_weight`, `gb_dgs_selling_subtotal`, `gb_dgs_weight_goods_id`, `gb_dgs_after_profit_subtotal`, `gb_dgs_cost_rate`, `gb_dgs_rest_weight_show_standard_name`, `gb_dgs_nx_distributer_id`, `gb_dgs_produce_selling_subtotal`, `gb_dgs_gb_dis_goods_grand_id`, `gb_dgs_gb_dis_goods_great_id`, `gb_dgs_stars`, `gb_dgs_nx_supplier_id`, `gb_dgs_pur_user_id`) VALUES (2, 1, 3, 1, 2, 4, 2, NULL, '5', '12', NULL, '60.0', '5', NULL, '60.0', '2026-04-23', '2026-04-23 11:06', NULL, NULL, NULL, NULL, 0, 2, NULL, NULL, NULL, NULL, NULL, '星期四', '04', '2026', '1776913567', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 5, 6, NULL, -1, 1);
INSERT INTO `gb_department_goods_stock` (`gb_department_goods_stock_id`, `gb_dgs_gb_distributer_id`, `gb_dgs_gb_department_id`, `gb_dgs_gb_department_father_id`, `gb_dgs_gb_dis_goods_id`, `gb_dgs_gb_dis_goods_father_id`, `gb_dgs_gb_dep_dis_goods_id`, `gb_dgs_gb_department_order_id`, `gb_dgs_weight`, `gb_dgs_price`, `gb_dgs_selling_price`, `gb_dgs_subtotal`, `gb_dgs_rest_weight`, `gb_dgs_rest_weight_show_standard`, `gb_dgs_rest_subtotal`, `gb_dgs_date`, `gb_dgs_full_time`, `gb_dgs_out_date`, `gb_dgs_out_full_time`, `gb_dgs_out_hour`, `gb_dgs_receive_user_id`, `gb_dgs_status`, `gb_dgs_gb_pur_goods_id`, `gb_dgs_gb_price_goods_id`, `gb_dgs_gb_price_subtotal`, `gb_dgs_gb_price_subtotal_scale`, `gb_dgs_gb_goods_stock_id`, `gb_dgs_gb_from_department_id`, `gb_dgs_week`, `gb_dgs_month`, `gb_dgs_year`, `gb_dgs_time_stamp`, `gb_dgs_warn_full_time`, `gb_dgs_waste_full_time`, `gb_dgs_warn_time_quantum_name`, `gb_dgs_waste_time_quantum_name`, `gb_dgs_do_waste_full_time`, `gb_dgs_inventory_full_time`, `gb_dgs_inventory_date`, `gb_dgs_inventory_week`, `gb_dgs_inventory_month`, `gb_dgs_inventory_year`, `gb_dgs_dep_settle_id`, `gb_dgs_from_dep_settle_id`, `gb_dgs_return_weight`, `gb_dgs_return_subtotal`, `gb_dgs_produce_weight`, `gb_dgs_produce_subtotal`, `gb_dgs_loss_weight`, `gb_dgs_loss_subtotal`, `gb_dgs_waste_weight`, `gb_dgs_waste_subtotal`, `gb_dgs_between_price`, `gb_dgs_profit_subtotal`, `gb_dgs_profit_weight`, `gb_dgs_selling_subtotal`, `gb_dgs_weight_goods_id`, `gb_dgs_after_profit_subtotal`, `gb_dgs_cost_rate`, `gb_dgs_rest_weight_show_standard_name`, `gb_dgs_nx_distributer_id`, `gb_dgs_produce_selling_subtotal`, `gb_dgs_gb_dis_goods_grand_id`, `gb_dgs_gb_dis_goods_great_id`, `gb_dgs_stars`, `gb_dgs_nx_supplier_id`, `gb_dgs_pur_user_id`) VALUES (3, 1, 3, 1, 3, 7, 3, NULL, '6', '26.67', NULL, '160.0', '6', NULL, '160.0', '2026-04-23', '2026-04-23 11:06', NULL, NULL, NULL, NULL, 0, 3, NULL, NULL, NULL, NULL, NULL, '星期四', '04', '2026', '1776913577', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 8, 9, NULL, -1, 1);
COMMIT;

-- ----------------------------
-- Table structure for gb_department_goods_stock_record
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_stock_record`;
CREATE TABLE `gb_department_goods_stock_record` (
  `gb_department_goods_stock_record_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgsc_gb_department_id` int DEFAULT NULL,
  `gb_dgsc_gb_department_father_id` int DEFAULT NULL,
  `gb_dgsc_gb_distributer_id` int DEFAULT NULL,
  `gb_dgsc_gb_dis_goods_id` int DEFAULT NULL,
  `gb_dgsc_gb_dep_dis_goods_id` int DEFAULT NULL,
  `gb_dgsc_gb_department_order_id` int DEFAULT NULL,
  `gb_dgsc_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_rest_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_rest_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_receive_user_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_goods_stock_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_department_goods_stock_record
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_department_goods_stock_reduce
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_stock_reduce`;
CREATE TABLE `gb_department_goods_stock_reduce` (
  `gb_department_goods_stock_reduce_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgsr_gb_department_id` int DEFAULT NULL,
  `gb_dgsr_gb_department_father_id` int DEFAULT NULL,
  `gb_dgsr_gb_distributer_id` int DEFAULT NULL,
  `gb_dgsr_gb_dis_goods_id` int DEFAULT NULL,
  `gb_dgsr_gb_dep_dis_goods_id` int DEFAULT NULL,
  `gb_dgsr_gb_dis_goods_father_id` int DEFAULT NULL COMMENT '分销商商品父级ID',
  `gb_dgsr_gb_dis_goods_grand_id` int DEFAULT NULL COMMENT '分销商商品祖父级ID',
  `gb_dgsr_gb_dis_goods_great_id` int DEFAULT NULL COMMENT '分销商商品曾祖父级ID',
  `gb_dgsr_stock_nx_supplier_id` int DEFAULT NULL COMMENT '库存关联农鲜供应商ID',
  `gb_dgsr_status` int DEFAULT NULL COMMENT '状态',
  `gb_dgsr_stock_pur_user_id` int DEFAULT NULL COMMENT '库存采购人用户ID',
  `gb_dgsr_gb_pur_goods_id` int DEFAULT NULL COMMENT '关联采购商品ID',
  `gb_dgsr_gb_goods_stock_id` int DEFAULT NULL,
  `gb_dgsr_type` tinyint DEFAULT NULL COMMENT '1 出库 2 损耗 3 废弃 4 退货',
  `gb_dgsr_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_user_id` int DEFAULT NULL,
  `gb_dgsr_dep_settle_id` int DEFAULT NULL,
  `gb_dgsr_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_department_goods_stock_reduce_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_department_goods_stock_reduce
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_department_orders
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_orders`;
CREATE TABLE `gb_department_orders` (
  `gb_department_orders_id` int NOT NULL AUTO_INCREMENT COMMENT '部门订单id',
  `gb_DO_nx_goods_id` int DEFAULT NULL COMMENT '部门订单nx商品id',
  `gb_DO_nx_goods_father_id` int DEFAULT NULL COMMENT '部门订单商品父id',
  `gb_DO_dis_goods_id` int DEFAULT NULL COMMENT '部门订单社区商品id',
  `gb_DO_dis_goods_father_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_dep_dis_goods_id` int DEFAULT NULL,
  `gb_DO_quantity` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `gb_DO_standard` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `gb_DO_remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请备注',
  `gb_DO_weight` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `gb_DO_price` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单商品单价',
  `gb_DO_subtotal` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请商品小计',
  `gb_DO_department_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `gb_DO_department_father_id` int DEFAULT NULL,
  `gb_DO_distributer_id` int DEFAULT NULL COMMENT '部门订单批发商id',
  `gb_DO_purchase_user_id` int DEFAULT NULL COMMENT '部门商品采购员id',
  `gb_DO_bill_id` int DEFAULT NULL COMMENT '部门订单账单id',
  `gb_DO_status` tinyint DEFAULT NULL COMMENT '部门订单申请商品状态',
  `gb_DO_order_user_id` int DEFAULT NULL COMMENT '部门订单订货用户id',
  `gb_DO_pick_user_id` int DEFAULT NULL COMMENT '部门订单商品称重用户id',
  `gb_DO_receive_user_id` int DEFAULT NULL COMMENT '收货用户id',
  `gb_DO_buy_status` tinyint DEFAULT NULL COMMENT '部门订单商品进货状态',
  `gb_DO_purchase_goods_id` int DEFAULT NULL COMMENT '订单采购商品id',
  `gb_DO_apply_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `gb_DO_apply_what_day` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请星期',
  `gb_DO_apply_arrive_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `gb_DO_apply_full_time` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DO_apply_only_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DO_arrive_only_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DO_arrive_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单送达时间',
  `gb_DO_arrive_weeks_year` int DEFAULT NULL COMMENT '本年第几周',
  `gb_DO_arrive_what_day` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '星期几',
  `gb_DO_goods_type` tinyint DEFAULT NULL COMMENT '配送商品0，自采购商品1',
  `gb_DO_operation_time` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DO_is_agent` tinyint DEFAULT NULL,
  `gb_DO_cost_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '进货单价',
  `gb_DO_cost_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '进货重量',
  `gb_DO_cost_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '进货小计',
  `gb_DO_nx_distributer_id` int DEFAULT NULL COMMENT 'comGoods的批发商商品id',
  `gb_DO_nx_distributer_goods_id` int DEFAULT NULL COMMENT 'comGoods的批发商',
  `gb_DO_dg_goods_sell_type` tinyint DEFAULT NULL COMMENT '按规格销售方式',
  `gb_DO_nx_department_order_id` int DEFAULT NULL COMMENT 'nxDepartmentId',
  `gb_DO_to_department_id` int DEFAULT NULL COMMENT '库房或者中央厨房部门id',
  `gb_DO_order_type` tinyint DEFAULT NULL COMMENT '订单类型',
  `gb_DO_return_user_id` int DEFAULT NULL COMMENT '库房或者中央厨房部门id',
  `gb_DO_ds_standard_id` int DEFAULT NULL COMMENT '订货单位id',
  `gb_DO_ds_standard_scale` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货单位比例',
  `gb_DO_scale_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `gb_DO_scale_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `gb_DO_weight_total_id` int DEFAULT NULL COMMENT '拣货单id',
  `gb_DO_selling_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '零售单价',
  `gb_DO_selling_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '零售小计',
  `gb_DO_weight_goods_id` int DEFAULT NULL COMMENT 'id',
  `gb_DO_price_different` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT 'id',
  `gb_DO_dgsr_return_id` int DEFAULT NULL COMMENT '库房或者中央厨房部门id',
  `gb_DO_dis_goods_grand_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_nx_goods_grand_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_nx_goods_great_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_print_standard` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT 'id',
  `gb_DO_cost_price_level` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_goods_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '商品名称',
  `gb_DO_dis_goods_great_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  PRIMARY KEY (`gb_department_orders_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_department_orders
-- ----------------------------
BEGIN;
INSERT INTO `gb_department_orders` (`gb_department_orders_id`, `gb_DO_nx_goods_id`, `gb_DO_nx_goods_father_id`, `gb_DO_dis_goods_id`, `gb_DO_dis_goods_father_id`, `gb_DO_dep_dis_goods_id`, `gb_DO_quantity`, `gb_DO_standard`, `gb_DO_remark`, `gb_DO_weight`, `gb_DO_price`, `gb_DO_subtotal`, `gb_DO_department_id`, `gb_DO_department_father_id`, `gb_DO_distributer_id`, `gb_DO_purchase_user_id`, `gb_DO_bill_id`, `gb_DO_status`, `gb_DO_order_user_id`, `gb_DO_pick_user_id`, `gb_DO_receive_user_id`, `gb_DO_buy_status`, `gb_DO_purchase_goods_id`, `gb_DO_apply_date`, `gb_DO_apply_what_day`, `gb_DO_apply_arrive_date`, `gb_DO_apply_full_time`, `gb_DO_apply_only_time`, `gb_DO_arrive_only_date`, `gb_DO_arrive_date`, `gb_DO_arrive_weeks_year`, `gb_DO_arrive_what_day`, `gb_DO_goods_type`, `gb_DO_operation_time`, `gb_DO_is_agent`, `gb_DO_cost_price`, `gb_DO_cost_weight`, `gb_DO_cost_subtotal`, `gb_DO_nx_distributer_id`, `gb_DO_nx_distributer_goods_id`, `gb_DO_dg_goods_sell_type`, `gb_DO_nx_department_order_id`, `gb_DO_to_department_id`, `gb_DO_order_type`, `gb_DO_return_user_id`, `gb_DO_ds_standard_id`, `gb_DO_ds_standard_scale`, `gb_DO_scale_weight`, `gb_DO_scale_price`, `gb_DO_weight_total_id`, `gb_DO_selling_price`, `gb_DO_selling_subtotal`, `gb_DO_weight_goods_id`, `gb_DO_price_different`, `gb_DO_dgsr_return_id`, `gb_DO_dis_goods_grand_id`, `gb_DO_nx_goods_grand_id`, `gb_DO_nx_goods_great_id`, `gb_DO_print_standard`, `gb_DO_cost_price_level`, `gb_DO_goods_name`, `gb_DO_dis_goods_great_id`) VALUES (1, 100470, 10101, 1, 1, 1, '3', '颗', '', '3', '1', '3.0', 3, 1, 1, NULL, NULL, 4, 1, NULL, NULL, 6, 1, '2026-04-23', NULL, NULL, '2026-04-23 11:05', '11:05', '04-23', '2026-04-23', 17, '星期四', 2, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2, NULL, NULL, '-1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2, 101, 1, NULL, NULL, NULL, 3);
INSERT INTO `gb_department_orders` (`gb_department_orders_id`, `gb_DO_nx_goods_id`, `gb_DO_nx_goods_father_id`, `gb_DO_dis_goods_id`, `gb_DO_dis_goods_father_id`, `gb_DO_dep_dis_goods_id`, `gb_DO_quantity`, `gb_DO_standard`, `gb_DO_remark`, `gb_DO_weight`, `gb_DO_price`, `gb_DO_subtotal`, `gb_DO_department_id`, `gb_DO_department_father_id`, `gb_DO_distributer_id`, `gb_DO_purchase_user_id`, `gb_DO_bill_id`, `gb_DO_status`, `gb_DO_order_user_id`, `gb_DO_pick_user_id`, `gb_DO_receive_user_id`, `gb_DO_buy_status`, `gb_DO_purchase_goods_id`, `gb_DO_apply_date`, `gb_DO_apply_what_day`, `gb_DO_apply_arrive_date`, `gb_DO_apply_full_time`, `gb_DO_apply_only_time`, `gb_DO_arrive_only_date`, `gb_DO_arrive_date`, `gb_DO_arrive_weeks_year`, `gb_DO_arrive_what_day`, `gb_DO_goods_type`, `gb_DO_operation_time`, `gb_DO_is_agent`, `gb_DO_cost_price`, `gb_DO_cost_weight`, `gb_DO_cost_subtotal`, `gb_DO_nx_distributer_id`, `gb_DO_nx_distributer_goods_id`, `gb_DO_dg_goods_sell_type`, `gb_DO_nx_department_order_id`, `gb_DO_to_department_id`, `gb_DO_order_type`, `gb_DO_return_user_id`, `gb_DO_ds_standard_id`, `gb_DO_ds_standard_scale`, `gb_DO_scale_weight`, `gb_DO_scale_price`, `gb_DO_weight_total_id`, `gb_DO_selling_price`, `gb_DO_selling_subtotal`, `gb_DO_weight_goods_id`, `gb_DO_price_different`, `gb_DO_dgsr_return_id`, `gb_DO_dis_goods_grand_id`, `gb_DO_nx_goods_grand_id`, `gb_DO_nx_goods_great_id`, `gb_DO_print_standard`, `gb_DO_cost_price_level`, `gb_DO_goods_name`, `gb_DO_dis_goods_great_id`) VALUES (2, 100187, 10281, 2, 4, 2, '2', '斤', '', '5', '12', '60.0', 3, 1, 1, NULL, NULL, 4, 1, NULL, NULL, 6, 2, '2026-04-23', NULL, NULL, '2026-04-23 11:05', '11:05', '04-23', '2026-04-23', 17, '星期四', 2, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2, NULL, NULL, '-1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 5, 201, 2, NULL, NULL, NULL, 6);
INSERT INTO `gb_department_orders` (`gb_department_orders_id`, `gb_DO_nx_goods_id`, `gb_DO_nx_goods_father_id`, `gb_DO_dis_goods_id`, `gb_DO_dis_goods_father_id`, `gb_DO_dep_dis_goods_id`, `gb_DO_quantity`, `gb_DO_standard`, `gb_DO_remark`, `gb_DO_weight`, `gb_DO_price`, `gb_DO_subtotal`, `gb_DO_department_id`, `gb_DO_department_father_id`, `gb_DO_distributer_id`, `gb_DO_purchase_user_id`, `gb_DO_bill_id`, `gb_DO_status`, `gb_DO_order_user_id`, `gb_DO_pick_user_id`, `gb_DO_receive_user_id`, `gb_DO_buy_status`, `gb_DO_purchase_goods_id`, `gb_DO_apply_date`, `gb_DO_apply_what_day`, `gb_DO_apply_arrive_date`, `gb_DO_apply_full_time`, `gb_DO_apply_only_time`, `gb_DO_arrive_only_date`, `gb_DO_arrive_date`, `gb_DO_arrive_weeks_year`, `gb_DO_arrive_what_day`, `gb_DO_goods_type`, `gb_DO_operation_time`, `gb_DO_is_agent`, `gb_DO_cost_price`, `gb_DO_cost_weight`, `gb_DO_cost_subtotal`, `gb_DO_nx_distributer_id`, `gb_DO_nx_distributer_goods_id`, `gb_DO_dg_goods_sell_type`, `gb_DO_nx_department_order_id`, `gb_DO_to_department_id`, `gb_DO_order_type`, `gb_DO_return_user_id`, `gb_DO_ds_standard_id`, `gb_DO_ds_standard_scale`, `gb_DO_scale_weight`, `gb_DO_scale_price`, `gb_DO_weight_total_id`, `gb_DO_selling_price`, `gb_DO_selling_subtotal`, `gb_DO_weight_goods_id`, `gb_DO_price_different`, `gb_DO_dgsr_return_id`, `gb_DO_dis_goods_grand_id`, `gb_DO_nx_goods_grand_id`, `gb_DO_nx_goods_great_id`, `gb_DO_print_standard`, `gb_DO_cost_price_level`, `gb_DO_goods_name`, `gb_DO_dis_goods_great_id`) VALUES (3, 105267, 10395, 3, 7, 3, '1', '箱', '', '6', '26.67', '160.0', 3, 1, 1, NULL, NULL, 4, 1, NULL, NULL, 6, 3, '2026-04-23', NULL, NULL, '2026-04-23 11:05', '11:05', '04-23', '2026-04-23', 17, '星期四', 2, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2, NULL, NULL, '6', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 8, 301, 3, NULL, NULL, NULL, 9);
COMMIT;

-- ----------------------------
-- Table structure for gb_department_orders_history
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_orders_history`;
CREATE TABLE `gb_department_orders_history` (
  `gb_department_orders_history_id` int NOT NULL AUTO_INCREMENT,
  `gb_doh_department_id` int DEFAULT NULL,
  `gb_doh_department_father_id` int DEFAULT NULL,
  `gb_doh_distributer_id` int DEFAULT NULL,
  `gb_doh_orders_id` int DEFAULT NULL,
  `gb_doh_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_department_orders_history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_department_orders_history
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_department_user
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_user`;
CREATE TABLE `gb_department_user` (
  `gb_department_user_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门用户id',
  `gb_DU_department_id` int DEFAULT NULL COMMENT '订货部门id',
  `gb_DU_wx_avartra_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门用户微信头像',
  `gb_DU_wx_nick_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门用户微信昵称',
  `gb_DU_wx_open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门用户微信openid',
  `gb_DU_wx_phone` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门用户微信手机号码',
  `gb_DU_admin` tinyint DEFAULT NULL COMMENT '订货部门用户是否是管理员',
  `gb_DU_distributer_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `gb_DU_url_change` tinyint DEFAULT NULL,
  `gb_DU_department_father_id` int DEFAULT NULL,
  `gb_DU_join_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DU_print_device_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DU_print_bill_device_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DU_customer_service` int DEFAULT NULL,
  `gb_DU_login_times` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_department_user
-- ----------------------------
BEGIN;
INSERT INTO `gb_department_user` (`gb_department_user_id`, `gb_DU_department_id`, `gb_DU_wx_avartra_url`, `gb_DU_wx_nick_name`, `gb_DU_wx_open_id`, `gb_DU_wx_phone`, `gb_DU_admin`, `gb_DU_distributer_id`, `gb_DU_url_change`, `gb_DU_department_father_id`, `gb_DU_join_date`, `gb_DU_print_device_id`, `gb_DU_print_bill_device_id`, `gb_DU_customer_service`, `gb_DU_login_times`) VALUES (1, 1, 'uploadImage/CjjxkShc6h3j041def69af3a58b00999d98c9b8dc5be.jpeg', '冲冲冲管理员', 'o85GY5bUj3f1lS5-tK1eFOMb5uZ8', '1', 11, 1, 1, 1, '2026-04-16', '-1', '-1', NULL, 0);
INSERT INTO `gb_department_user` (`gb_department_user_id`, `gb_DU_department_id`, `gb_DU_wx_avartra_url`, `gb_DU_wx_nick_name`, `gb_DU_wx_open_id`, `gb_DU_wx_phone`, `gb_DU_admin`, `gb_DU_distributer_id`, `gb_DU_url_change`, `gb_DU_department_father_id`, `gb_DU_join_date`, `gb_DU_print_device_id`, `gb_DU_print_bill_device_id`, `gb_DU_customer_service`, `gb_DU_login_times`) VALUES (2, 1, 'uploadImage/tmp_dc170fbf7a959a91029df685b76a57235325f1e100eff1e270919148c2295e5e.jpeg', '不是管理员', 'o85GY5duOa9M8wAmz05Is5CCaOpo', '1', 11, 1, 1, 1, '2026-04-18', '-1', '-1', NULL, 0);
INSERT INTO `gb_department_user` (`gb_department_user_id`, `gb_DU_department_id`, `gb_DU_wx_avartra_url`, `gb_DU_wx_nick_name`, `gb_DU_wx_open_id`, `gb_DU_wx_phone`, `gb_DU_admin`, `gb_DU_distributer_id`, `gb_DU_url_change`, `gb_DU_department_father_id`, `gb_DU_join_date`, `gb_DU_print_device_id`, `gb_DU_print_bill_device_id`, `gb_DU_customer_service`, `gb_DU_login_times`) VALUES (7, 3, 'uploadImage/kmLoTcI7zZUJa04adc94ead3921d391af1c31a108df1.jpeg', '李沛谊', 'o85GY5bUj3f1lS5-tK1eFOMb5uZ8==', NULL, 1, 1, 1, 1, '2026-04-19', '-1', '-1', NULL, 0);
COMMIT;

-- ----------------------------
-- Table structure for gb_dis_nx_dis
-- ----------------------------
DROP TABLE IF EXISTS `gb_dis_nx_dis`;
CREATE TABLE `gb_dis_nx_dis` (
  `gb_dis_nx_dis_id` int NOT NULL AUTO_INCREMENT,
  `gb_dnd_gb_dis_id` int DEFAULT NULL,
  `gb_dnd_nx_dis_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_dis_nx_dis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_dis_nx_dis
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer`;
CREATE TABLE `gb_distributer` (
  `gb_distributer_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商id',
  `gb_distributer_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_lan` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商位置经度',
  `gb_distributer_lun` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商位置纬度',
  `gb_distributer_business_type` int DEFAULT NULL COMMENT '批发商商业类型',
  `gb_distributer_manager` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_distributer_phone` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_distributer_address` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_distributer_img` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_distributer_settle_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_distributer_settle_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_distributer_settle_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算周',
  `gb_distributer_settle_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_distributer_settle_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_distributer_settle_times` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_distributer_time_quantum` tinyint DEFAULT NULL COMMENT '经营时间段',
  `gb_distributer_print_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_buy_quantity` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_distributer_sys_city_id` tinyint DEFAULT NULL COMMENT '批发商商业类型',
  `gb_distributer_nx_dis_id` tinyint DEFAULT NULL COMMENT '批发商商业类型',
  `gb_distributer_pick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_record_seconds` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_stock_cycle` tinyint DEFAULT NULL COMMENT '库存显示周期',
  PRIMARY KEY (`gb_distributer_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_distributer
-- ----------------------------
BEGIN;
INSERT INTO `gb_distributer` (`gb_distributer_id`, `gb_distributer_name`, `gb_distributer_lan`, `gb_distributer_lun`, `gb_distributer_business_type`, `gb_distributer_manager`, `gb_distributer_phone`, `gb_distributer_address`, `gb_distributer_img`, `gb_distributer_settle_full_time`, `gb_distributer_settle_date`, `gb_distributer_settle_week`, `gb_distributer_settle_month`, `gb_distributer_settle_year`, `gb_distributer_settle_times`, `gb_distributer_time_quantum`, `gb_distributer_print_name`, `gb_distributer_buy_quantity`, `gb_distributer_sys_city_id`, `gb_distributer_nx_dis_id`, `gb_distributer_pick_name`, `gb_distributer_record_seconds`, `gb_distributer_stock_cycle`) VALUES (1, '冲冲冲', NULL, NULL, -1, '09:00', '133', '', NULL, '2026-04-16 14:19', '2026-04-16', '16', '04', '2026', '0', NULL, 'ApplyHalfPanel', '7', 6, -1, NULL, '30', 1);
INSERT INTO `gb_distributer` (`gb_distributer_id`, `gb_distributer_name`, `gb_distributer_lan`, `gb_distributer_lun`, `gb_distributer_business_type`, `gb_distributer_manager`, `gb_distributer_phone`, `gb_distributer_address`, `gb_distributer_img`, `gb_distributer_settle_full_time`, `gb_distributer_settle_date`, `gb_distributer_settle_week`, `gb_distributer_settle_month`, `gb_distributer_settle_year`, `gb_distributer_settle_times`, `gb_distributer_time_quantum`, `gb_distributer_print_name`, `gb_distributer_buy_quantity`, `gb_distributer_sys_city_id`, `gb_distributer_nx_dis_id`, `gb_distributer_pick_name`, `gb_distributer_record_seconds`, `gb_distributer_stock_cycle`) VALUES (2, '不是', NULL, NULL, -1, '09:00', '1', '', NULL, '2026-04-18 19:50', '2026-04-18', '16', '04', '2026', '0', NULL, 'ApplyHalfPanel', '10', 6, -1, NULL, '30', 0);
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_alias
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_alias`;
CREATE TABLE `gb_distributer_alias` (
  `gb_distributer_alias_id` int NOT NULL AUTO_INCREMENT,
  `gb_DA_dis_goods_id` int DEFAULT NULL,
  `gb_DA_alias_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DA_gb_alias_id` int DEFAULT NULL,
  `gb_DA_alias_pinyin` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DA_alias_py` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_alias_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_distributer_alias
-- ----------------------------
BEGIN;
INSERT INTO `gb_distributer_alias` (`gb_distributer_alias_id`, `gb_DA_dis_goods_id`, `gb_DA_alias_name`, `gb_DA_gb_alias_id`, `gb_DA_alias_pinyin`, `gb_DA_alias_py`) VALUES (1, 1, '包菜', NULL, NULL, NULL);
INSERT INTO `gb_distributer_alias` (`gb_distributer_alias_id`, `gb_DA_dis_goods_id`, `gb_DA_alias_name`, `gb_DA_gb_alias_id`, `gb_DA_alias_pinyin`, `gb_DA_alias_py`) VALUES (2, 2, '猪肉', NULL, NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_father_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_father_goods`;
CREATE TABLE `gb_distributer_father_goods` (
  `gb_distributer_father_goods_id` int NOT NULL AUTO_INCREMENT,
  `gb_dfg_father_goods_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dfg_father_goods_img` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dfg_father_goods_sort` int DEFAULT NULL,
  `gb_dfg_father_goods_color` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dfg_fathers_father_id` int DEFAULT NULL,
  `gb_dfg_father_goods_level` tinyint DEFAULT NULL,
  `gb_dfg_distributer_id` int DEFAULT NULL,
  `gb_dfg_goods_amount` int DEFAULT NULL,
  `gb_dfg_nx_goods_id` int DEFAULT NULL,
  `gb_dfg_price_amount` int DEFAULT NULL,
  `gb_dfg_price_two_amount` int DEFAULT NULL,
  `gb_dfg_price_three_amount` int DEFAULT NULL,
  `gb_dfg_father_goods_img_large` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_father_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_distributer_father_goods
-- ----------------------------
BEGIN;
INSERT INTO `gb_distributer_father_goods` (`gb_distributer_father_goods_id`, `gb_dfg_father_goods_name`, `gb_dfg_father_goods_img`, `gb_dfg_father_goods_sort`, `gb_dfg_father_goods_color`, `gb_dfg_fathers_father_id`, `gb_dfg_father_goods_level`, `gb_dfg_distributer_id`, `gb_dfg_goods_amount`, `gb_dfg_nx_goods_id`, `gb_dfg_price_amount`, `gb_dfg_price_two_amount`, `gb_dfg_price_three_amount`, `gb_dfg_father_goods_img_large`) VALUES (1, '圆白菜', 'goodsImage/圆白菜2024-11-12 14:32:29.jpg', 1, NULL, 2, 2, 1, 1, 10101, 0, 0, 0, 'goodsImage/圆白菜2024-11-12 14:32:29large.jpg');
INSERT INTO `gb_distributer_father_goods` (`gb_distributer_father_goods_id`, `gb_dfg_father_goods_name`, `gb_dfg_father_goods_img`, `gb_dfg_father_goods_sort`, `gb_dfg_father_goods_color`, `gb_dfg_fathers_father_id`, `gb_dfg_father_goods_level`, `gb_dfg_distributer_id`, `gb_dfg_goods_amount`, `gb_dfg_nx_goods_id`, `gb_dfg_price_amount`, `gb_dfg_price_two_amount`, `gb_dfg_price_three_amount`, `gb_dfg_father_goods_img_large`) VALUES (2, '叶花菜', 'goodsImage/叶花菜2024-10-21 17:10:26.jpg', 1, NULL, 3, 1, 1, 1, 101, NULL, NULL, NULL, NULL);
INSERT INTO `gb_distributer_father_goods` (`gb_distributer_father_goods_id`, `gb_dfg_father_goods_name`, `gb_dfg_father_goods_img`, `gb_dfg_father_goods_sort`, `gb_dfg_father_goods_color`, `gb_dfg_fathers_father_id`, `gb_dfg_father_goods_level`, `gb_dfg_distributer_id`, `gb_dfg_goods_amount`, `gb_dfg_nx_goods_id`, `gb_dfg_price_amount`, `gb_dfg_price_two_amount`, `gb_dfg_price_three_amount`, `gb_dfg_father_goods_img_large`) VALUES (3, '新鲜蔬菜', 'goodsImage/新鲜蔬菜2024-09-23 21:46:19.jpg', 1, NULL, 0, 0, 1, 1, 1, NULL, NULL, NULL, NULL);
INSERT INTO `gb_distributer_father_goods` (`gb_distributer_father_goods_id`, `gb_dfg_father_goods_name`, `gb_dfg_father_goods_img`, `gb_dfg_father_goods_sort`, `gb_dfg_father_goods_color`, `gb_dfg_fathers_father_id`, `gb_dfg_father_goods_level`, `gb_dfg_distributer_id`, `gb_dfg_goods_amount`, `gb_dfg_nx_goods_id`, `gb_dfg_price_amount`, `gb_dfg_price_two_amount`, `gb_dfg_price_three_amount`, `gb_dfg_father_goods_img_large`) VALUES (4, '猪肉后臀尖', 'goodsImage/鲜猪后臀尖2025-06-23 21:20:33.jpg', 1, NULL, 5, 2, 1, 1, 10281, 0, 0, 0, 'goodsImage/鲜猪后臀尖2025-06-23 21:20:33large.jpg');
INSERT INTO `gb_distributer_father_goods` (`gb_distributer_father_goods_id`, `gb_dfg_father_goods_name`, `gb_dfg_father_goods_img`, `gb_dfg_father_goods_sort`, `gb_dfg_father_goods_color`, `gb_dfg_fathers_father_id`, `gb_dfg_father_goods_level`, `gb_dfg_distributer_id`, `gb_dfg_goods_amount`, `gb_dfg_nx_goods_id`, `gb_dfg_price_amount`, `gb_dfg_price_two_amount`, `gb_dfg_price_three_amount`, `gb_dfg_father_goods_img_large`) VALUES (5, '鲜猪肉', 'goodsImage/鲜猪肉2023-10-09 23:12:42.jpg', 1, NULL, 6, 1, 1, 1, 201, NULL, NULL, NULL, NULL);
INSERT INTO `gb_distributer_father_goods` (`gb_distributer_father_goods_id`, `gb_dfg_father_goods_name`, `gb_dfg_father_goods_img`, `gb_dfg_father_goods_sort`, `gb_dfg_father_goods_color`, `gb_dfg_fathers_father_id`, `gb_dfg_father_goods_level`, `gb_dfg_distributer_id`, `gb_dfg_goods_amount`, `gb_dfg_nx_goods_id`, `gb_dfg_price_amount`, `gb_dfg_price_two_amount`, `gb_dfg_price_three_amount`, `gb_dfg_father_goods_img_large`) VALUES (6, '鲜肉禽蛋', 'goodsImage/鲜肉禽蛋2024-09-23 21:45:55.jpg', 2, NULL, 0, 0, 1, 1, 2, NULL, NULL, NULL, NULL);
INSERT INTO `gb_distributer_father_goods` (`gb_distributer_father_goods_id`, `gb_dfg_father_goods_name`, `gb_dfg_father_goods_img`, `gb_dfg_father_goods_sort`, `gb_dfg_father_goods_color`, `gb_dfg_fathers_father_id`, `gb_dfg_father_goods_level`, `gb_dfg_distributer_id`, `gb_dfg_goods_amount`, `gb_dfg_nx_goods_id`, `gb_dfg_price_amount`, `gb_dfg_price_two_amount`, `gb_dfg_price_three_amount`, `gb_dfg_father_goods_img_large`) VALUES (7, '海天系列', 'goodsImage/haitian5dubaicu_105267_20260422222203.jpg', 1, NULL, 8, 2, 1, 1, 10395, 0, 0, 0, 'goodsImage/haitian5dubaicu_105267_20260422222203large.jpg');
INSERT INTO `gb_distributer_father_goods` (`gb_distributer_father_goods_id`, `gb_dfg_father_goods_name`, `gb_dfg_father_goods_img`, `gb_dfg_father_goods_sort`, `gb_dfg_father_goods_color`, `gb_dfg_fathers_father_id`, `gb_dfg_father_goods_level`, `gb_dfg_distributer_id`, `gb_dfg_goods_amount`, `gb_dfg_nx_goods_id`, `gb_dfg_price_amount`, `gb_dfg_price_two_amount`, `gb_dfg_price_three_amount`, `gb_dfg_father_goods_img_large`) VALUES (8, '酱油醋', 'goodsImage/酱油醋2023-10-09 23:17:10.jpg', 1, NULL, 9, 1, 1, 1, 301, NULL, NULL, NULL, NULL);
INSERT INTO `gb_distributer_father_goods` (`gb_distributer_father_goods_id`, `gb_dfg_father_goods_name`, `gb_dfg_father_goods_img`, `gb_dfg_father_goods_sort`, `gb_dfg_father_goods_color`, `gb_dfg_fathers_father_id`, `gb_dfg_father_goods_level`, `gb_dfg_distributer_id`, `gb_dfg_goods_amount`, `gb_dfg_nx_goods_id`, `gb_dfg_price_amount`, `gb_dfg_price_two_amount`, `gb_dfg_price_three_amount`, `gb_dfg_father_goods_img_large`) VALUES (9, '调料干货', 'goodsImage/调料干货2024-09-23 21:46:32.jpg', 3, NULL, 0, 0, 1, 1, 3, NULL, NULL, NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_food
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_food`;
CREATE TABLE `gb_distributer_food` (
  `gb_distributer_food_id` int NOT NULL AUTO_INCREMENT COMMENT '菜品id',
  `gb_df_distributer_id` int DEFAULT NULL COMMENT '所属配送商id',
  `gb_df_nx_food_id` int DEFAULT NULL COMMENT '老系统食品id',
  `gb_df_food_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品名称',
  `gb_df_food_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品价格',
  `gb_df_status` tinyint DEFAULT NULL COMMENT '状态',
  `gb_df_food_pinyin` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品拼音',
  `gb_df_food_py` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品拼音简拼',
  `gb_df_food_father_id` int DEFAULT NULL COMMENT '菜品分类父id',
  `gb_df_food_img` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品图片',
  `gb_df_food_img_large` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品大图',
  `gb_df_food_method` varchar(400) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作方法',
  `gb_df_food_detail` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品详情',
  `gb_df_goods_sort` int DEFAULT NULL COMMENT '排序',
  PRIMARY KEY (`gb_distributer_food_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Records of gb_distributer_food
-- ----------------------------
BEGIN;
INSERT INTO `gb_distributer_food` (`gb_distributer_food_id`, `gb_df_distributer_id`, `gb_df_nx_food_id`, `gb_df_food_name`, `gb_df_food_price`, `gb_df_status`, `gb_df_food_pinyin`, `gb_df_food_py`, `gb_df_food_father_id`, `gb_df_food_img`, `gb_df_food_img_large`, `gb_df_food_method`, `gb_df_food_detail`, `gb_df_goods_sort`) VALUES (1, 1, NULL, '热菜', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `gb_distributer_food` (`gb_distributer_food_id`, `gb_df_distributer_id`, `gb_df_nx_food_id`, `gb_df_food_name`, `gb_df_food_price`, `gb_df_status`, `gb_df_food_pinyin`, `gb_df_food_py`, `gb_df_food_father_id`, `gb_df_food_img`, `gb_df_food_img_large`, `gb_df_food_method`, `gb_df_food_detail`, `gb_df_goods_sort`) VALUES (2, 1, NULL, '红烧肉', '20', NULL, NULL, NULL, 1, NULL, NULL, '', NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_food_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_food_goods`;
CREATE TABLE `gb_distributer_food_goods` (
  `gb_distributer_food_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '菜品原料id',
  `gb_dfg_dis_id` int DEFAULT NULL COMMENT '所属配送商id',
  `gb_dfg_food_id` int DEFAULT NULL COMMENT '菜品id',
  `gb_dfg_dis_goods_id` int DEFAULT NULL COMMENT '原料商品id',
  `gb_dfg_goods_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '原料数量',
  `gb_dfg_goods_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '原料名称',
  `gb_dfg_goods_standardname` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '原料规格',
  `gb_dfg_status` tinyint DEFAULT NULL COMMENT '状态',
  PRIMARY KEY (`gb_distributer_food_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Records of gb_distributer_food_goods
-- ----------------------------
BEGIN;
INSERT INTO `gb_distributer_food_goods` (`gb_distributer_food_goods_id`, `gb_dfg_dis_id`, `gb_dfg_food_id`, `gb_dfg_dis_goods_id`, `gb_dfg_goods_amount`, `gb_dfg_goods_name`, `gb_dfg_goods_standardname`, `gb_dfg_status`) VALUES (1, 1, 2, 2, '0.5', '猪肉后臀尖', '斤', 1);
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_goods`;
CREATE TABLE `gb_distributer_goods` (
  `gb_distributer_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '社区商品id',
  `gb_dg_dfg_goods_father_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `gb_dg_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_dg_goods_status` tinyint DEFAULT NULL COMMENT '商品状态',
  `gb_dg_goods_is_weight` tinyint DEFAULT NULL COMMENT '是否称重',
  `gb_dg_goods_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '商品名称',
  `gb_dg_goods_detail` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '商品详细',
  `gb_dg_goods_standardname` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '商品规格',
  `gb_dg_goods_pinyin` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '社区商品拼音',
  `gb_dg_goods_py` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '社区商品拼音简拼',
  `gb_dg_nx_goods_id` int DEFAULT NULL COMMENT 'nxGoodsId',
  `gb_dg_nx_father_img` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '进货方式',
  `gb_dg_nx_father_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT 'nxGoodsFatherId',
  `gb_dg_nx_grand_id` int DEFAULT NULL COMMENT 'nxGoodsGrandid',
  `gb_dg_nx_great_grand_id` int DEFAULT NULL COMMENT 'nxGreatGrandid',
  `gb_dg_pull_off` tinyint DEFAULT NULL COMMENT '是否下架',
  `gb_dg_goods_brand` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_goods_place` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_nx_goods_father_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_goods_standard_weight` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_goods_type` tinyint DEFAULT NULL COMMENT '1 集采 2出库 3 自采',
  `gb_dg_goods_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '价格',
  `gb_dg_goods_lowest_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_goods_highest_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_nx_distributer_id` int DEFAULT NULL COMMENT '商品库父类id',
  `gb_dg_nx_distributer_goods_id` int DEFAULT NULL,
  `gb_dg_gb_department_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_dg_control_price` tinyint DEFAULT NULL COMMENT '是否控制价格',
  `gb_dg_control_fresh` tinyint DEFAULT NULL COMMENT '是否控制鲜度',
  `gb_dg_fresh_warn_hour` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '鲜度报警小时',
  `gb_dg_fresh_waste_hour` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_dg_goods_inventory_type` tinyint DEFAULT NULL COMMENT '盘库方式1 月，2周，3日',
  `gb_dg_gb_supplier_id` int DEFAULT NULL COMMENT '指定供货商id',
  `gb_dg_franchise_price_one` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_two` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_three` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_one_update` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_two_update` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_three_update` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_is_franchise_price` int DEFAULT NULL COMMENT '加盟商商品',
  `gb_dg_is_self_control` int DEFAULT NULL COMMENT '自制商品',
  `gb_dg_self_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '自制价格',
  `gb_dg_selling_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '销售价格',
  `gb_dg_goods_sort` int DEFAULT NULL COMMENT '商品状态',
  `gb_dg_goods_sons_sort` int DEFAULT NULL COMMENT '子商品顺序',
  `gb_dg_goods_is_hidden` int DEFAULT NULL COMMENT '是否显示',
  `gb_dg_nx_father_img_large` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '进货方式',
  `gb_dg_nx_distributer_goods_price` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_dfg_goods_grand_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `gb_dg_dfg_goods_great_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `gb_dg_quantity_days` int DEFAULT NULL COMMENT '批发商父类商品id',
  PRIMARY KEY (`gb_distributer_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_distributer_goods
-- ----------------------------
BEGIN;
INSERT INTO `gb_distributer_goods` (`gb_distributer_goods_id`, `gb_dg_dfg_goods_father_id`, `gb_dg_distributer_id`, `gb_dg_goods_status`, `gb_dg_goods_is_weight`, `gb_dg_goods_name`, `gb_dg_goods_detail`, `gb_dg_goods_standardname`, `gb_dg_goods_pinyin`, `gb_dg_goods_py`, `gb_dg_nx_goods_id`, `gb_dg_nx_father_img`, `gb_dg_nx_father_id`, `gb_dg_nx_grand_id`, `gb_dg_nx_great_grand_id`, `gb_dg_pull_off`, `gb_dg_goods_brand`, `gb_dg_goods_place`, `gb_dg_nx_goods_father_color`, `gb_dg_goods_standard_weight`, `gb_dg_goods_type`, `gb_dg_goods_price`, `gb_dg_goods_lowest_price`, `gb_dg_goods_highest_price`, `gb_dg_nx_distributer_id`, `gb_dg_nx_distributer_goods_id`, `gb_dg_gb_department_id`, `gb_dg_control_price`, `gb_dg_control_fresh`, `gb_dg_fresh_warn_hour`, `gb_dg_fresh_waste_hour`, `gb_dg_goods_inventory_type`, `gb_dg_gb_supplier_id`, `gb_dg_franchise_price_one`, `gb_dg_franchise_price_two`, `gb_dg_franchise_price_three`, `gb_dg_franchise_price_one_update`, `gb_dg_franchise_price_two_update`, `gb_dg_franchise_price_three_update`, `gb_dg_is_franchise_price`, `gb_dg_is_self_control`, `gb_dg_self_price`, `gb_dg_selling_price`, `gb_dg_goods_sort`, `gb_dg_goods_sons_sort`, `gb_dg_goods_is_hidden`, `gb_dg_nx_father_img_large`, `gb_dg_nx_distributer_goods_price`, `gb_dg_dfg_goods_grand_id`, `gb_dg_dfg_goods_great_id`, `gb_dg_quantity_days`) VALUES (1, 1, 1, 1, 0, '圆白菜', NULL, '斤', 'yuanbaicai', 'ybc', 100470, 'goodsImage/圆白菜2024-11-12 14:32:29.jpg', '10101', 101, 1, 0, NULL, NULL, NULL, NULL, 2, NULL, NULL, NULL, -1, -1, NULL, 0, 0, NULL, NULL, 1, -1, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, NULL, NULL, 1, 1, 0, 'goodsImage/圆白菜2024-11-12 14:32:29large.jpg', '0.1', 2, 3, 1);
INSERT INTO `gb_distributer_goods` (`gb_distributer_goods_id`, `gb_dg_dfg_goods_father_id`, `gb_dg_distributer_id`, `gb_dg_goods_status`, `gb_dg_goods_is_weight`, `gb_dg_goods_name`, `gb_dg_goods_detail`, `gb_dg_goods_standardname`, `gb_dg_goods_pinyin`, `gb_dg_goods_py`, `gb_dg_nx_goods_id`, `gb_dg_nx_father_img`, `gb_dg_nx_father_id`, `gb_dg_nx_grand_id`, `gb_dg_nx_great_grand_id`, `gb_dg_pull_off`, `gb_dg_goods_brand`, `gb_dg_goods_place`, `gb_dg_nx_goods_father_color`, `gb_dg_goods_standard_weight`, `gb_dg_goods_type`, `gb_dg_goods_price`, `gb_dg_goods_lowest_price`, `gb_dg_goods_highest_price`, `gb_dg_nx_distributer_id`, `gb_dg_nx_distributer_goods_id`, `gb_dg_gb_department_id`, `gb_dg_control_price`, `gb_dg_control_fresh`, `gb_dg_fresh_warn_hour`, `gb_dg_fresh_waste_hour`, `gb_dg_goods_inventory_type`, `gb_dg_gb_supplier_id`, `gb_dg_franchise_price_one`, `gb_dg_franchise_price_two`, `gb_dg_franchise_price_three`, `gb_dg_franchise_price_one_update`, `gb_dg_franchise_price_two_update`, `gb_dg_franchise_price_three_update`, `gb_dg_is_franchise_price`, `gb_dg_is_self_control`, `gb_dg_self_price`, `gb_dg_selling_price`, `gb_dg_goods_sort`, `gb_dg_goods_sons_sort`, `gb_dg_goods_is_hidden`, `gb_dg_nx_father_img_large`, `gb_dg_nx_distributer_goods_price`, `gb_dg_dfg_goods_grand_id`, `gb_dg_dfg_goods_great_id`, `gb_dg_quantity_days`) VALUES (2, 4, 1, 1, 0, '猪肉后臀尖', '', '斤', 'zhurouhoutunjian', 'zrhtj', 100187, 'goodsImage/鲜猪后臀尖2025-06-23 21:20:33.jpg', '10281', 201, 2, 0, '', '', NULL, '', 2, NULL, NULL, NULL, -1, -1, NULL, 0, 0, NULL, NULL, 1, -1, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, NULL, NULL, 1, 0, 0, 'goodsImage/鲜猪后臀尖2025-06-23 21:20:33large.jpg', '0.1', 5, 6, 2);
INSERT INTO `gb_distributer_goods` (`gb_distributer_goods_id`, `gb_dg_dfg_goods_father_id`, `gb_dg_distributer_id`, `gb_dg_goods_status`, `gb_dg_goods_is_weight`, `gb_dg_goods_name`, `gb_dg_goods_detail`, `gb_dg_goods_standardname`, `gb_dg_goods_pinyin`, `gb_dg_goods_py`, `gb_dg_nx_goods_id`, `gb_dg_nx_father_img`, `gb_dg_nx_father_id`, `gb_dg_nx_grand_id`, `gb_dg_nx_great_grand_id`, `gb_dg_pull_off`, `gb_dg_goods_brand`, `gb_dg_goods_place`, `gb_dg_nx_goods_father_color`, `gb_dg_goods_standard_weight`, `gb_dg_goods_type`, `gb_dg_goods_price`, `gb_dg_goods_lowest_price`, `gb_dg_goods_highest_price`, `gb_dg_nx_distributer_id`, `gb_dg_nx_distributer_goods_id`, `gb_dg_gb_department_id`, `gb_dg_control_price`, `gb_dg_control_fresh`, `gb_dg_fresh_warn_hour`, `gb_dg_fresh_waste_hour`, `gb_dg_goods_inventory_type`, `gb_dg_gb_supplier_id`, `gb_dg_franchise_price_one`, `gb_dg_franchise_price_two`, `gb_dg_franchise_price_three`, `gb_dg_franchise_price_one_update`, `gb_dg_franchise_price_two_update`, `gb_dg_franchise_price_three_update`, `gb_dg_is_franchise_price`, `gb_dg_is_self_control`, `gb_dg_self_price`, `gb_dg_selling_price`, `gb_dg_goods_sort`, `gb_dg_goods_sons_sort`, `gb_dg_goods_is_hidden`, `gb_dg_nx_father_img_large`, `gb_dg_nx_distributer_goods_price`, `gb_dg_dfg_goods_grand_id`, `gb_dg_dfg_goods_great_id`, `gb_dg_quantity_days`) VALUES (3, 7, 1, 1, 0, '海天5度白醋', NULL, '桶', 'haitian5dubaicu', 'ht5dbc', 105267, 'goodsImage/haitian5dubaicu_105267_20260422222203.jpg', '10395', 301, 3, 0, NULL, NULL, NULL, '1.9L', 2, NULL, NULL, NULL, -1, -1, NULL, 0, 0, NULL, NULL, 1, -1, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, NULL, NULL, 1, 2, 0, 'goodsImage/haitian5dubaicu_105267_20260422222203large.jpg', '0.1', 8, 9, NULL);
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_goods_price
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_goods_price`;
CREATE TABLE `gb_distributer_goods_price` (
  `gb_distributer_goods_price_id` int NOT NULL AUTO_INCREMENT COMMENT '价格商品id',
  `gb_dgp_dfg_goods_father_id` int DEFAULT NULL COMMENT 'dg父类商品id',
  `gb_dgp_distributer_goods_id` int DEFAULT NULL COMMENT 'dgGoodsId',
  `gb_dgp_distributer_id` tinyint DEFAULT NULL COMMENT 'dg',
  `gb_dgp_goods_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '价格',
  `gb_dgp_goods_lowest_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最低价格',
  `gb_dgp_goods_highest_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最高价格',
  `gb_dgp_pur_goods_id` int DEFAULT NULL COMMENT '采购商品id',
  `gb_dgp_pur_user_id` int DEFAULT NULL COMMENT '采购员',
  `gb_dgp_pur_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '采购日期',
  `gb_dgp_pur_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '采购价格',
  `gb_dgp_pur_what` tinyint DEFAULT NULL COMMENT '采购价高或低',
  `gb_dgp_pur_scale` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高或低比例',
  `gb_dgp_pur_what_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_pur_department_id` int DEFAULT NULL COMMENT '采购部门id',
  `gb_dgp_status` tinyint DEFAULT NULL,
  `gb_dgp_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgp_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgp_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgp_dep_settle_id` int DEFAULT NULL,
  `gb_dgp_pur_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_goods_lowest_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_goods_highest_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_pur_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '采购shuliang',
  `gb_dgp_pur_nx_distributer_id` int DEFAULT NULL COMMENT '采购部门id',
  PRIMARY KEY (`gb_distributer_goods_price_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_distributer_goods_price
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_goods_shelf
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_goods_shelf`;
CREATE TABLE `gb_distributer_goods_shelf` (
  `gb_distributer_goods_shelf_id` int NOT NULL AUTO_INCREMENT COMMENT '货架id',
  `gb_distributer_goods_shelf_name` varchar(20) DEFAULT NULL COMMENT '货架名称',
  `gb_distributer_goods_shelf_sort` int DEFAULT NULL COMMENT '货架排序',
  `gb_distributer_goods_shelf_dis_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_distributer_goods_shelf_dep_id` int DEFAULT NULL COMMENT '批发商库房id',
  PRIMARY KEY (`gb_distributer_goods_shelf_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_distributer_goods_shelf
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_goods_shelf_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_goods_shelf_goods`;
CREATE TABLE `gb_distributer_goods_shelf_goods` (
  `gb_distributer_goods_shelf_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '货架商品id',
  `gb_DGSG_dis_goods_id` int DEFAULT NULL COMMENT '批发商商品id',
  `gb_DGSG_shelf_id` int DEFAULT NULL COMMENT '货架id',
  `gb_DGSG_sort` int DEFAULT NULL COMMENT '货架商品排序',
  `gb_DGSG_dep_id` int DEFAULT NULL,
  `gb_DGSG_dep_father_id` int DEFAULT NULL,
  `gb_DGSG_dep_dis_goods_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_goods_shelf_goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_distributer_goods_shelf_goods
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_module
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_module`;
CREATE TABLE `gb_distributer_module` (
  `gb_distributer_module_id` int NOT NULL AUTO_INCREMENT,
  `gb_dm_fixed_supplier_number` int DEFAULT '-1',
  `gb_dm_purchase_number` int DEFAULT '-1',
  `gb_dm_stock_number` int DEFAULT '-1',
  `gb_dm_app_supplier_number` int DEFAULT '-1',
  `gb_dm_central_kitchen_number` int DEFAULT '-1',
  `gb_dm_direct_sales_number` int DEFAULT '-1',
  `gb_dm_franchisee_number` int DEFAULT '-1',
  `gb_dm_distributer_id` int NOT NULL DEFAULT '-1',
  PRIMARY KEY (`gb_distributer_module_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_distributer_module
-- ----------------------------
BEGIN;
INSERT INTO `gb_distributer_module` (`gb_distributer_module_id`, `gb_dm_fixed_supplier_number`, `gb_dm_purchase_number`, `gb_dm_stock_number`, `gb_dm_app_supplier_number`, `gb_dm_central_kitchen_number`, `gb_dm_direct_sales_number`, `gb_dm_franchisee_number`, `gb_dm_distributer_id`) VALUES (9, -1, -1, -1, -1, -1, 0, -1, 19);
INSERT INTO `gb_distributer_module` (`gb_distributer_module_id`, `gb_dm_fixed_supplier_number`, `gb_dm_purchase_number`, `gb_dm_stock_number`, `gb_dm_app_supplier_number`, `gb_dm_central_kitchen_number`, `gb_dm_direct_sales_number`, `gb_dm_franchisee_number`, `gb_dm_distributer_id`) VALUES (10, -1, -1, -1, -1, -1, 0, -1, 1);
INSERT INTO `gb_distributer_module` (`gb_distributer_module_id`, `gb_dm_fixed_supplier_number`, `gb_dm_purchase_number`, `gb_dm_stock_number`, `gb_dm_app_supplier_number`, `gb_dm_central_kitchen_number`, `gb_dm_direct_sales_number`, `gb_dm_franchisee_number`, `gb_dm_distributer_id`) VALUES (11, -1, -1, -1, -1, -1, 0, -1, 1);
INSERT INTO `gb_distributer_module` (`gb_distributer_module_id`, `gb_dm_fixed_supplier_number`, `gb_dm_purchase_number`, `gb_dm_stock_number`, `gb_dm_app_supplier_number`, `gb_dm_central_kitchen_number`, `gb_dm_direct_sales_number`, `gb_dm_franchisee_number`, `gb_dm_distributer_id`) VALUES (12, -1, -1, -1, -1, -1, 0, -1, 1);
INSERT INTO `gb_distributer_module` (`gb_distributer_module_id`, `gb_dm_fixed_supplier_number`, `gb_dm_purchase_number`, `gb_dm_stock_number`, `gb_dm_app_supplier_number`, `gb_dm_central_kitchen_number`, `gb_dm_direct_sales_number`, `gb_dm_franchisee_number`, `gb_dm_distributer_id`) VALUES (13, -1, -1, -1, -1, -1, 0, -1, 1);
INSERT INTO `gb_distributer_module` (`gb_distributer_module_id`, `gb_dm_fixed_supplier_number`, `gb_dm_purchase_number`, `gb_dm_stock_number`, `gb_dm_app_supplier_number`, `gb_dm_central_kitchen_number`, `gb_dm_direct_sales_number`, `gb_dm_franchisee_number`, `gb_dm_distributer_id`) VALUES (14, -1, -1, -1, -1, -1, 0, -1, 1);
INSERT INTO `gb_distributer_module` (`gb_distributer_module_id`, `gb_dm_fixed_supplier_number`, `gb_dm_purchase_number`, `gb_dm_stock_number`, `gb_dm_app_supplier_number`, `gb_dm_central_kitchen_number`, `gb_dm_direct_sales_number`, `gb_dm_franchisee_number`, `gb_dm_distributer_id`) VALUES (15, -1, -1, -1, -1, -1, 0, -1, 1);
INSERT INTO `gb_distributer_module` (`gb_distributer_module_id`, `gb_dm_fixed_supplier_number`, `gb_dm_purchase_number`, `gb_dm_stock_number`, `gb_dm_app_supplier_number`, `gb_dm_central_kitchen_number`, `gb_dm_direct_sales_number`, `gb_dm_franchisee_number`, `gb_dm_distributer_id`) VALUES (16, -1, -1, -1, -1, -1, 0, -1, 1);
INSERT INTO `gb_distributer_module` (`gb_distributer_module_id`, `gb_dm_fixed_supplier_number`, `gb_dm_purchase_number`, `gb_dm_stock_number`, `gb_dm_app_supplier_number`, `gb_dm_central_kitchen_number`, `gb_dm_direct_sales_number`, `gb_dm_franchisee_number`, `gb_dm_distributer_id`) VALUES (17, -1, -1, -1, -1, -1, 0, -1, 1);
INSERT INTO `gb_distributer_module` (`gb_distributer_module_id`, `gb_dm_fixed_supplier_number`, `gb_dm_purchase_number`, `gb_dm_stock_number`, `gb_dm_app_supplier_number`, `gb_dm_central_kitchen_number`, `gb_dm_direct_sales_number`, `gb_dm_franchisee_number`, `gb_dm_distributer_id`) VALUES (18, -1, -1, -1, -1, -1, 0, -1, 1);
INSERT INTO `gb_distributer_module` (`gb_distributer_module_id`, `gb_dm_fixed_supplier_number`, `gb_dm_purchase_number`, `gb_dm_stock_number`, `gb_dm_app_supplier_number`, `gb_dm_central_kitchen_number`, `gb_dm_direct_sales_number`, `gb_dm_franchisee_number`, `gb_dm_distributer_id`) VALUES (19, -1, -1, -1, -1, -1, 0, -1, 2);
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_pay
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_pay`;
CREATE TABLE `gb_distributer_pay` (
  `gb_distributer_pay_id` int NOT NULL AUTO_INCREMENT,
  `gb_gdp_gb_dis_id` int DEFAULT NULL,
  `gb_gdp_pay_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_gdp_from_time` date DEFAULT NULL,
  `gb_gdp_stop_time` date DEFAULT NULL,
  `gb_gdp_pay_time` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_gdp_type` tinyint DEFAULT NULL,
  `gb_gdp_status` tinyint DEFAULT NULL,
  `gb_gdp_trade_no` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_gdp_gb_new_dis_id` int DEFAULT NULL,
  `gb_gdp_buy_quantity` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_gdp_img_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_gdp_sell_detail` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_pay_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_distributer_pay
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_pay_list
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_pay_list`;
CREATE TABLE `gb_distributer_pay_list` (
  `gb_distributer_pay_list_id` int NOT NULL AUTO_INCREMENT,
  `gb_ndpl_gb_dis_id` int DEFAULT NULL,
  `gb_ndpl_pay_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_pay_time` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_type` tinyint DEFAULT NULL,
  `gb_ndpl_status` tinyint DEFAULT NULL,
  `gb_ndpl_pay_date` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_gb_pb_id` int DEFAULT NULL,
  `gb_ndpl_pay_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_pay_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_rest_points` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_nx_supplier_id` int DEFAULT NULL,
  `gb_ndpl_gb_department_father_id` int DEFAULT NULL,
  `gb_ndpl_gb_department_id` int DEFAULT NULL,
  `gb_ndpl_gb_dis_goods_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_pay_list_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_distributer_pay_list
-- ----------------------------
BEGIN;
INSERT INTO `gb_distributer_pay_list` (`gb_distributer_pay_list_id`, `gb_ndpl_gb_dis_id`, `gb_ndpl_pay_subtotal`, `gb_ndpl_pay_time`, `gb_ndpl_type`, `gb_ndpl_status`, `gb_ndpl_pay_date`, `gb_ndpl_gb_pb_id`, `gb_ndpl_pay_month`, `gb_ndpl_pay_year`, `gb_ndpl_rest_points`, `gb_ndpl_nx_supplier_id`, `gb_ndpl_gb_department_father_id`, `gb_ndpl_gb_department_id`, `gb_ndpl_gb_dis_goods_id`) VALUES (1, 1, '1', '2026-04-16 02:05', 0, 0, '2026-04-16', 21, '04', '2026', '10', 34, NULL, NULL, -1);
INSERT INTO `gb_distributer_pay_list` (`gb_distributer_pay_list_id`, `gb_ndpl_gb_dis_id`, `gb_ndpl_pay_subtotal`, `gb_ndpl_pay_time`, `gb_ndpl_type`, `gb_ndpl_status`, `gb_ndpl_pay_date`, `gb_ndpl_gb_pb_id`, `gb_ndpl_pay_month`, `gb_ndpl_pay_year`, `gb_ndpl_rest_points`, `gb_ndpl_nx_supplier_id`, `gb_ndpl_gb_department_father_id`, `gb_ndpl_gb_department_id`, `gb_ndpl_gb_dis_goods_id`) VALUES (2, 1, '1', '2026-04-17 21:20', 0, 0, '2026-04-17', 4, '04', '2026', '10', 1, NULL, NULL, -1);
INSERT INTO `gb_distributer_pay_list` (`gb_distributer_pay_list_id`, `gb_ndpl_gb_dis_id`, `gb_ndpl_pay_subtotal`, `gb_ndpl_pay_time`, `gb_ndpl_type`, `gb_ndpl_status`, `gb_ndpl_pay_date`, `gb_ndpl_gb_pb_id`, `gb_ndpl_pay_month`, `gb_ndpl_pay_year`, `gb_ndpl_rest_points`, `gb_ndpl_nx_supplier_id`, `gb_ndpl_gb_department_father_id`, `gb_ndpl_gb_department_id`, `gb_ndpl_gb_dis_goods_id`) VALUES (3, 1, '1', '2026-04-17 21:26', 0, 0, '2026-04-17', 1, '04', '2026', '9', 1, NULL, NULL, -1);
INSERT INTO `gb_distributer_pay_list` (`gb_distributer_pay_list_id`, `gb_ndpl_gb_dis_id`, `gb_ndpl_pay_subtotal`, `gb_ndpl_pay_time`, `gb_ndpl_type`, `gb_ndpl_status`, `gb_ndpl_pay_date`, `gb_ndpl_gb_pb_id`, `gb_ndpl_pay_month`, `gb_ndpl_pay_year`, `gb_ndpl_rest_points`, `gb_ndpl_nx_supplier_id`, `gb_ndpl_gb_department_father_id`, `gb_ndpl_gb_department_id`, `gb_ndpl_gb_dis_goods_id`) VALUES (4, 1, '1', '2026-04-17 21:51', 0, 0, '2026-04-17', 1, '04', '2026', '8', 1, NULL, NULL, -1);
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_purchase_batch
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_purchase_batch`;
CREATE TABLE `gb_distributer_purchase_batch` (
  `gb_distributer_purchase_batch_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商进货批次id',
  `gb_DPB_status` tinyint DEFAULT NULL COMMENT '批发商进货批次状态',
  `gb_DPB_user_admin_type` tinyint DEFAULT NULL COMMENT '进货批次用户类型',
  `gb_DPB_time` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商进货批次时间',
  `gb_DPB_pur_user_id` int DEFAULT NULL COMMENT '批发商进货采购员id',
  `gb_DPB_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_DPB_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_hour` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_minute` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_subtotal` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_pur_department_id` int DEFAULT NULL COMMENT '采购部门id',
  `gb_DPB_pay_type` int DEFAULT NULL COMMENT '付款方式:0==现金; 1 ==记账，',
  `gb_DPB_pay_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '付款金额',
  `gb_DPB_supplier_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_purchase_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_purchase_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_purchase_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_gb_supplier_payment_id` int DEFAULT NULL,
  `gb_DPB_purchase_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_seller_reply_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_finish_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_nx_distributer_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_buy_user_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_sell_user_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_buy_user_open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_sell_user_open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_purchase_type` tinyint DEFAULT NULL COMMENT '0 手动订货，1 自动订货',
  `gb_DPB_dep_bill_id` int DEFAULT NULL COMMENT '供货商商id',
  PRIMARY KEY (`gb_distributer_purchase_batch_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_distributer_purchase_batch
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_purchase_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_purchase_goods`;
CREATE TABLE `gb_distributer_purchase_goods` (
  `gb_distributer_purchase_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商采购商品id',
  `gb_DPG_dis_goods_id` int DEFAULT NULL COMMENT '采购商品id',
  `gb_DPG_dis_goods_father_id` int DEFAULT NULL COMMENT '采购父级商品id',
  `gb_DPG_quantity` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购数量',
  `gb_DPG_standard` varchar(6) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购规格',
  `gb_DPG_status` tinyint DEFAULT NULL COMMENT '采购状态',
  `gb_DPG_distributer_id` int DEFAULT NULL COMMENT '采购批发商id',
  `gb_DPG_purchase_type` tinyint DEFAULT NULL COMMENT '采购方式："1 订单采购""2 添加采购"',
  `gb_DPG_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购时间',
  `gb_DPG_batch_id` int DEFAULT NULL COMMENT '采购批次号',
  `gb_DPG_pur_user_id` int DEFAULT NULL COMMENT '采购方式为"采购"的采购员id',
  `gb_DPG_buy_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购单价',
  `gb_DPG_buy_quantity` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购数量',
  `gb_DPG_orders_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  `gb_DPG_type_add_user_id` int DEFAULT NULL COMMENT '添加采购用户id',
  `gb_DPG_apply_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPG_purchase_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_input_type` tinyint DEFAULT NULL,
  `gb_DPG_buy_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPG_purchase_department_id` int DEFAULT NULL COMMENT '库房或者中央厨房采购部门id',
  `gb_DPG_purchase_month` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_purchase_year` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_purchase_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_dis_goods_price_id` int DEFAULT NULL COMMENT '采购商品价格表id',
  `gb_DPG_purchase_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购week',
  `gb_DPG_purchase_week_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '第几周',
  `gb_DPG_buy_scale_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货单位价格',
  `gb_DPG_buy_scale_quantity` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货单位单价',
  `gb_DPG_buy_scale` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货单位系数',
  `gb_DPG_buy_price_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购单价异常原因',
  `gb_DPG_pay_type` tinyint DEFAULT NULL COMMENT '支付方式',
  `gb_DPG_is_check` tinyint DEFAULT NULL COMMENT '支付方式',
  `gb_DPG_waste_full_time` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '废弃时间',
  `gb_DPG_warn_full_time` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '报警时间',
  `gb_DPG_weight_id` int DEFAULT NULL COMMENT '称重disid',
  `gb_DPG_orders_finish_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  `gb_DPG_orders_bill_amount` int DEFAULT NULL COMMENT 'bill的订单数量',
  `gb_DPG_purchase_nx_supplier_id` int DEFAULT NULL COMMENT 'jsSupplierId',
  `gb_DPG_dis_goods_grand_id` int DEFAULT NULL,
  `gb_DPG_supplier_finish_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_stock_finish_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_dis_goods_great_id` int DEFAULT NULL,
  `gb_DPG_orders_weight_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  PRIMARY KEY (`gb_distributer_purchase_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_distributer_purchase_goods
-- ----------------------------
BEGIN;
INSERT INTO `gb_distributer_purchase_goods` (`gb_distributer_purchase_goods_id`, `gb_DPG_dis_goods_id`, `gb_DPG_dis_goods_father_id`, `gb_DPG_quantity`, `gb_DPG_standard`, `gb_DPG_status`, `gb_DPG_distributer_id`, `gb_DPG_purchase_type`, `gb_DPG_time`, `gb_DPG_batch_id`, `gb_DPG_pur_user_id`, `gb_DPG_buy_price`, `gb_DPG_buy_quantity`, `gb_DPG_orders_amount`, `gb_DPG_type_add_user_id`, `gb_DPG_apply_date`, `gb_DPG_purchase_date`, `gb_DPG_input_type`, `gb_DPG_buy_subtotal`, `gb_DPG_purchase_department_id`, `gb_DPG_purchase_month`, `gb_DPG_purchase_year`, `gb_DPG_purchase_full_time`, `gb_DPG_dis_goods_price_id`, `gb_DPG_purchase_week`, `gb_DPG_purchase_week_year`, `gb_DPG_buy_scale_price`, `gb_DPG_buy_scale_quantity`, `gb_DPG_buy_scale`, `gb_DPG_buy_price_reason`, `gb_DPG_pay_type`, `gb_DPG_is_check`, `gb_DPG_waste_full_time`, `gb_DPG_warn_full_time`, `gb_DPG_weight_id`, `gb_DPG_orders_finish_amount`, `gb_DPG_orders_bill_amount`, `gb_DPG_purchase_nx_supplier_id`, `gb_DPG_dis_goods_grand_id`, `gb_DPG_supplier_finish_date`, `gb_DPG_stock_finish_date`, `gb_DPG_dis_goods_great_id`, `gb_DPG_orders_weight_amount`) VALUES (1, 1, 1, '3', '颗', 4, 1, 1, '11:05', -1, 1, '1', '3.0', 1, NULL, '2026-04-23', '2026-04-23', NULL, '3.0', 1, '04', '2026', '2026-04-23 11:05', NULL, '星期四', '17', NULL, NULL, '-1', NULL, 5, NULL, NULL, NULL, NULL, 0, 0, -1, 2, NULL, '2026-04-23', 3, 0);
INSERT INTO `gb_distributer_purchase_goods` (`gb_distributer_purchase_goods_id`, `gb_DPG_dis_goods_id`, `gb_DPG_dis_goods_father_id`, `gb_DPG_quantity`, `gb_DPG_standard`, `gb_DPG_status`, `gb_DPG_distributer_id`, `gb_DPG_purchase_type`, `gb_DPG_time`, `gb_DPG_batch_id`, `gb_DPG_pur_user_id`, `gb_DPG_buy_price`, `gb_DPG_buy_quantity`, `gb_DPG_orders_amount`, `gb_DPG_type_add_user_id`, `gb_DPG_apply_date`, `gb_DPG_purchase_date`, `gb_DPG_input_type`, `gb_DPG_buy_subtotal`, `gb_DPG_purchase_department_id`, `gb_DPG_purchase_month`, `gb_DPG_purchase_year`, `gb_DPG_purchase_full_time`, `gb_DPG_dis_goods_price_id`, `gb_DPG_purchase_week`, `gb_DPG_purchase_week_year`, `gb_DPG_buy_scale_price`, `gb_DPG_buy_scale_quantity`, `gb_DPG_buy_scale`, `gb_DPG_buy_price_reason`, `gb_DPG_pay_type`, `gb_DPG_is_check`, `gb_DPG_waste_full_time`, `gb_DPG_warn_full_time`, `gb_DPG_weight_id`, `gb_DPG_orders_finish_amount`, `gb_DPG_orders_bill_amount`, `gb_DPG_purchase_nx_supplier_id`, `gb_DPG_dis_goods_grand_id`, `gb_DPG_supplier_finish_date`, `gb_DPG_stock_finish_date`, `gb_DPG_dis_goods_great_id`, `gb_DPG_orders_weight_amount`) VALUES (2, 2, 4, '2', '斤', 4, 1, 1, '11:06', -1, 1, '12', '5.0', 1, NULL, '2026-04-23', '2026-04-23', NULL, '60.0', 1, '04', '2026', '2026-04-23 11:06', NULL, '星期四', '17', NULL, NULL, '-1', NULL, 5, NULL, NULL, NULL, NULL, 0, 0, -1, 5, NULL, '2026-04-23', 6, 0);
INSERT INTO `gb_distributer_purchase_goods` (`gb_distributer_purchase_goods_id`, `gb_DPG_dis_goods_id`, `gb_DPG_dis_goods_father_id`, `gb_DPG_quantity`, `gb_DPG_standard`, `gb_DPG_status`, `gb_DPG_distributer_id`, `gb_DPG_purchase_type`, `gb_DPG_time`, `gb_DPG_batch_id`, `gb_DPG_pur_user_id`, `gb_DPG_buy_price`, `gb_DPG_buy_quantity`, `gb_DPG_orders_amount`, `gb_DPG_type_add_user_id`, `gb_DPG_apply_date`, `gb_DPG_purchase_date`, `gb_DPG_input_type`, `gb_DPG_buy_subtotal`, `gb_DPG_purchase_department_id`, `gb_DPG_purchase_month`, `gb_DPG_purchase_year`, `gb_DPG_purchase_full_time`, `gb_DPG_dis_goods_price_id`, `gb_DPG_purchase_week`, `gb_DPG_purchase_week_year`, `gb_DPG_buy_scale_price`, `gb_DPG_buy_scale_quantity`, `gb_DPG_buy_scale`, `gb_DPG_buy_price_reason`, `gb_DPG_pay_type`, `gb_DPG_is_check`, `gb_DPG_waste_full_time`, `gb_DPG_warn_full_time`, `gb_DPG_weight_id`, `gb_DPG_orders_finish_amount`, `gb_DPG_orders_bill_amount`, `gb_DPG_purchase_nx_supplier_id`, `gb_DPG_dis_goods_grand_id`, `gb_DPG_supplier_finish_date`, `gb_DPG_stock_finish_date`, `gb_DPG_dis_goods_great_id`, `gb_DPG_orders_weight_amount`) VALUES (3, 3, 7, '1', '箱', 4, 1, 1, '11:06', -1, 1, '26.67', '6', 1, NULL, '2026-04-23', '2026-04-23', NULL, '160.0', 1, '04', '2026', '2026-04-23 11:06', NULL, '星期四', '17', NULL, NULL, '6', NULL, 5, NULL, NULL, NULL, NULL, 0, 0, -1, 8, NULL, '2026-04-23', 9, 0);
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_standard
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_standard`;
CREATE TABLE `gb_distributer_standard` (
  `gb_distributer_standard_id` int NOT NULL AUTO_INCREMENT,
  `gb_DS_dis_goods_id` int DEFAULT NULL,
  `gb_DS_standard_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DS_standard_file_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DS_standard_scale` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DS_standard_error` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DS_standard_sort` int DEFAULT NULL,
  `gb_DS_standard_weight` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_standard_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_distributer_standard
-- ----------------------------
BEGIN;
INSERT INTO `gb_distributer_standard` (`gb_distributer_standard_id`, `gb_DS_dis_goods_id`, `gb_DS_standard_name`, `gb_DS_standard_file_path`, `gb_DS_standard_scale`, `gb_DS_standard_error`, `gb_DS_standard_sort`, `gb_DS_standard_weight`) VALUES (1, 1, '颗', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `gb_distributer_standard` (`gb_distributer_standard_id`, `gb_DS_dis_goods_id`, `gb_DS_standard_name`, `gb_DS_standard_file_path`, `gb_DS_standard_scale`, `gb_DS_standard_error`, `gb_DS_standard_sort`, `gb_DS_standard_weight`) VALUES (2, 3, '箱', NULL, NULL, NULL, NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for gb_distributer_user
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_user`;
CREATE TABLE `gb_distributer_user` (
  `gb_distributer_user_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商用户id',
  `gb_DIU_wx_avartra_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '用户名',
  `gb_DIU_wx_nick_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '登陆密码',
  `gb_DIU_wx_open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DIU_wx_phone` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DIU_distributer_id` int DEFAULT NULL,
  `gb_DIU_admin` tinyint DEFAULT NULL,
  `gb_DIU_print_device_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DIU_url_change` tinyint DEFAULT NULL,
  `gb_DIU_print_bill_device_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DIU_qy_corp_user_id` int DEFAULT NULL COMMENT '企业用户id',
  `gb_DIU_login_times` int DEFAULT NULL COMMENT '企业用户id',
  PRIMARY KEY (`gb_distributer_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of gb_distributer_user
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for gb_report
-- ----------------------------
DROP TABLE IF EXISTS `gb_report`;
CREATE TABLE `gb_report` (
  `gb_report_id` int NOT NULL AUTO_INCREMENT,
  `gb_rep_ids` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_rep_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_rep_dis_user_id` int DEFAULT NULL,
  `gb_rep_start_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_rep_stop_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_report_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of gb_report
-- ----------------------------
BEGIN;
INSERT INTO `gb_report` (`gb_report_id`, `gb_rep_ids`, `gb_rep_type`, `gb_rep_dis_user_id`, `gb_rep_start_date`, `gb_rep_stop_date`) VALUES (2, '1', 'disBusiness', 1, '2026-04-01', '2026-04-18');
COMMIT;

-- ----------------------------
-- Table structure for nx_alias
-- ----------------------------
DROP TABLE IF EXISTS `nx_alias`;
CREATE TABLE `nx_alias` (
  `nx_alias_id` int NOT NULL AUTO_INCREMENT COMMENT '别名id',
  `nx_alias_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '别名名称',
  `nx_als_goods_id` int DEFAULT NULL COMMENT '别名商品id',
  `nx_als_sort` int DEFAULT NULL COMMENT '别名排序',
  `nx_alias_pinyin` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '别名名称',
  `nx_alias_py` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '别名名称',
  PRIMARY KEY (`nx_alias_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=123 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of nx_alias
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for nx_jrdh_supplier
-- ----------------------------
DROP TABLE IF EXISTS `nx_jrdh_supplier`;
CREATE TABLE `nx_jrdh_supplier` (
  `nx_jrdh_supplier_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `nx_jrdhs_supplier_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '供货商名称',
  `nx_jrdhs_gb_distributer_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_gb_department_id` int DEFAULT NULL COMMENT 'gbDepid',
  `nx_jrdhs_user_id` int DEFAULT NULL COMMENT '接单元id',
  `nx_jrdhs_nx_distributer_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_nx_community_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_nx_pur_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_gb_pur_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_comm_pur_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_nx_jrdh_buy_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_status` tinyint(1) DEFAULT NULL COMMENT '供货商名称',
  `nx_jrdhs_sys_city_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_sys_market_id` int DEFAULT NULL COMMENT 'gbDisid',
  PRIMARY KEY (`nx_jrdh_supplier_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of nx_jrdh_supplier
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for nx_jrdh_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_jrdh_user`;
CREATE TABLE `nx_jrdh_user` (
  `nx_jrdh_user_id` int NOT NULL AUTO_INCREMENT COMMENT '订货用户id',
  `nx_jrdh_wx_avartra_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货用户微信头像',
  `nx_jrdh_wx_nick_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货用户微信昵称',
  `nx_jrdh_wx_open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货用户微信openid',
  `nx_jrdh_wx_phone` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货户微信手机号码',
  `nx_jrdh_join_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '零售商用户加入日期',
  `nx_jrdh_nx_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_jrdh_nx_purchaser_user_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_nx_community_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_jrdh_nx_comm_purchaser_user_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_url_change` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_admin` int DEFAULT NULL COMMENT '0 seller, 1nxpurchaser 2 gbpurchaser ',
  `nx_jrdh_gb_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_jrdh_gb_department_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_gb_department_user_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_device_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_device_print_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_auth_supplier_id` int DEFAULT NULL COMMENT '批发商id',
  PRIMARY KEY (`nx_jrdh_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of nx_jrdh_user
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for nx_sell_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_sell_user`;
CREATE TABLE `nx_sell_user` (
  `nx_sell_user_id` int NOT NULL AUTO_INCREMENT COMMENT '卖货用户id',
  `nx_SU_retailer_id` int DEFAULT NULL COMMENT '零售商id',
  `nx_SU_wx_avartra_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '卖货用户微信头像',
  `nx_SU_wx_nick_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '卖货用户微信昵称',
  `nx_SU_wx_open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '卖货用户微信openid',
  `nx_SU_wx_phone` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '卖货户微信手机号码',
  `nx_SU_join_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '用户加入日期',
  `nx_SU_gb_dis_supplier_id` int DEFAULT NULL COMMENT 'gb供货商id',
  `nx_SU_nx_dis_id` int DEFAULT NULL COMMENT 'nxDistributerId',
  PRIMARY KEY (`nx_sell_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of nx_sell_user
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for nx_standard
-- ----------------------------
DROP TABLE IF EXISTS `nx_standard`;
CREATE TABLE `nx_standard` (
  `nx_standard_id` int NOT NULL AUTO_INCREMENT,
  `nx_standard_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_s_goods_id` int DEFAULT NULL,
  `nx_standard_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_standard_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_standard_error` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_standard_sort` int DEFAULT NULL,
  `nx_standard_weight` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_standard_id`)
) ENGINE=InnoDB AUTO_INCREMENT=477 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Records of nx_standard
-- ----------------------------
BEGIN;
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (1, '根', 45096, 'uploadImage/wxbc686226ccc443f1.o6zAJsw3k3_I4jY0lYtqK1TqmM_c.0r30bxBpTNcFbabac3529edec5e2c86ba0c548932af5.jpg', '1.3', '0', NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (158, '个', 45559, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (159, '箱', 45506, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (160, '个', 45126, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (161, '个', 45580, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (162, '根', 45114, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (164, '把', 45176, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (165, '捆', 45174, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (166, '把', 45198, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (167, '颗', 45188, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (168, '把', 45189, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (169, '把', 45191, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (170, '个', 45563, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (171, '颗', 45182, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (172, '个', 45106, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (173, '根', 45094, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (174, '个', 45139, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (180, '个', 45217, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (181, '个', 45247, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (182, '个', 45258, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (183, '个', 45239, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (184, '个', 45284, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (185, '个', 45285, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (186, '个', 45286, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (187, '个', 45237, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (188, '个', 45236, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (189, '个', 45226, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (190, '个', 45225, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (191, '个', 45272, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (192, '个', 45265, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (193, '个', 45288, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (194, '个', 45243, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (195, '个', 45240, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (196, '个', 45287, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (197, '个', 45241, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (198, '个', 45252, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (199, '个', 45301, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (200, '个', 45302, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (201, '个', 45303, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (202, '个', 45305, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (203, '个', 45227, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (204, '个', 45253, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (205, '个', 45262, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (206, '个', 45263, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (207, '个', 45224, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (208, '个', 45251, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (209, '个', 45238, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (210, '个', 45230, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (211, '个', 45289, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (212, '个', 45290, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (213, '个', 45291, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (214, '个', 45248, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (215, '个', 45300, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (216, '个', 45299, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (217, '个', 45261, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (218, '个', 45274, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (219, '个', 45275, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (220, '个', 45276, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (221, '个', 45277, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (222, '个', 45279, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (223, '个', 45280, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (224, '个', 45282, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (225, '个', 45283, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (226, '个', 45278, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (227, '个', 45250, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (228, '个', 45249, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (229, '个', 45264, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (230, '个', 45235, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (232, '个', 45234, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (233, '个', 45259, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (234, '个', 45255, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (235, '个', 45256, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (236, '条', 45325, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (237, '条', 45351, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (238, '条', 45341, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (239, '条', 45343, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (240, '条', 45353, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (241, '条', 45364, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (242, '条', 45357, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (243, '条', 45339, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (244, '条', 45338, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (245, '条', 45337, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (246, '条', 45356, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (247, '条', 45328, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (248, '条', 45336, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (249, '条', 45334, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (250, '条', 45322, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (251, '条', 45352, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (252, '条', 45346, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (253, '条', 45348, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (254, '条', 45333, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (255, '条', 45342, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (256, '条', 45345, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (257, '条', 45347, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (258, '个', 45379, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (259, '个', 45378, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (260, '个', 45376, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (261, '个', 45374, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (262, '个', 45372, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (263, '张', 31117, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (264, '块', 31118, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (265, '个', 17600008, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (266, '块', 17600010, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (267, '根', 17600026, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (269, '个', 45097, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (270, '个', 45104, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (271, '个', 45590, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (272, '个', 45107, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (273, '个', 45558, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (274, '根', 45101, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (275, '节', 45103, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (276, '根', 45103, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (277, '个', 44678, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (278, '根', 17600014, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (279, '根', 45100, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (280, '个', 45095, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (281, '个', 45093, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (282, '把', 45109, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (283, '个', 45159, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (284, '根', 45135, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (285, '根', 45163, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (286, '个', 45138, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (287, '根', 45155, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (288, '根', 45132, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (289, '根', 45592, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (290, '颗', 45141, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (291, '个', 45158, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (292, '根', 45151, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (293, '个', 45153, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (294, '根', 45152, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (295, '颗', 45142, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (296, '根', 45140, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (297, '个', 45130, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (298, '颗', 45599, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (299, '个', 45144, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (300, '根', 45156, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (301, '根', 45161, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (302, '个', 45136, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (303, '捆', 45168, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (304, '颗', 45165, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (305, '把', 45190, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (306, '颗', 45194, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (307, '颗', 45593, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (308, '颗', 45600, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (309, '颗', 45187, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (310, '颗', 45185, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (311, '捆', 45173, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (312, '盒', 45598, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (313, '个', 45170, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (314, '个', 17600015, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (315, '个', 45112, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (316, '个', 45583, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (317, '个', 45566, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (318, '捆', 45121, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (320, '个', 45113, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (321, '根', 45229, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (322, '根', 17600030, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (323, '根', 45413, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (324, '个', 45407, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (325, '根', 45428, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (326, '根', 45427, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (327, '个', 45418, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (328, '个', 45416, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (329, '个', 45425, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (330, '个', 45424, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (331, '个', 45414, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (332, '根', 45408, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (333, '个', 45423, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (334, '个', 45421, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (335, '个', 45409, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (336, '个', 45417, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (337, '个', 45415, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (338, '个', 45449, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (339, '个', 45441, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (340, '根', 45445, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (341, '个', 45444, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (342, '个', 45450, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (343, '个', 45443, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (344, '个', 45440, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (345, '个', 45455, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (346, '个', 45454, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (347, '个', 45468, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (348, '个', 45466, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (349, '根', 45464, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (350, '个', 45471, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (351, '个', 45467, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (352, '个', 45465, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (353, '根', 45469, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (354, '根', 45463, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (355, '个', 45456, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (356, '只', 45505, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (357, '只', 45500, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (358, '个', 45482, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (359, '只', 45502, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (360, '只', 45501, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (361, '个', 45480, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (362, '个', 45487, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (363, '个', 45488, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (365, '个', 45492, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (366, '个', 45478, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (367, '个', 45479, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (368, '个', 45486, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (369, '个', 45490, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (370, '个', 45481, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (371, '个', 45489, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (372, '个', 45477, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (373, '个', 45484, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (374, '只', 45476, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (375, '只', 45498, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (376, '只', 45473, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (377, '只', 45499, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (378, '个', 45494, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (379, '个', 45495, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (380, '个', 45497, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (381, '个', 45496, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (382, '个', 45475, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (383, '个', 45511, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (384, '个', 45506, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (385, '个', 45510, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (386, '个', 45508, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (387, '个', 45509, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (388, '个', 45514, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (389, '袋', 17600078, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (390, '袋', 17600077, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (391, '个', 17600065, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (393, '捆', 45128, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (394, '捆', 45114, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (397, '盒', 45219, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (398, '盒', 45222, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (400, '个', 17600114, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (401, '个', 17600115, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (402, '颗', 17600116, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (403, '袋', 45096, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (404, '包', 45094, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (405, '包', 45104, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (406, '包', 45590, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (407, '包', 45101, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (408, '袋', 44678, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (409, '袋', 17600014, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (410, '袋', 45093, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (411, '袋', 45112, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (412, '捆', 45127, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (413, '袋', 17600012, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (414, '袋', 45113, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (415, '个', 45171, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (418, '件', 17600130, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (419, '件', 17600146, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (423, '箱', 1561, NULL, '10', NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (424, '箱', 1387, NULL, '10', NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (427, '颗', 100470, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (428, '捆', 100003, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (429, '颗', 100007, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (430, '颗', 102044, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (431, '颗', 100547, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (433, '根', 101874, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (436, '捆', 100005, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (437, '颗', 100008, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (438, '串', 102228, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (439, '根', 102242, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (441, '只', 102248, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (442, '只', 102249, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (443, '袋', 102275, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (445, '根', 102304, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (446, '只', 102306, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (447, '件', 102334, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (448, '包', 100131, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (450, '条', 100393, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (451, '个', 100106, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (452, '把', 101820, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (453, '把', 100545, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (454, '件', 101987, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (455, '节', 100039, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (458, '条', 100077, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (459, '个', 100181, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (460, '个', 100041, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (461, '把', 100704, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (462, '颗', 100011, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (463, '颗', 101837, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (464, '个', 100137, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (465, '颗', 101966, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (466, '颗', 100010, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (468, '包', 101726, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (469, '件', 100926, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (470, '件', 100925, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (471, '件', 100101, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (472, '件', 100985, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (474, '把', 100369, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (475, '个', 101336, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `nx_standard` (`nx_standard_id`, `nx_standard_name`, `nx_s_goods_id`, `nx_standard_file_path`, `nx_standard_scale`, `nx_standard_error`, `nx_standard_sort`, `nx_standard_weight`) VALUES (476, '颗', 102350, NULL, NULL, NULL, NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for qy_gb_dis_corp_user
-- ----------------------------
DROP TABLE IF EXISTS `qy_gb_dis_corp_user`;
CREATE TABLE `qy_gb_dis_corp_user` (
  `qy_gb_dis_corp_user_id` int NOT NULL AUTO_INCREMENT,
  `qy_gb_dis_corp_user_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_user_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_open_user_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_qy_corp_id` int DEFAULT NULL,
  `qy_gb_distributer_id` int DEFAULT NULL,
  `qy_gb_dis_corp_session_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_user_join_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`qy_gb_dis_corp_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of qy_gb_dis_corp_user
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for sys_business_type
-- ----------------------------
DROP TABLE IF EXISTS `sys_business_type`;
CREATE TABLE `sys_business_type` (
  `sys_business_type_id` int NOT NULL,
  `sys_business_type_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `sys_by_dis_type` tinyint DEFAULT NULL COMMENT 'nxDis 1, gbDis 2,',
  PRIMARY KEY (`sys_business_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of sys_business_type
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for sys_city_market
-- ----------------------------
DROP TABLE IF EXISTS `sys_city_market`;
CREATE TABLE `sys_city_market` (
  `sys_city_market_id` int NOT NULL AUTO_INCREMENT,
  `sys_cm_city_id` int DEFAULT NULL,
  `sys_cm_market_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `sys_cm_machine_agent_id` int DEFAULT NULL,
  `sys_cm_register_gift_points` int DEFAULT '1000' COMMENT '配送商注册赠送试用点数',
  `sys_cm_points_per_yuan` int DEFAULT '100' COMMENT '一元兑换点数比例',
  `sys_cm_self_print_enabled` tinyint(1) DEFAULT '0' COMMENT '是否开通自助打印机器：0=关闭，1=开启',
  `sys_cm_manager_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '市场管理人员手机号',
  `sys_cm_area_coordinates` text CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci COMMENT '市场区域范围坐标（JSON格式存储多边形坐标点）',
  `sys_cm_center_latitude` decimal(10,7) DEFAULT NULL COMMENT '市场中心点纬度',
  `sys_cm_center_longitude` decimal(10,7) DEFAULT NULL COMMENT '市场中心点经度',
  `sys_cm_delivery_radius` int DEFAULT '5000' COMMENT '市场配送半径（米），默认5公里',
  `sys_cm_pay_config_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '市场支付配置类名（如：MyWxJjdhPayConfig）',
  `sys_cm_mini_app_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '市场小程序AppID（如：wx58ba279bc3d04c4a）',
  PRIMARY KEY (`sys_city_market_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- Records of sys_city_market
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) DEFAULT NULL COMMENT '密码',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `mobile` varchar(100) DEFAULT NULL COMMENT '手机号',
  `status` tinyint DEFAULT NULL COMMENT '状态  0：禁用   1：正常',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `user_dis_user_id` int DEFAULT NULL,
  `user_dis_id` int DEFAULT NULL,
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=428 DEFAULT CHARSET=utf8mb3 COMMENT='系统用户';

-- ----------------------------
-- Records of sys_user
-- ----------------------------
BEGIN;
COMMIT;

SET FOREIGN_KEY_CHECKS = 1;
