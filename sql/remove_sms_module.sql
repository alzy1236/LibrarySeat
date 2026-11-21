-- 移除短信验证码模块（表 / 唯一索引 / 配置项）
DROP TABLE IF EXISTS `sms_code`;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_phone');
SET @sql := IF(@idx > 0, 'ALTER TABLE `sys_user` DROP INDEX `uk_phone`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DELETE FROM `sys_config` WHERE `config_key` LIKE 'sms.%';

SELECT COUNT(*) AS remaining_sms_configs FROM `sys_config` WHERE `config_key` LIKE 'sms.%';
SELECT COUNT(*) AS remaining_uk_phone FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_phone';