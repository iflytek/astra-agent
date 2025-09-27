select 'agent DATABASE initialization started' as '';
CREATE DATABASE IF NOT EXISTS agent;

USE agent;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for bot_config
-- ----------------------------
DROP TABLE IF EXISTS `bot_config`;
CREATE TABLE `bot_config` (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `app_id` varchar(32) NOT NULL COMMENT '应用id',
  `bot_id` varchar(40) NOT NULL COMMENT 'bot_id',
  `knowledge_config` json NOT NULL COMMENT '知识库参数配置',
  `model_config` json NOT NULL COMMENT '模型配置',
  `regular_config` json NOT NULL COMMENT '知识库选择配置',
  `tool_ids` json NOT NULL COMMENT '工具id配置',
  `mcp_server_ids` json NOT NULL COMMENT 'mcp server id配置',
  `mcp_server_urls` json NOT NULL COMMENT 'mcp server url配置',
  `flow_ids` json NOT NULL COMMENT 'flow id配置',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `union_app_bot` (`app_id`,`bot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

select 'agent DATABASE initialization completed' as '';