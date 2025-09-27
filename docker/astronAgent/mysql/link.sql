select 'spark-link DATABASE initialization started' as '';
CREATE DATABASE IF NOT EXISTS `spark-link`;

USE spark-link;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for link
-- ----------------------------

DROP TABLE IF EXISTS `tools_schema`;
CREATE TABLE tools_schema (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `app_id` VARCHAR(32) COMMENT '应用ID',
    `tool_id` VARCHAR(32) COMMENT '工具ID',
    `name` VARCHAR(128) COMMENT '工具名称',
    `description` VARCHAR(512) COMMENT '工具描述',
    `open_api_schema` TEXT COMMENT 'open api schema，json格式',
    `create_at` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_at` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `mcp_server_url` VARCHAR(255) COMMENT 'mcp_server_url',
    `schema` TEXT COMMENT 'schema,json格式',
    `version` VARCHAR(32) NOT NULL DEFAULT 'DEF_VER' COMMENT '版本号',
    `is_deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '是否已删除',
    UNIQUE KEY unique_tool_version (tool_id, version, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具数据库表';

select 'spark-link DATABASE initialization completed' as '';