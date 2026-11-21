-- ============================================================
--  签到地点（地理围栏）
--  说明：
--    room_id 为 NULL 表示"全楼通用签到点"；指定 room_id 则只对该自习室生效
--    latitude/longitude 使用 WGS-84 坐标系（与浏览器 Geolocation 一致，
--    不要用高德/腾讯地图取点，它们是 GCJ-02，会产生几十到几百米偏移）
-- ============================================================

CREATE TABLE IF NOT EXISTS `checkin_location` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(64)  NOT NULL                COMMENT '签到点名称，如"一楼大厅签到处"',
  `room_id`     BIGINT       NULL     DEFAULT NULL   COMMENT '所属自习室，NULL=全楼通用',
  `latitude`    DECIMAL(10,7) NOT NULL               COMMENT '纬度(WGS-84)',
  `longitude`   DECIMAL(10,7) NOT NULL               COMMENT '经度(WGS-84)',
  `radius`      INT          NOT NULL DEFAULT 100    COMMENT '允许签到的半径(米)',
  `enabled`     TINYINT      NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `remark`      VARCHAR(255) NULL     DEFAULT NULL   COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_room` (`room_id`),
  KEY `idx_enabled` (`enabled`),
  CONSTRAINT `fk_location_room` FOREIGN KEY (`room_id`) REFERENCES `study_room` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到地点（地理围栏）';

-- 总开关：是否启用定位校验（1 启用 / 0 关闭，关闭后与原逻辑一致）
INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`)
SELECT 'checkin.location.enabled', '1', '是否启用签到定位校验（1启用 0关闭）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'checkin.location.enabled');

-- 默认签到点（示例坐标：某高校教学楼，管理员按实际位置修改即可）
INSERT INTO `checkin_location` (`name`, `room_id`, `latitude`, `longitude`, `radius`, `enabled`, `remark`)
SELECT '自习楼一层大厅签到处', NULL, 39.9042000, 116.4074000, 120, 1, '示例坐标，请管理员在后台按实际位置重新标定'
WHERE NOT EXISTS (SELECT 1 FROM `checkin_location`);