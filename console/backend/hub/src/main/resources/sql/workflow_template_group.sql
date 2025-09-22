SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `workflow_template_group`;
CREATE TABLE `workflow_template_group` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Non-business primary key',
  `create_user` varchar(32) NOT NULL COMMENT 'Publisher domain account',
  `group_name` varchar(20) NOT NULL COMMENT 'Group name',
  `sort_index` int NOT NULL COMMENT 'Sort order',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT 'Whether logically deleted: 0 not deleted, 1 deleted',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `group_name_en` varchar(128) DEFAULT NULL COMMENT 'Group English name',
  PRIMARY KEY (`id`),
  KEY `idx_group_name` (`group_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Astra workflow template group (comprehensive management control)';

SET FOREIGN_KEY_CHECKS = 1;