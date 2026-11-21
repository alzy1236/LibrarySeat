-- ============================================================
--  短信验证码（注册 / 找回密码）
--  说明：
--    scene: REGISTER 注册 / RESET 重置密码
--    验证码默认 4 位数字，5 分钟有效，最多尝试 5 次，60 秒可重发
-- ============================================================

CREATE TABLE IF NOT EXISTS `sms_code` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone`       VARCHAR(20) NOT NULL                COMMENT '手机号',
  `code`        VARCHAR(8)  NOT NULL                COMMENT '验证码',
  `scene`       VARCHAR(16) NOT NULL                COMMENT 'REGISTER / RESET',
  `expire_at`   DATETIME    NOT NULL                COMMENT '过期时间',
  `used`        TINYINT     NOT NULL DEFAULT 0      COMMENT '1 已使用',
  `attempts`    INT         NOT NULL DEFAULT 0      COMMENT '已校验次数',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_phone_scene` (`phone`, `scene`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信验证码';

-- 手机号唯一（重置密码靠它定位账号）；先把空字符串规范成 NULL
UPDATE `sys_user` SET `phone` = NULL WHERE `phone` = '';

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_phone'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_phone` (`phone`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------- 配置项 ----------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`)
SELECT 'sms.provider', 'mock', '短信服务商：mock 演示模式 / aliyun 阿里云短信'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sms.provider');

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`)
SELECT 'sms.verify.register', '1', '注册时是否强制手机验证码（1 强制 / 0 关闭）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sms.verify.register');

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`)
SELECT 'sms.code.expire.minutes', '5', '验证码有效期（分钟）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sms.code.expire.minutes');

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`)
SELECT 'sms.resend.seconds', '60', '同一手机号验证码重发间隔（秒）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sms.resend.seconds');

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`)
SELECT 'sms.daily.limit', '10', '同一手机号每日最多发送次数'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sms.daily.limit');

-- 阿里云短信参数（切正式短信时填这三项 + provider=aliyun）
INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`)
SELECT 'sms.aliyun.signName', '', '阿里云短信签名，如：自习室预约系统'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sms.aliyun.signName');

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`)
SELECT 'sms.aliyun.templateCode', '', '阿里云短信模板 CODE，如：SMS_123456789'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sms.aliyun.templateCode');

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`)
SELECT 'sms.aliyun.accessKeyId', '', '阿里云 AccessKeyId（建议用仅短信权限的子账号）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sms.aliyun.accessKeyId');

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`)
SELECT 'sms.aliyun.accessKeySecret', '', '阿里云 AccessKeySecret'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sms.aliyun.accessKeySecret');