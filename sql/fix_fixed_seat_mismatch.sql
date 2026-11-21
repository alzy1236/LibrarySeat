-- ============================================================
--  修复演示数据：固定座位绑定 / 历史预约 的「房间-座位」不一致
--  问题：long_term_seat.room_id 与 seat.room_id 不匹配，
--        导致一/二/五层（短期）出现「固定」座位，三层反而没有。
-- ============================================================

-- 1) 固定座位绑定改到「本房间内」的座位（同尾号，尽量保留语义）
UPDATE `long_term_seat` SET `seat_id` = (SELECT id FROM `seat` WHERE room_id = 3 AND seat_no = 'R3-10') WHERE id = 1;
UPDATE `long_term_seat` SET `seat_id` = (SELECT id FROM `seat` WHERE room_id = 3 AND seat_no = 'R3-11') WHERE id = 2;
UPDATE `long_term_seat` SET `seat_id` = (SELECT id FROM `seat` WHERE room_id = 3 AND seat_no = 'R3-12') WHERE id = 3;
UPDATE `long_term_seat` SET `seat_id` = (SELECT id FROM `seat` WHERE room_id = 4 AND seat_no = 'R4-15') WHERE id = 4;
UPDATE `long_term_seat` SET `seat_id` = (SELECT id FROM `seat` WHERE room_id = 4 AND seat_no = 'R4-16') WHERE id = 5;
UPDATE `long_term_seat` SET `seat_id` = (SELECT id FROM `seat` WHERE room_id = 3 AND seat_no = 'R3-14') WHERE id = 6;
UPDATE `long_term_seat` SET `seat_id` = (SELECT id FROM `seat` WHERE room_id = 4 AND seat_no = 'R4-14') WHERE id = 7;

-- 2) 历史预约：座位的实际房间与预约房间不一致时，换成「预约房间内的同号座位」
UPDATE `seat_reservation` r
JOIN `seat` s ON s.id = r.seat_id
JOIN `seat` s2 ON s2.room_id = r.room_id
              AND s2.seat_no = CONCAT('R', r.room_id, '-', SUBSTRING_INDEX(s.seat_no, '-', -1))
SET r.seat_id = s2.id
WHERE s.room_id <> r.room_id;

-- 3) 校验：短期自习室(1/2/5)不应存在生效中的固定座位
SELECT r.id, r.name, r.room_type, COUNT(b.id) AS active_bindings
FROM `study_room` r
LEFT JOIN `long_term_seat` b ON b.room_id = r.id AND b.status = 'ACTIVE'
GROUP BY r.id, r.name, r.room_type
ORDER BY r.id;

-- 4) 校验：绑定房间与座位房间是否已一致
SELECT b.id, b.room_id AS bind_room, s.room_id AS seat_room, s.seat_no, b.status
FROM `long_term_seat` b JOIN `seat` s ON s.id = b.seat_id
ORDER BY b.id;