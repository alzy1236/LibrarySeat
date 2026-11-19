-- =====================================================================
--  高校自习室座位预约管理系统 - 数据库初始化脚本
--  数据库: MySQL 8.0  字符集: utf8mb4
--  执行方式: mysql -uroot -p < sql/library_seat.sql
--  注意: 为便于复现实验与演示，本脚本不做去重保护，重复执行会重建数据。
-- =====================================================================

DROP DATABASE IF EXISTS library_seat;
CREATE DATABASE library_seat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE library_seat;

-- ---------------------------------------------------------------------
-- 1. 用户表（学生 / 管理员共用，通过 role 字段区分角色）
-- ---------------------------------------------------------------------
CREATE TABLE sys_user (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username        VARCHAR(32)  NOT NULL COMMENT '登录账号（学号/工号）',
    password        VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    real_name       VARCHAR(32)  NOT NULL COMMENT '真实姓名',
    role            VARCHAR(16)  NOT NULL DEFAULT 'STUDENT' COMMENT '角色: ADMIN 管理员 / STUDENT 学生',
    student_no      VARCHAR(32)  DEFAULT NULL COMMENT '学号',
    college         VARCHAR(64)  DEFAULT NULL COMMENT '学院',
    major           VARCHAR(64)  DEFAULT NULL COMMENT '专业班级',
    phone           VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态: 1 正常 / 0 禁用',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_role (role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ---------------------------------------------------------------------
-- 2. 自习室表
--    type: LONG 长期自习室（存放物品、可固定座位、按月考核）
--          SHORT 短期自习室（禁止存放物品、当日 24:00 前清场）
-- ---------------------------------------------------------------------
CREATE TABLE study_room (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(64)  NOT NULL COMMENT '自习室名称',
    room_type       VARCHAR(16)  NOT NULL DEFAULT 'SHORT' COMMENT '类型: LONG 长期 / SHORT 短期',
    location        VARCHAR(128) DEFAULT NULL COMMENT '位置（楼栋楼层）',
    capacity        INT          NOT NULL DEFAULT 0 COMMENT '座位数',
    open_start      VARCHAR(5)   NOT NULL DEFAULT '08:00' COMMENT '开放时间(起) HH:mm',
    open_end        VARCHAR(5)   NOT NULL DEFAULT '23:00' COMMENT '开放时间(止) HH:mm',
    description     VARCHAR(255) DEFAULT NULL COMMENT '自习室说明',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1 开放 / 0 停用',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_type (room_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '自习室表';

-- ---------------------------------------------------------------------
-- 3. 座位表
-- ---------------------------------------------------------------------
CREATE TABLE seat (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    room_id         BIGINT       NOT NULL COMMENT '所属自习室',
    seat_no         VARCHAR(16)  NOT NULL COMMENT '座位编号（如 A-01）',
    row_no          INT          NOT NULL DEFAULT 1 COMMENT '行号（用于座位图布局）',
    col_no          INT          NOT NULL DEFAULT 1 COMMENT '列号（用于座位图布局）',
    has_power       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否带电源插座: 1 是 / 0 否',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1 可用 / 0 停用(损坏维修)',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_seat (room_id, seat_no),
    KEY idx_room (room_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '座位表';

-- ---------------------------------------------------------------------
-- 4. 座位预约记录表（同时记录签到 / 签退）
--    status: WAITING 待签到 / SEATED 已签到在座 / LEFT 已离开(已签退) / CANCELLED 已取消 / TIMEOUT 未签到失效
-- ---------------------------------------------------------------------
CREATE TABLE seat_reservation (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT       NOT NULL COMMENT '预约学生',
    room_id         BIGINT       NOT NULL COMMENT '自习室',
    seat_id         BIGINT       NOT NULL COMMENT '座位',
    reserve_date    DATE         NOT NULL COMMENT '预约日期',
    status          VARCHAR(16)  NOT NULL DEFAULT 'WAITING' COMMENT '状态: WAITING/SEATED/LEFT/CANCELLED/TIMEOUT',
    check_in_time   DATETIME     DEFAULT NULL COMMENT '签到时间',
    check_out_time  DATETIME     DEFAULT NULL COMMENT '签退时间',
    overstay        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否超时未清场(短期自习室 24:00 后)',
    cancel_reason   VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预约创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_date (user_id, reserve_date),
    KEY idx_seat_date (seat_id, reserve_date),
    KEY idx_status (status),
    KEY idx_room_date (room_id, reserve_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '座位预约记录表';

-- ---------------------------------------------------------------------
-- 5. 长期自习室固定座位申请表
--    状态：PENDING 待管理员审核 / ACTIVE 使用中 / REJECTED 未通过 / CANCELLED 已取消
--    长期座位按月考核，签到且签退不足 20 天自动取消
-- ---------------------------------------------------------------------
CREATE TABLE long_term_seat (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT       NOT NULL COMMENT '学生',
    room_id         BIGINT       NOT NULL COMMENT '自习室（长期）',
    seat_id         BIGINT       NOT NULL COMMENT '固定座位',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING 待审核 / ACTIVE 使用中 / REJECTED 未通过 / CANCELLED 已取消',
    apply_time      DATETIME     DEFAULT NULL COMMENT '申请时间',
    review_time     DATETIME     DEFAULT NULL COMMENT '审核时间',
    review_remark   VARCHAR(255) DEFAULT NULL COMMENT '审核意见',
    bind_time       DATETIME     DEFAULT NULL COMMENT '审核通过（绑定生效）时间',
    cancel_time     DATETIME     DEFAULT NULL COMMENT '取消时间',
    cancel_reason   VARCHAR(255) DEFAULT NULL COMMENT '取消原因（系统考核/学生放弃/管理员释放）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_seat_status (seat_id, status),
    KEY idx_room_status (room_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '长期固定座位绑定表';

-- ---------------------------------------------------------------------
-- 6. 月度考核记录表（每月 1 日 02:00 对上月长期座位进行考核）
-- ---------------------------------------------------------------------
CREATE TABLE seat_assessment (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id             BIGINT       NOT NULL COMMENT '学生',
    room_id             BIGINT       NOT NULL COMMENT '自习室',
    actual_seat_id      BIGINT       DEFAULT NULL COMMENT '考核时对应的固定座位绑定 id',
    assess_month        VARCHAR(7)   NOT NULL COMMENT '考核月份 yyyy-MM',
    required_days       INT          NOT NULL DEFAULT 20 COMMENT '要求最少天',
    checkin_days        INT          NOT NULL DEFAULT 0 COMMENT '当月签到天数',
    checkout_days       INT          NOT NULL DEFAULT 0 COMMENT '当月签退天数',
    qualified           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否合格: 1 合格 / 0 不合格',
    cancelled_binding   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否因此取消座位: 1 是 / 0 否',
    remark              VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_month (user_id, room_id, assess_month),
    KEY idx_month (assess_month)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '月度考核记录表';

-- ---------------------------------------------------------------------
-- 7. 公告表
-- ---------------------------------------------------------------------
CREATE TABLE sys_notice (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title           VARCHAR(128) NOT NULL COMMENT '标题',
    content         TEXT         NOT NULL COMMENT '内容',
    publisher_id    BIGINT       DEFAULT NULL COMMENT '发布人',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1 发布 / 0 下线',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '公告表';

-- ---------------------------------------------------------------------
-- 8. 系统参数表
-- ---------------------------------------------------------------------
CREATE TABLE sys_config (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key      VARCHAR(64)  NOT NULL COMMENT '参数键',
    config_value    VARCHAR(255) NOT NULL COMMENT '参数值',
    remark          VARCHAR(255) DEFAULT NULL COMMENT '参数说明',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统参数表';

-- =====================================================================
--  初始数据
-- =====================================================================
-- 密码均为 123456（BCrypt）
--   管理员: admin
--   学生:   20230001 / 20230002 ... / 20230100
INSERT INTO sys_user (username, password, real_name, role, student_no, college, major, phone, status) VALUES
('admin', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '系统管理员', 'ADMIN', NULL, '图书馆', '自习室管理', '13800000000', 1),
('20230001', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '张伟', 'STUDENT', '20230001', '计算机学院', '软件工程2301班', '13800000001', 1),
('20230002', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '王芳', 'STUDENT', '20230002', '计算机学院', '软件工程2301班', '13800000002', 1),
('20230003', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '李娜', 'STUDENT', '20230003', '计算机学院', '软件工程2302班', '13800000003', 1),
('20230004', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '刘洋', 'STUDENT', '20230004', '信息工程学院', '通信工程2301班', '13800000004', 1),
('20230005', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '陈静', 'STUDENT', '20230005', '信息工程学院', '通信工程2301班', '13800000005', 1),
('20230006', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '杨磊', 'STUDENT', '20230006', '经济管理学院', '会计学2301班', '13800000006', 1),
('20230007', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '赵敏', 'STUDENT', '20230007', '经济管理学院', '会计学2301班', '13800000007', 1),
('20230008', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '黄强', 'STUDENT', '20230008', '外国语学院', '英语2301班', '13800000008', 1),
('20230009', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '周婷', 'STUDENT', '20230009', '外国语学院', '英语2301班', '13800000009', 1),
('20230010', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '吴昊', 'STUDENT', '20230010', '计算机学院', '计算机科学2301班', '13800000010', 1),
('20230011', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '徐丽', 'STUDENT', '20230011', '计算机学院', '计算机科学2301班', '13800000011', 1),
('20230012', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '孙杰', 'STUDENT', '20230012', '数学学院', '数学与应用数学2301班', '13800000012', 1),
('20230013', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '马丽', 'STUDENT', '20230013', '数学学院', '数学与应用数学2301班', '13800000013', 1),
('20230014', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '朱峰', 'STUDENT', '20230014', '机械工程学院', '机械设计2301班', '13800000014', 1),
('20230015', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '胡雪', 'STUDENT', '20230015', '机械工程学院', '机械设计2301班', '13800000015', 1),
('20230016', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '林涛', 'STUDENT', '20230016', '计算机学院', '人工智能2301班', '13800000016', 1),
('20230017', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '何雨', 'STUDENT', '20230017', '计算机学院', '人工智能2301班', '13800000017', 1),
('20230018', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '高峰', 'STUDENT', '20230018', '信息工程学院', '电子信息2301班', '13800000018', 1),
('20230019', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '罗丹', 'STUDENT', '20230019', '信息工程学院', '电子信息2301班', '13800000019', 1),
('20230020', '$2a$10$xvHULJcUIfOh5.W6uVqVFu1i20ZeNvd2JLKBu8023dyqSbL7n09aC', '郑凯', 'STUDENT', '20230020', '经济管理学院', '工商管理2301班', '13800000020', 1);

-- 自习室：独立的综合自习楼（非图书馆），共 1-5 层
--   1 层 / 2 层：短期自习室（当日预约，禁止存放物品，24:00 清场）
--   3 层 / 4 层：长期自习室（固定座位，可存放学习用品，按月考核）
--   5 层      ：短期自习室（考研备考）
INSERT INTO study_room (name, room_type, location, capacity, open_start, open_end, description, status) VALUES
('一层 · 短期自习室', 'SHORT', '自习楼 1 层', 24, '00:00', '23:59', '位于自习楼一层，按日预约。禁止存放个人物品，每日 24:00 前须带走随身物品并离开。', 1),
('二层 · 短期自习室', 'SHORT', '自习楼 2 层', 24, '00:00', '23:59', '位于自习楼二层，按日预约。禁止存放个人物品，每日 24:00 前须带走随身物品并离开。', 1),
('三层 · 固定座位自习室', 'LONG', '自习楼 3 层', 24, '00:00', '23:59', '长期固定座位，可存放学习用品。每日 08:30 前签到、21:00-24:00 签退，每月签约不足 20 天将被取消座位。', 1),
('四层 · 固定座位自习室', 'LONG', '自习楼 4 层', 24, '00:00', '23:59', '长期固定座位，安静专注，适合考研、考证长期备考；同样执行每日签到签退与月度考核。', 1),
('五层 · 考研自习室', 'SHORT', '自习楼 5 层', 30, '00:00', '23:59', '位于自习楼五层，座位较多，适合临时自习与小组学习；禁止存放个人物品，24:00 清场。', 1);

-- 座位（4 行 x 6 列 = 24 个；C 区 5 行 x 6 列 = 30 个）
INSERT INTO seat (room_id, seat_no, row_no, col_no, has_power, status)
SELECT r.id,
       CONCAT('R', r.id, '-', LPAD(g.n, 2, '0')) AS seat_no,
       CEIL(g.n / 6) AS row_no,
       (g.n - 1) % 6 + 1 AS col_no,
       IF((g.n - 1) % 6 IN (0, 5), 1, 0) AS has_power,
       1
FROM study_room r
JOIN (WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 30)
      SELECT n FROM seq) g
  ON g.n <= r.capacity
WHERE r.id IN (1, 2, 3, 4, 5);

-- 系统参数
INSERT INTO sys_config (config_key, config_value, remark) VALUES
('checkin.deadline',     '08:30', '长期自习室每日签到截止时间'),
('checkin.early.open',   '07:00', '允许签到最早时间'),
('checkout.start',       '21:00', '长期自习室签退开始时间'),
('checkout.end',         '23:59', '长期自习室签退结束时间'),
('short.clear.deadline', '24:00', '短期自习室清场截止时间（0 点前须带走物品）'),
('long.min.days',        '20',    '长期自习室每月最少签到天数'),
('long.active.month',    '11',    '长期座位开放约定的月份（每月 1 号自动开放）');

-- 公告
INSERT INTO sys_notice (title, content, publisher_id, status) VALUES
('自习室预约使用须知', '1. 学生每日可通过系统预约当日座位；预约后请按时到馆。\n2. 长期自习室（三层、四层）每天 08:30 前完成签到，21:00 - 24:00 完成签退；每月签到且签退天数不足 20 天者，系统将在次月 1 日自动取消其固定座位。\n3. 短期自习室（一层、二层、五层）禁止存放个人物品，每天 24:00 前必须带走物品并离场，超时将记录违规。\n4. 长期固定座位需提交申请，管理员审核通过后生效。\n5. 系统使用服务器时间，如长期座位考核不合格，可重新申请空余座位。', 1, 1),
('长期固定座位申请需管理员审核', '长期自习室（三层、四层）固定座位现已开放申请，每月 1 号自动开放当月名额。学生提交申请后需等待管理员审核，审核通过后座位即刻生效；被拒绝的同学可重新选择其他空余座位申请。', 1, 1);

-- 长期固定座位示例数据（演示用：已审核通过的学生）
INSERT INTO long_term_seat (user_id, room_id, seat_id, status, apply_time, review_time, review_remark, bind_time, create_time) VALUES
(2, 3, 49, 'ACTIVE', NOW(), NOW(), '审核通过', NOW(), NOW()),
(3, 3, 50, 'ACTIVE', NOW(), NOW(), '审核通过', NOW(), NOW()),
(4, 3, 51, 'ACTIVE', NOW(), NOW(), '审核通过', NOW(), NOW()),
(5, 4, 73, 'ACTIVE', NOW(), NOW(), '审核通过', NOW(), NOW()),
(6, 4, 74, 'ACTIVE', NOW(), NOW(), '审核通过', NOW(), NOW()),
-- 待审核示例（管理员端“申请审核”中可见）
(7, 3, 52, 'PENDING', NOW(), NULL, NULL, NULL, NOW()),
(8, 4, 75, 'PENDING', NOW(), NULL, NULL, NULL, NOW());

-- 今日预约示例数据（演示学生端与管理端数据）
--   座位：1层 1-24 / 2层 25-48 / 3层 49-72 / 4层 73-96 / 5层 97-126
INSERT INTO seat_reservation (user_id, room_id, seat_id, reserve_date, status, check_in_time, check_out_time, create_time) VALUES
(2,  3,  49, CURDATE(), 'SEATED',    CONCAT(CURDATE(), ' 08:12:35'), NULL,                         CONCAT(CURDATE(), ' 07:50:00')),
(3,  3,  50, CURDATE(), 'SEATED',    CONCAT(CURDATE(), ' 08:05:10'), NULL,                         CONCAT(CURDATE(), ' 07:45:00')),
(4,  3,  51, CURDATE(), 'LEFT',      CONCAT(CURDATE(), ' 08:20:00'), CONCAT(CURDATE(), ' 21:10:00'), CONCAT(CURDATE(), ' 07:55:00')),
(5,  4,  73, CURDATE(), 'SEATED',    CONCAT(CURDATE(), ' 08:25:00'), NULL,                         CONCAT(CURDATE(), ' 08:00:00')),
(6,  4,  74, CURDATE(), 'WAITING',   NULL, NULL, CONCAT(CURDATE(), ' 08:01:00')),
(9,  1,  5,  CURDATE(), 'SEATED',    CONCAT(CURDATE(), ' 09:10:00'), NULL,                         CONCAT(CURDATE(), ' 09:00:00')),
(10, 1,  6,  CURDATE(), 'LEFT',      CONCAT(CURDATE(), ' 10:00:00'), CONCAT(CURDATE(), ' 21:30:00'), CONCAT(CURDATE(), ' 09:50:00')),
(11, 2,  29, CURDATE(), 'WAITING',   NULL, NULL, CONCAT(CURDATE(), ' 10:10:00')),
(12, 5,  97, CURDATE(), 'SEATED',    CONCAT(CURDATE(), ' 08:30:00'), NULL,                         CONCAT(CURDATE(), ' 08:10:00')),
(13, 5,  98, CURDATE(), 'CANCELLED', NULL, NULL, CONCAT(CURDATE(), ' 08:20:00'));

