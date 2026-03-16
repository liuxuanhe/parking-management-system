-- ============================================================
-- 测试种子数据
-- 用途：在本地开发环境中快速创建可用的测试账号
-- 执行方式：连接 MySQL 后执行 source test-seed-data.sql
-- ============================================================

-- 1. 插入测试小区
INSERT INTO sys_community (id, community_name, community_code, province, city, district, address, contact_person, contact_phone, status)
VALUES (1, '测试小区A', 'COMM_TEST_A', '广东省', '深圳市', '南山区', '科技园路1号', '张物业', '13800000001', 'active')
ON DUPLICATE KEY UPDATE community_name = VALUES(community_name);

-- 2. 插入测试房屋
INSERT INTO sys_house (id, community_id, house_no, building, unit, floor, room, status)
VALUES (1, 1, 'A-1-101', 'A栋', '1单元', '1', '101', 'normal')
ON DUPLICATE KEY UPDATE house_no = VALUES(house_no);

INSERT INTO sys_house (id, community_id, house_no, building, unit, floor, room, status)
VALUES (2, 1, 'A-1-102', 'A栋', '1单元', '1', '102', 'normal')
ON DUPLICATE KEY UPDATE house_no = VALUES(house_no);

-- 3. 插入测试业主（状态直接设为 approved，跳过审核流程）
--    手机号: 13800138000, 身份证后4位: 1234
INSERT INTO sys_owner (id, community_id, house_no, phone_number, id_card_last4, real_name, status, account_status)
VALUES (1, 1, 'A-1-101', '13800138000', '1234', '测试业主', 'approved', 'active')
ON DUPLICATE KEY UPDATE status = 'approved', account_status = 'active';

-- 4. 插入业主-房屋关联
INSERT INTO sys_owner_house_rel (id, community_id, owner_id, house_no, relation_type)
VALUES (1, 1, 1, 'A-1-101', 'owner')
ON DUPLICATE KEY UPDATE relation_type = VALUES(relation_type);

-- 5. 插入停车场配置（总车位 100）
INSERT INTO parking_config (id, community_id, total_spaces, visitor_quota_hours, visitor_max_stay_hours, visitor_activation_hours, zombie_threshold_days, status)
VALUES (1, 1, 100, 72, 24, 24, 7, 'active')
ON DUPLICATE KEY UPDATE total_spaces = VALUES(total_spaces);

-- 6. 插入测试管理员（用于 Admin_Portal 登录）
--    用户名: admin  密码: Admin@123
--    BCrypt 哈希值对应明文 Admin@123
INSERT INTO sys_admin (id, community_id, username, password, real_name, phone_number, role, status, must_change_password)
VALUES (1, 0, 'admin', '$2a$10$hN..JPmqpbt0BxGGhrgRtOn2KgRgRxjDGah0DCJ9RnXbbDIaVCrMm', '超级管理员', '13800000000', 'super_admin', 'active', 0)
ON DUPLICATE KEY UPDATE password = VALUES(password), status = 'active';

-- 7. 插入物业管理员（绑定测试小区A）
--    用户名: property1  密码: Admin@123
INSERT INTO sys_admin (id, community_id, username, password, real_name, phone_number, role, status, must_change_password)
VALUES (2, 1, 'property1', '$2a$10$hN..JPmqpbt0BxGGhrgRtOn2KgRgRxjDGah0DCJ9RnXbbDIaVCrMm', '物业管理员A', '13800000002', 'property_admin', 'active', 0)
ON DUPLICATE KEY UPDATE password = VALUES(password), status = 'active';

-- ============================================================
-- 测试账号汇总：
--
-- 【Admin_Portal 管理后台】
--   Super_Admin:    用户名 admin      密码 Admin@123
--   Property_Admin: 用户名 property1  密码 Admin@123
--
-- 【Owner_App 业主端】
--   业主手机号: 13800138000
--   小区: 测试小区A (community_id=1)
--   房屋号: A-1-101
--
--   登录方式：使用万能验证码 000000 即可登录（开发环境已配置）
--   或者调用 POST /api/v1/auth/owner-login
--   请求体: {"phoneNumber": "13800138000", "verificationCode": "000000"}
-- ============================================================
