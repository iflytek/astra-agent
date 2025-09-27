select 'tenant DATABASE initialization started' as '';
CREATE DATABASE IF NOT EXISTS tenant;

USE tenant;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for tb_app
-- ----------------------------
DROP TABLE IF EXISTS `tb_app`;

CREATE TABLE `tb_app` (
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `registration_time` datetime DEFAULT NULL COMMENT '创建时间',
  `app_id` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT '' COMMENT '应用唯一标识',
  `app_name` varchar(256) COLLATE utf8_bin DEFAULT NULL COMMENT '应用名称',
  `dev_id` bigint(20) DEFAULT NULL COMMENT '开发者id',
  `channel_id` varchar(128) COLLATE utf8_bin DEFAULT NULL COMMENT '渠道id',
  `source` varchar(32) COLLATE utf8_bin DEFAULT '' COMMENT '来源',
  `is_disable` tinyint(1) DEFAULT NULL COMMENT '是否禁用(true禁用 false启用)',
  `app_desc` varchar(521) COLLATE utf8_bin DEFAULT NULL COMMENT '应用描述',
  `is_delete` tinyint(1) DEFAULT NULL COMMENT '是否删除',
  `extend` varchar(256) COLLATE utf8_bin DEFAULT '' COMMENT '扩展字段',
  PRIMARY KEY (`app_id`),
  KEY `idx_registration_time` (`registration_time`),
  KEY `idx_dev_id` (`dev_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='应用主表';

INSERT INTO `tenant`.`tb_app` (`update_time`, `registration_time`, `app_id`, `app_name`, `dev_id`, `channel_id`, `source`, `is_disable`, `app_desc`, `is_delete`, `extend`)
    VALUES ('2025-09-20 00:00:00', '2025-09-20 00:00:00', '680ab54f', '星辰租户', 1, '0', 'admin', 0, '星辰租户', 0, '');

-- ----------------------------
-- Table structure for tb_auth
-- ----------------------------
DROP TABLE IF EXISTS `tb_auth`;
CREATE TABLE `tb_auth` (
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `registration_time` datetime DEFAULT NULL COMMENT '创建时间',
  `app_id` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT '' COMMENT '应用唯一标识',
  `api_key` varchar(128) COLLATE utf8_bin NOT NULL DEFAULT '' COMMENT '鉴权key',
  `api_secret` varchar(128) COLLATE utf8_bin DEFAULT NULL COMMENT '鉴权私钥',
  `source` bigint(20) DEFAULT NULL COMMENT '来源',
  `is_delete` tinyint(1) DEFAULT NULL COMMENT '是否删除',
  `extend` varchar(256) COLLATE utf8_bin DEFAULT NULL COMMENT '扩展字段',
  PRIMARY KEY (`app_id`,`api_key`),
  KEY `idx_registration_time` (`registration_time`),
  KEY `idx_api_key` (`api_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='应用关联的鉴权表';

INSERT INTO `tenant`.`tb_auth` (`update_time`, `registration_time`, `app_id`, `api_key`, `api_secret`, `source`, `is_delete`, `extend`)
    VALUES ('2025-09-20 00:00:00', '2025-09-20 00:00:00', '680ab54f', '7b709739e8da44536127a333c7603a83', 'NjhmY2NmM2NkZDE4MDFlNmM5ZjcyZjMy', 0, 0, '');

select 'tenant DATABASE initialization completed' as '';