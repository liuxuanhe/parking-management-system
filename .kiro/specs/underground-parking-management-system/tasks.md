# Implementation Plan: 地下停车场管理系统

## Overview

本实现计划基于已完成的 requirements.md 和 design.md，采用 Java + Spring Boot 技术栈，按照"基础设施 → 核心模块 → 集成测试"的顺序进行开发。每个任务都包含明确的目标、实现范围、依赖关系和完成标准。

**技术栈：**
- 后端：Spring Boot 3.2.x, MyBatis 3.5.x, MySQL 8.0, Redis 6.x, JDK 17
- 前端：Vue 3 + Ant Design Vue (管理后台), uni-app (业主小程序)
- 测试：JUnit 5, jqwik (属性测试), Testcontainers

**开发原则：**
- 每个任务独立可交付，≤ 8 小时工作量
- 优先实现核心功能，测试任务标记为可选
- 所有代码遵循 camelCase 命名规范
- 数据库字段使用 snake_case 命名规范

## Tasks

- [x] 1. 项目初始化与基础设施搭建
  - 创建 Spring Boot 项目骨架，配置 Maven 依赖
  - 配置 MySQL 数据源和连接池
  - 配置 Redis 连接和序列化
  - 配置 MyBatis 和自动映射策略 (snake_case → camelCase)
  - 创建统一响应格式 ApiResponse {code, message, data, requestId}
  - 创建全局异常处理器 GlobalExceptionHandler
  - 配置日志框架 (Logback)
  - _Requirements: 26.1, 26.2, 26.3, 26.4, 27.1, 27.2, 27.3, 27.4, 28.1, 28.2, 28.3_

- [x] 2. 数据库表结构创建
  - [x] 2.1 创建核心业务表
    - 创建 sys_community (小区表)
    - 创建 sys_admin (管理员表)
    - 创建 sys_owner (业主表)
    - 创建 sys_house (房屋号表)
    - 创建 sys_owner_house_rel (业主房屋号关联表)
    - 创建 sys_car_plate (车牌表，包含 uk_community_house_primary 唯一索引)
    - 创建 parking_config (停车场配置表)
    - _Requirements: 27.1, 27.2, 27.3, 27.4, 27.5, 27.6_


  - [x] 2.2 创建入场记录分表
    - 创建分表模板 parking_car_record_template
    - 创建当前月份和未来3个月的分表 (parking_car_record_yyyymm)
    - 在分表上创建索引 (community_id, enter_time), (community_id, car_number, enter_time), (community_id, house_no, enter_time), (community_id, status, enter_time)
    - 创建自动创建分表的存储过程 create_parking_record_tables()
    - _Requirements: 15.1, 15.2, 15.6, 15.7, 15.8, 15.9, 15.10, 15.11_

  - [x] 2.3 创建 Visitor 相关表
    - 创建 visitor_application (Visitor 申请表)
    - 创建 visitor_authorization (Visitor 授权表)
    - 创建 visitor_session (Visitor 会话表)
    - _Requirements: 7.4, 8.4, 8.5_

  - [x] 2.4 创建审计日志表
    - 创建 sys_operation_log (操作日志表，按月分区)
    - 创建 sys_access_log (访问日志表，按月分区)
    - 配置分区策略 (PARTITION BY RANGE)
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.7_

  - [x] 2.5 创建辅助功能表
    - 创建 parking_stat_daily (每日统计预聚合表)
    - 创建 sys_ip_whitelist (IP 白名单表)
    - 创建 zombie_vehicle (僵尸车辆表)
    - 创建 owner_info_modify_application (敏感信息修改申请表)
    - 创建 export_task (导出任务表)
    - 创建 verification_code (验证码表)
    - 创建 hardware_device (硬件设备表 - 预留)
    - 创建 parking_fee (停车费用表 - 预留)
    - _Requirements: 21.3, 20.4, 22.3, 24.1, 16.4, 1.2, 29.1, 30.1_

- [x] 3. 认证鉴权模块实现
  - [x] 3.1 实现 JWT Token 管理
    - 创建 JwtTokenService 接口和实现类
    - 实现 generateAccessToken() (有效期2小时，包含 userId, role, communityId, houseNo)
    - 实现 generateRefreshToken() (有效期7天)
    - 实现 validateToken() 验证 Token 有效性
    - 实现 revokeToken() 撤销 Token
    - _Requirements: 13.1, 13.2, 13.3_

  - [x] 3.2 实现签名验证机制
    - 创建 SignatureService 接口和实现类
    - 实现签名算法 SHA256(timestamp + nonce + requestBody + secretKey)
    - 实现签名验证逻辑
    - _Requirements: 19.9, 19.10_

  - [x] 3.3 实现防重放机制
    - 创建 AntiReplayService 接口和实现类
    - 实现 timestamp 验证 (5分钟窗口)
    - 实现 nonce 唯一性验证 (Redis 存储，5分钟过期)
    - 返回对应错误码 PARKING_19001, PARKING_19002, PARKING_19003
    - _Requirements: 19.5, 19.6, 19.7, 19.8_

  - [x] 3.4 实现权限校验
    - 创建 AuthorizationService 接口和实现类
    - 实现 checkCommunityAccess() 验证小区访问权限
    - 实现 checkHouseNoAccess() 验证房屋号数据域权限
    - 实现 checkIpWhitelist() 验证 IP 白名单
    - 实现 checkRolePermission() 验证角色权限
    - 返回错误码 PARKING_12001, PARKING_20001
    - _Requirements: 12.5, 12.6, 12.7, 20.2, 20.3_

  - [x] 3.5 实现 API Gateway 拦截器
    - 创建 AuthenticationInterceptor 拦截器
    - 验证 JWT Token
    - 验证签名、timestamp、nonce
    - 验证权限
    - 记录访问日志到 sys_access_log
    - _Requirements: 19.4, 19.5, 18.3, 18.4_


- [x] 4. 通用基础设施组件实现
  - [x] 4.1 实现幂等性组件
    - 创建 IdempotencyService 接口和实现类
    - 实现 checkAndSet() 检查并设置幂等键 (Redis 存储，5分钟过期)
    - 实现 getResult() 获取幂等结果
    - 实现 generateKey() 生成幂等键 (格式: {operationType}:{communityId}:{targetId}:{requestId})
    - _Requirements: 2.8, 5.7, 7.10_

  - [x] 4.2 实现分布式锁组件
    - 创建 DistributedLockService 接口和实现类
    - 实现 tryLock() 获取 Redis 分布式锁 (超时时间5秒)
    - 实现 unlock() 释放锁
    - 实现 executeWithLock() 带锁执行
    - 实现锁获取失败重试机制 (最多3次，间隔100ms)
    - _Requirements: 4.10, 5.10_

  - [x] 4.3 实现缓存管理组件
    - 创建 CacheService 接口和实现类
    - 实现缓存键生成策略 ({resource}:{communityId}:{houseNo})
    - 实现缓存失效策略
    - 配置缓存过期时间 (报表1小时，IP白名单1小时，热点数据30分钟)
    - _Requirements: 21.7, 21.8_

  - [x] 4.4 实现数据脱敏组件
    - 创建 MaskingService 接口和实现类
    - 实现 maskPhoneNumber() 脱敏为 "138****5678" 格式
    - 实现 maskIdCard() 仅显示后4位
    - 在所有查询接口响应中自动执行脱敏
    - _Requirements: 17.1, 17.2, 17.8_

  - [x] 4.5 实现通知服务组件
    - 创建 NotificationService 接口和实现类
    - 实现 sendSubscriptionMessage() 发送订阅消息
    - 实现失败重试机制 (最多3次: 1分钟、5分钟、15分钟)
    - 实现 retryFailedMessages() 定时补偿推送
    - _Requirements: 2.5, 2.6, 7.8_

- [ ] 5. 用户管理模块实现
  - [x] 5.1 实现验证码服务
    - 创建 VerificationCodeService 接口和实现类
    - 实现 send() 发送验证码 (6位数字，5分钟有效)
    - 实现 verify() 验证码校验
    - 实现失败次数统计 (3次失败锁定10分钟)
    - 返回错误码 PARKING_1001, PARKING_1002
    - _Requirements: 1.2, 1.3_

  - [x] 5.2 实现业主注册接口
    - 创建 OwnerController 和 OwnerService
    - 实现 POST /api/v1/owners/register 接口
    - 验证所有字段格式 (手机号、验证码、房屋号、身份证后4位)
    - 创建业主账号，状态设置为 pending
    - 绑定到 community_id 和 house_no
    - 记录操作日志到 sys_operation_log
    - _Requirements: 1.1, 1.4, 1.5, 1.6, 1.7_

  - [x]* 5.3 编写业主注册单元测试
    - 测试字段格式验证
    - 测试验证码失败3次锁定
    - 测试验证码5分钟过期
    - 测试账号创建和数据域绑定
    - 测试同房屋号多业主支持
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.7_

  - [x] 5.4 实现业主审核接口
    - 实现 POST /api/v1/owners/{ownerId}/audit 接口
    - 使用幂等键防止重复审核
    - 使用行级锁 (SELECT FOR UPDATE) 防止并发冲突
    - 验证状态为 pending
    - 更新状态为 approved 或 rejected
    - 记录操作日志 (包含 before/after 状态)
    - 发送订阅消息通知业主
    - 返回错误码 PARKING_2001
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.8_

  - [x]* 5.5 编写业主审核属性测试
    - **Property 4: 审批操作幂等性**
    - **Validates: Requirements 2.8**
    - 测试重复审批请求返回相同结果
    - 测试并发审批只有一个成功
    - _Requirements: 2.8_

  - [ ]* 5.6 编写业主审核单元测试
    - 测试审核通过流程
    - 测试审核驳回流程
    - 测试非 pending 状态拒绝审核
    - 测试审核版本历史保留
    - 测试订阅消息推送和重试
    - _Requirements: 2.1, 2.2, 2.3, 2.7_


  - [x] 5.7 实现管理员账号初始化
    - 实现系统首次启动时生成随机密码
    - 创建超级管理员账号
    - 设置 must_change_password = 1
    - 实现首次登录强制修改密码
    - _Requirements: 13.1, 13.2, 13.3_

  - [x] 5.8 实现管理员登录接口
    - 实现 POST /api/v1/auth/login 接口
    - 验证用户名和密码 (BCrypt)
    - 验证密码强度 (至少8位，包含大小写字母、数字、特殊字符)
    - 实现登录失败次数统计 (5次失败锁定账号)
    - 生成 Access Token 和 Refresh Token
    - 记录登录日志到 sys_access_log
    - _Requirements: 13.4, 13.5, 13.6, 13.8_

  - [x] 5.9 实现业主账号注销接口
    - 实现 POST /api/v1/owners/{ownerId}/disable 接口
    - 仅允许超级管理员执行
    - 验证所有车辆均不在场
    - 更新账号状态为 disabled
    - 更新所有车牌状态为 disabled
    - 记录操作日志 (包含注销原因)
    - 返回错误码 PARKING_14001
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8_

  - [x] 5.10 实现敏感信息修改申请和审批
    - 实现 POST /api/v1/owners/info-modify/apply 接口 (业主申请)
    - 实现 POST /api/v1/owners/info-modify/{applyId}/audit 接口 (物业审批)
    - 创建修改申请记录，状态为 pending
    - 审批通过后更新业主信息
    - 记录操作日志 (包含 before/after 值)
    - 发送订阅消息通知业主
    - 保留历史版本用于审计
    - _Requirements: 24.1, 24.2, 24.3, 24.4, 24.5, 24.6_

- [ ] 6. 车辆管理模块实现
  - [x] 6.1 实现车牌格式验证
    - 创建 CarPlateValidator 工具类
    - 实现中国车牌格式验证正则表达式
    - 支持标准车牌格式 (如 京A12345、京AD12345)
    - _Requirements: 3.2_

  - [ ]* 6.2 编写车牌格式验证属性测试
    - **Property 7: 车牌格式验证**
    - **Validates: Requirements 3.2**
    - 测试有效车牌格式被接受
    - 测试无效车牌格式被拒绝
    - _Requirements: 3.2_

  - [x] 6.3 实现车牌添加接口
    - 创建 VehicleController 和 VehicleService
    - 实现 POST /api/v1/vehicles 接口
    - 验证车牌格式
    - 验证车牌数量 ≤ 5
    - 验证车牌在小区内未被其他业主绑定
    - 创建车牌记录，状态为 normal
    - 失效缓存 vehicles:{communityId}:{houseNo}
    - 记录操作日志
    - 返回错误码 PARKING_3001
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.9_

  - [ ]* 6.4 编写车牌添加单元测试
    - 测试车牌格式验证
    - 测试车牌数量上限 (5个)
    - 测试车牌小区内唯一性
    - 测试缓存失效
    - _Requirements: 3.1, 3.2, 3.3, 3.5_

  - [x] 6.5 实现车牌删除接口
    - 实现 DELETE /api/v1/vehicles/{vehicleId} 接口
    - 验证车辆当前不在场
    - 执行逻辑删除 (设置 is_deleted = 1)
    - 失效缓存
    - 记录操作日志
    - 返回错误码 PARKING_3002
    - _Requirements: 3.6, 3.7, 3.8, 3.9_

  - [ ]* 6.6 编写车牌删除单元测试
    - 测试删除前置条件 (车辆不在场)
    - 测试逻辑删除
    - 测试在场车辆拒绝删除
    - _Requirements: 3.6, 3.7_

  - [x] 6.7 实现车牌查询接口
    - 实现 GET /api/v1/vehicles 接口
    - 使用 community_id + house_no 查询
    - 返回房屋号下所有车牌 (支持同房屋号多业主)
    - 使用 Redis 缓存 (30分钟过期)
    - 执行数据脱敏
    - _Requirements: 11.1, 11.5_


  - [x] 6.8 实现 Primary 车辆设置接口
    - 实现 PUT /api/v1/vehicles/{vehicleId}/primary 接口
    - 获取分布式锁 lock:primary:{communityId}:{houseNo}
    - 使用行级锁 (SELECT FOR UPDATE) 查询房屋号下所有车辆
    - 验证所有车辆均不在场
    - 验证原 Primary 车辆无未完成入场申请
    - 要求业主二次确认
    - 更新旧 Primary 车辆状态为 normal
    - 更新新 Primary 车辆状态为 primary
    - 依赖数据库唯一索引 uk_community_house_primary 确保 one-primary 约束
    - 失效缓存
    - 记录操作日志
    - 返回错误码 PARKING_4001, PARKING_4002
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10_

  - [ ]* 6.9 编写 Primary 车辆设置属性测试
    - **Property 1: One-Primary 约束不变量**
    - **Validates: Requirements 4.1, 4.10**
    - 测试并发设置 Primary 车辆，最终只有1个
    - 测试数据库唯一索引约束
    - _Requirements: 4.1, 4.9, 4.10_

  - [ ]* 6.10 编写 Primary 车辆设置单元测试
    - 测试设置 Primary 前置条件 (所有车辆不在场)
    - 测试原 Primary 车辆状态更新
    - 测试新 Primary 车辆状态更新
    - 测试有车辆在场拒绝切换
    - 测试有未完成入场申请拒绝切换
    - _Requirements: 4.2, 4.3, 4.5, 4.6, 4.7, 4.8_

- [ ] 7. 车位管理与计算模块实现
  - [x] 7.1 实现车位配置管理
    - 创建 ParkingConfigController 和 ParkingConfigService
    - 实现 GET /api/v1/parking/config 接口
    - 实现 PUT /api/v1/parking/config 接口
    - 验证新 total_spaces ≥ 当前在场车辆数
    - 使用乐观锁 (version 字段) 防止并发冲突
    - 修改后重算 Visitor_Available_Spaces
    - 更新不可用的 Visitor 授权状态为 unavailable
    - 失效相关缓存
    - 记录操作日志
    - 返回错误码 PARKING_9002
    - _Requirements: 9.5, 9.6, 9.7, 9.8_

  - [x] 7.2 实现车位数量计算器
    - 创建 ParkingSpaceCalculator 接口和实现类
    - 实现 calculateAvailableSpaces() 计算可用车位数
    - 公式: Available_Spaces = total_spaces - COUNT(status='entered')
    - 实现 calculateVisitorAvailableSpaces() 计算 Visitor 可开放车位数
    - 实现 checkSpaceAvailable() 检查车位是否充足
    - 使用分布式锁确保计算一致性
    - _Requirements: 5.2, 5.3, 5.4, 9.1, 9.3, 9.4_

  - [ ]* 7.3 编写车位数量计算属性测试
    - **Property 2: 车位数量一致性不变量**
    - **Validates: Requirements 5.2, 5.3, 5.4, 5.10, 9.1**
    - 测试并发入场/出场操作下车位数量一致性
    - 测试 Available_Spaces 不出现负数
    - _Requirements: 5.2, 5.10, 9.1_

  - [ ]* 7.4 编写车位配置修改单元测试
    - 测试修改 total_spaces 约束 (≥ 当前在场车辆数)
    - 测试乐观锁并发冲突
    - 测试 Visitor 可开放车位数重算
    - 测试不可用授权状态更新
    - _Requirements: 9.6, 9.7, 9.8_

- [ ] 8. 入场出场模块实现
  - [x] 8.1 实现车辆入场接口
    - 创建 EntryController 和 EntryService
    - 实现 POST /api/v1/parking/entry 接口
    - 检查幂等键 vehicle_entry:{communityId}:{carNumber}:{minute}
    - 查询车牌状态 (primary 或 visitor)
    - 获取分布式锁 lock:space:{communityId}
    - 计算可用车位数
    - 验证 Available_Spaces > 0
    - 创建入场记录，路由到对应月份分表
    - 设置幂等键 (5分钟过期)
    - 失效报表缓存
    - 释放分布式锁
    - 记录操作日志
    - 返回错误码 PARKING_5001
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 15.2_

  - [ ]* 8.2 编写车辆入场属性测试
    - **Property 3: 入场操作幂等性**
    - **Validates: Requirements 5.7**
    - 测试5分钟内重复入场返回相同结果
    - 测试入场记录数量不增加
    - _Requirements: 5.7_


  - [ ]* 8.3 编写车辆入场单元测试
    - 测试 Primary 车辆自动入场
    - 测试车位不足拒绝入场
    - 测试先到先得机制
    - 测试分表路由正确性
    - 测试分布式锁保护
    - _Requirements: 5.1, 5.3, 5.4, 5.9, 5.10, 15.2_

  - [x] 8.4 实现车辆出场接口
    - 实现 POST /api/v1/parking/exit 接口
    - 查找对应的入场记录 (status='entered')
    - 如果找到，更新状态为 exited，记录 exit_time
    - 如果未找到，创建异常出场记录 (status='exit_exception')
    - 通知物业管理员处理异常
    - 如果是 Visitor 车辆，累计停放时长
    - 获取分布式锁更新车位数量
    - 失效报表缓存
    - 记录操作日志
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.7, 6.8_

  - [ ]* 8.5 编写车辆出场单元测试
    - 测试正常出场流程
    - 测试异常出场记录创建
    - 测试 Visitor 时长累计
    - 测试车位数量更新
    - _Requirements: 6.2, 6.3, 6.4, 6.7_

  - [x] 8.6 实现异常出场处理接口
    - 实现 POST /api/v1/parking/exit-exception/handle 接口
    - 物业管理员填写处理原因
    - 更新异常出场记录
    - 记录操作日志
    - _Requirements: 6.5, 6.6_

  - [x] 8.7 实现入场记录查询接口
    - 实现 GET /api/v1/parking/records 接口
    - 使用 community_id + house_no 查询
    - 支持时间范围查询 (必填参数)
    - 根据时间范围计算涉及的月份分表
    - 使用 UNION ALL 合并跨月查询结果
    - 使用游标分页 (基于 enter_time, id)
    - 返回 nextCursor 和 hasMore
    - 执行数据脱敏
    - _Requirements: 11.2, 15.3, 15.4, 15.5, 16.1, 16.2, 16.3_

  - [ ]* 8.8 编写入场记录查询单元测试
    - 测试跨月查询合并正确性
    - 测试游标分页
    - 测试时间范围必填校验
    - 测试数据脱敏
    - _Requirements: 15.3, 15.4, 16.1, 16.2, 17.8_

- [x] 9. Checkpoint - 核心功能验证
  - 验证业主注册→审核→车牌添加→Primary 设置→自动入场→出场完整流程
  - 验证车位数量一致性
  - 验证幂等性和防重放机制
  - 验证数据脱敏
  - 验证审计日志完整性
  - 确保所有测试通过，询问用户是否有问题

- [-] 10. Visitor 权限模块实现
  - [x] 10.1 实现 Visitor 配额管理器
    - 创建 VisitorQuotaManager 接口和实现类
    - 实现 calculateMonthlyUsage() 计算月度配额使用量
    - 公式: SUM(accumulated_duration) WHERE house_no=? AND MONTH(create_time)=?
    - 实现 checkQuotaSufficient() 检查配额是否充足 (< 72小时)
    - 实现 accumulateDuration() 累计 Visitor 停放时长
    - 实现 checkTimeout() 检查超时会话 (≥ 24小时)
    - _Requirements: 7.2, 8.6, 8.7, 8.8, 8.9, 8.10, 10.2_

  - [x] 10.2 实现 Visitor 申请接口
    - 创建 VisitorController 和 VisitorService
    - 实现 POST /api/v1/visitors/apply 接口
    - 验证车牌已绑定到业主账号
    - 检查月度配额 (< 72小时)
    - 检查 Visitor 可开放车位数 (> 0)
    - 创建申请记录，状态为 submitted
    - 返回错误码 PARKING_7001, PARKING_9001
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 9.2_

  - [ ]* 10.3 编写 Visitor 申请单元测试
    - 测试月度配额验证
    - 测试 Visitor 可开放车位数验证
    - 测试车牌绑定验证
    - _Requirements: 7.1, 7.2, 7.3, 9.2_

  - [x] 10.4 实现 Visitor 审批接口
    - 实现 POST /api/v1/visitors/{visitorId}/audit 接口
    - 使用幂等键防止重复审批
    - 使用行级锁防止并发冲突
    - 审批通过: 更新状态为 approved_pending_activation
    - 创建授权记录，设置24小时激活窗口
    - 审批驳回: 更新状态为 rejected，填写驳回原因
    - 发送订阅消息通知业主
    - 记录操作日志
    - _Requirements: 7.5, 7.6, 7.7, 7.8, 7.9, 7.10_


  - [ ]* 10.5 编写 Visitor 审批单元测试
    - 测试审批通过流程
    - 测试审批驳回流程
    - 测试24小时激活窗口设置
    - 测试幂等性保护
    - _Requirements: 7.5, 7.6, 7.7, 7.10_

  - [x] 10.6 实现 Visitor 首次入场激活逻辑
    - 在 EntryService 中添加 Visitor 入场处理
    - 查询授权记录 (status='approved_pending_activation')
    - 验证当前时间在24小时激活窗口内
    - 如果超过窗口，更新状态为 canceled_no_entry，拒绝入场
    - 如果在窗口内，更新状态为 activated
    - 创建 visitor_session 记录，状态为 in_park
    - 创建入场记录
    - 使用分布式锁确保车位一致性
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [ ]* 10.7 编写 Visitor 激活单元测试
    - 测试24小时窗口内激活成功
    - 测试超过窗口自动取消
    - 测试 visitor_session 创建
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 10.8 实现 Visitor 多次进出时长累计
    - 在 EntryService 中处理 Visitor 再次入场
    - 更新 visitor_session 状态为 in_park，记录 last_entry_time
    - 在 ExitService 中处理 Visitor 出场
    - 计算本次停放时长 (exit_time - last_entry_time)
    - 累加到 accumulated_duration
    - 更新 visitor_session 状态为 out_of_park
    - _Requirements: 8.6, 8.7, 8.8_

  - [ ]* 10.9 编写 Visitor 时长累计属性测试
    - **Property 15: Visitor 时长累计正确性**
    - **Validates: Requirements 8.8, 8.11**
    - 测试多次进出累计时长正确
    - 测试累计公式准确性
    - _Requirements: 8.8, 8.11_

  - [ ]* 10.10 编写 Visitor 时长累计单元测试
    - 测试单次进出时长计算
    - 测试多次进出累计
    - 测试状态转换 (in_park ↔ out_of_park)
    - _Requirements: 8.6, 8.7, 8.8_

  - [-] 10.11 实现 Visitor 超时检测定时任务
    - 创建定时任务 (每小时执行)
    - 查询 accumulated_duration ≥ 1440 分钟的会话
    - 创建超时记录到 visitor_timeout_record 表
    - 发送超时提醒给业主和物业
    - 记录操作日志
    - _Requirements: 8.9, 8.10_

  - [ ] 10.12 实现 Visitor 24小时未入场自动取消定时任务
    - 创建定时任务 (每小时执行)
    - 查询 status='approved_pending_activation' 且超过 expire_time 的授权
    - 更新状态为 canceled_no_entry
    - 记录操作日志
    - _Requirements: 8.3_

  - [ ] 10.13 实现 Visitor 权限查询接口
    - 实现 GET /api/v1/visitors 接口
    - 使用 community_id + house_no 查询
    - 返回房屋号下所有 Visitor 申请和授权
    - 执行数据脱敏
    - _Requirements: 11.3_

  - [ ] 10.14 实现月度配额查询接口
    - 实现 GET /api/v1/visitors/quota 接口
    - 计算当月累计使用时长
    - 返回已使用时长和剩余时长
    - 当使用量 ≥ 60小时时发送提醒
    - _Requirements: 10.5, 10.6, 10.7_

  - [ ]* 10.15 编写月度配额单元测试
    - 测试配额计算公式
    - 测试配额重置 (每月1日)
    - 测试配额超限拒绝申请
    - 测试60小时提醒
    - _Requirements: 10.2, 10.3, 10.4, 10.7_

- [ ] 11. 报表统计模块实现
  - [ ] 11.1 实现预聚合表增量更新
    - 在 EntryService 和 ExitService 中添加统计更新逻辑
    - 车辆入场时增量更新 parking_stat_daily.total_entry_count
    - 车辆出场时增量更新 parking_stat_daily.total_exit_count
    - 更新 primary_entry_count 和 visitor_entry_count
    - 更新 avg_parking_duration
    - _Requirements: 21.4_

  - [ ] 11.2 实现预聚合表定时回补任务
    - 创建定时任务 (每日凌晨2点执行)
    - 回补前一日完整统计数据
    - 计算峰值时段和峰值车辆数
    - 更新 parking_stat_daily 表
    - _Requirements: 21.5_


  - [ ] 11.3 实现入场趋势报表接口
    - 创建 ReportController 和 ReportService
    - 实现 GET /api/v1/reports/entry-trend 接口
    - 查询 parking_stat_daily 表
    - 支持按日、周、月聚合
    - 使用 Redis 缓存 (1小时过期)
    - 确保1年数据查询在3秒内返回
    - _Requirements: 21.1, 21.2, 21.7_

  - [ ] 11.4 实现车位使用率报表接口
    - 实现 GET /api/v1/reports/space-usage 接口
    - 计算车位使用率 = 平均在场车辆数 / total_spaces
    - 使用预聚合表提升性能
    - 使用 Redis 缓存
    - _Requirements: 21.1, 21.2_

  - [ ] 11.5 实现峰值时段报表接口
    - 实现 GET /api/v1/reports/peak-hours 接口
    - 查询 parking_stat_daily.peak_hour 和 peak_count
    - 分析高峰时段分布
    - 使用 Redis 缓存
    - _Requirements: 21.1, 21.2_

  - [ ] 11.6 实现僵尸车辆统计报表接口
    - 实现 GET /api/v1/reports/zombie-vehicles 接口
    - 查询 parking_stat_daily.zombie_vehicle_count
    - 统计僵尸车辆数量和处理情况
    - 使用 Redis 缓存
    - _Requirements: 21.1, 22.9_

  - [ ]* 11.7 编写报表性能测试
    - 测试1年数据查询响应时间 ≤ 3秒
    - 测试缓存命中率
    - 测试覆盖索引使用
    - _Requirements: 21.2, 21.6, 21.7_

- [ ] 12. 僵尸车辆识别与处理模块实现
  - [ ] 12.1 实现僵尸车辆识别定时任务
    - 创建定时任务 (每日执行)
    - 查询 status='entered' 且 (当前时间 - enter_time) > 7天的车辆
    - 创建 zombie_vehicle 记录，状态为 unhandled
    - 发送通知给物业管理员
    - _Requirements: 22.1, 22.2, 22.3, 22.4_

  - [ ] 12.2 实现僵尸车辆查询接口
    - 实现 GET /api/v1/zombie-vehicles 接口
    - 查询僵尸车辆列表
    - 支持按状态筛选
    - _Requirements: 22.9_

  - [ ] 12.3 实现僵尸车辆处理接口
    - 实现 POST /api/v1/zombie-vehicles/{zombieId}/handle 接口
    - 支持处理方式: contacted, resolved, ignored
    - 填写对应的处理记录
    - 更新僵尸车辆状态
    - 记录操作日志
    - _Requirements: 22.5, 22.6, 22.7, 22.8, 22.10_

  - [ ]* 12.4 编写僵尸车辆单元测试
    - 测试识别规则 (连续在场 > 7天)
    - 测试处理流程
    - 测试通知机制
    - _Requirements: 22.1, 22.2, 22.4, 22.5_

- [ ] 13. 审计日志模块实现
  - [ ] 13.1 实现操作日志记录切面
    - 创建 OperationLogAspect 切面
    - 拦截所有写操作 (创建、更新、删除)
    - 记录 request_id, community_id, 操作人, IP, 操作时间, 操作类型, before/after 状态
    - 写入 sys_operation_log 表
    - 确保日志不可删除、不可篡改
    - _Requirements: 18.1, 18.2, 18.5, 18.6_

  - [ ] 13.2 实现访问日志记录拦截器
    - 创建 AccessLogInterceptor 拦截器
    - 记录所有查询操作和接口访问
    - 记录 request_id, community_id, 访问人, IP, 访问时间, 接口路径, 查询参数, 响应结果
    - 写入 sys_access_log 表
    - _Requirements: 18.3, 18.4_

  - [ ] 13.3 实现审计日志查询接口
    - 实现 GET /api/v1/audit/operation-logs 接口
    - 实现 GET /api/v1/audit/access-logs 接口
    - 默认查询最近30天日志
    - 支持按 community_id, operator_id, operation_type, 时间范围筛选
    - 使用游标分页
    - _Requirements: 18.8_

  - [ ] 13.4 实现审计日志导出接口
    - 实现 POST /api/v1/audit/logs/export 接口
    - 需要超级管理员权限
    - 使用异步任务处理
    - 记录导出操作到 sys_operation_log
    - _Requirements: 18.9_

  - [ ] 13.5 实现审计日志归档定时任务
    - 创建定时任务 (每月1日执行)
    - 将6个月以上的日志迁移到归档库
    - 验证归档数据完整性
    - 删除在线库中的归档数据
    - 记录归档操作
    - _Requirements: 18.9, 18.10_


- [ ] 14. 导出功能模块实现
  - [ ] 14.1 实现异步导出任务处理器
    - 创建 ExportTaskProcessor 异步任务处理器
    - 使用线程池处理导出任务
    - 按月分片拉取数据并合并
    - 限制单次导出记录数 ≤ 100000 条
    - 生成文件并上传到文件存储
    - 更新 export_task 状态
    - 发送下载链接通知用户
    - _Requirements: 16.4, 16.5, 16.6, 16.7, 16.8_

  - [ ] 14.2 实现入场记录导出接口
    - 实现 POST /api/v1/exports/parking-records 接口
    - 创建导出任务记录
    - 提交异步任务
    - 返回 exportId
    - _Requirements: 16.4_

  - [ ] 14.3 实现导出状态查询接口
    - 实现 GET /api/v1/exports/{exportId}/status 接口
    - 返回任务状态、进度、文件大小、记录数量
    - _Requirements: 16.6_

  - [ ] 14.4 实现导出文件下载接口
    - 实现 GET /api/v1/exports/{exportId}/download 接口
    - 验证文件未过期
    - 返回文件下载流
    - _Requirements: 16.6_

  - [ ] 14.5 实现原始数据导出审批流程
    - 在导出接口中检查 need_raw_data 参数
    - 如果需要原始数据，验证 IP 白名单
    - 验证超级管理员权限
    - 记录导出操作到 sys_operation_log
    - 返回错误码 PARKING_17001
    - _Requirements: 17.3, 17.4, 17.5, 17.6, 17.7_

  - [ ] 14.6 实现数据脱敏导出
    - 在导出处理器中默认执行脱敏
    - 手机号脱敏为 "138****5678"
    - 身份证号仅显示后4位
    - 仅原始数据导出时跳过脱敏
    - _Requirements: 16.9, 17.1, 17.2, 17.3_

- [ ] 15. IP 白名单管理模块实现
  - [ ] 15.1 实现 IP 白名单配置接口
    - 创建 IpWhitelistController 和 IpWhitelistService
    - 实现 POST /api/v1/ip-whitelist 接口 (添加)
    - 实现 DELETE /api/v1/ip-whitelist/{id} 接口 (删除)
    - 实现 GET /api/v1/ip-whitelist 接口 (查询)
    - 仅允许超级管理员操作
    - 记录操作日志
    - 发送通知给所有超级管理员
    - _Requirements: 20.4, 20.5, 20.6_

  - [ ] 15.2 实现 IP 白名单缓存管理
    - 使用 Redis 缓存 IP 白名单 (1小时过期)
    - 配置变更时主动失效缓存
    - _Requirements: 20.7_

  - [ ] 15.3 实现高危操作 IP 验证
    - 在 AuthorizationService 中实现 checkIpWhitelist()
    - 验证操作类型和 IP 地址
    - 支持 IP 段 (CIDR 格式)
    - 返回错误码 PARKING_20001
    - _Requirements: 20.1, 20.2, 20.3_

- [ ] 16. 限流与防护模块实现
  - [ ] 16.1 实现 IP 级限流
    - 创建 RateLimitInterceptor 拦截器
    - 使用 Redis 实现令牌桶算法
    - 注册接口: 每个 IP 每小时最多10次
    - 登录接口: 每个 IP 每小时最多20次
    - 返回错误码 PARKING_19004
    - _Requirements: 19.1, 19.2_

  - [ ] 16.2 实现账号级限流
    - 管理端接口: 每个账号每分钟最多100次
    - 使用 Redis 计数器
    - 返回错误码 PARKING_19005
    - _Requirements: 19.3_

  - [ ]* 16.3 编写限流单元测试
    - 测试 IP 级限流
    - 测试账号级限流
    - 测试限流阈值
    - _Requirements: 19.1, 19.2, 19.3_

- [ ] 17. 批量操作模块实现
  - [ ] 17.1 实现批量审核业主接口
    - 实现 POST /api/v1/owners/batch-audit 接口
    - 限制每次最多处理50条记录
    - 验证所有记录状态为 pending
    - 跳过状态不正确的记录并标注
    - 返回成功数量和失败数量
    - 记录每条记录的操作日志
    - 使用事务确保原子性
    - 使用幂等键防止重复执行
    - _Requirements: 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7_

  - [ ] 17.2 实现批量审批 Visitor 接口
    - 实现 POST /api/v1/visitors/batch-audit 接口
    - 限制每次最多处理50条记录
    - 验证所有记录状态为 submitted
    - 使用事务和幂等键
    - 记录操作日志
    - _Requirements: 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7_


  - [ ]* 17.3 编写批量操作单元测试
    - 测试批量审核流程
    - 测试记录数量限制
    - 测试状态验证
    - 测试事务原子性
    - 测试幂等性保护
    - _Requirements: 23.1, 23.2, 23.3, 23.6, 23.7_

- [ ] 18. Checkpoint - 后端核心功能完成
  - 验证所有后端接口功能正常
  - 验证完整业务流程: 注册→审核→车牌→Primary→入场→出场
  - 验证 Visitor 完整流程: 申请→审批→激活→多次进出→超时提醒→月度配额
  - 验证并发场景: 车位一致性、Primary 约束、幂等性
  - 验证安全机制: 签名、防重放、限流、IP 白名单
  - 验证审计日志完整性
  - 确保所有测试通过，询问用户是否有问题

- [ ] 19. 业主小程序端页面实现
  - [ ] 19.1 实现注册登录页面
    - 创建注册页面 (手机号、验证码、小区、房屋号、身份证后4位)
    - 创建登录页面 (手机号、验证码)
    - 调用后端注册和登录接口
    - 存储 Access Token 和 Refresh Token
    - _Requirements: 1.1, 1.2, 1.3_

  - [ ] 19.2 实现车牌管理页面
    - 创建车牌列表页面
    - 创建添加车牌页面 (车牌号、品牌、型号、颜色)
    - 创建删除车牌确认对话框
    - 调用后端车牌管理接口
    - 显示车牌状态 (normal, primary)
    - _Requirements: 3.1, 3.2, 3.6_

  - [ ] 19.3 实现 Primary 设置页面
    - 创建 Primary 车辆选择页面
    - 显示二次确认对话框
    - 调用后端 Primary 设置接口
    - 显示设置结果和错误提示
    - _Requirements: 4.2, 4.4_

  - [ ] 19.4 实现 Visitor 申请页面
    - 创建 Visitor 申请页面 (选择车牌、填写申请原因)
    - 显示月度配额使用情况
    - 调用后端 Visitor 申请接口
    - 显示申请结果
    - _Requirements: 7.1, 7.2_

  - [ ] 19.5 实现入场记录查询页面
    - 创建入场记录列表页面
    - 支持时间范围筛选
    - 支持下拉刷新和上拉加载更多 (游标分页)
    - 显示车牌号、入场时间、出场时间、停放时长
    - _Requirements: 11.2, 16.2, 16.3_

  - [ ] 19.6 实现月度配额查询页面
    - 创建月度配额展示页面
    - 显示已使用时长和剩余时长
    - 显示配额使用进度条
    - 显示超限提醒
    - _Requirements: 10.5, 10.6_

- [ ] 20. 管理后台页面实现
  - [ ] 20.1 实现登录页面
    - 创建管理员登录页面 (用户名、密码)
    - 调用后端登录接口
    - 存储 Access Token
    - 首次登录强制修改密码
    - _Requirements: 13.3, 13.4_

  - [ ] 20.2 实现业主审核页面
    - 创建业主审核列表页面
    - 显示待审核业主信息 (手机号、房屋号、身份证后4位)
    - 创建审核对话框 (通过/驳回、驳回原因)
    - 调用后端审核接口
    - 支持批量审核
    - _Requirements: 2.1, 2.2, 23.1_

  - [ ] 20.3 实现车辆管理页面
    - 创建车辆列表页面
    - 支持按小区、房屋号、车牌号筛选
    - 显示车辆状态 (normal, primary)
    - 支持查看车辆详情
    - _Requirements: 3.9_

  - [ ] 20.4 实现 Visitor 审批页面
    - 创建 Visitor 审批列表页面
    - 显示待审批申请 (业主、车牌号、申请原因)
    - 创建审批对话框 (通过/驳回、驳回原因)
    - 调用后端审批接口
    - 支持批量审批
    - _Requirements: 7.5, 7.6, 23.1_

  - [ ] 20.5 实现车位配置页面
    - 创建车位配置页面
    - 显示当前配置 (总车位数、预留车位数、月度配额、单次时长限制)
    - 创建修改配置对话框
    - 调用后端配置修改接口
    - 显示修改结果和错误提示
    - _Requirements: 9.5, 9.6_


  - [ ] 20.6 实现报表页面
    - 创建报表展示页面
    - 实现入场趋势图表 (ECharts 折线图)
    - 实现车位使用率图表 (ECharts 柱状图)
    - 实现峰值时段图表 (ECharts 热力图)
    - 支持时间范围选择 (日、周、月、年)
    - 支持导出报表数据
    - _Requirements: 21.1, 21.2_

  - [ ] 20.7 实现僵尸车辆处理页面
    - 创建僵尸车辆列表页面
    - 显示僵尸车辆信息 (车牌号、入场时间、连续天数)
    - 创建处理对话框 (contacted/resolved/ignored、处理记录)
    - 调用后端处理接口
    - 支持按状态筛选
    - _Requirements: 22.5, 22.6, 22.7, 22.8_

  - [ ] 20.8 实现审计日志查询页面
    - 创建审计日志查询页面
    - 支持按操作类型、操作人、时间范围筛选
    - 显示操作日志详情 (before/after 值)
    - 支持导出审计日志
    - _Requirements: 18.8, 18.9_

- [ ] 21. 集成测试与端到端测试
  - [ ]* 21.1 编写完整注册审核流程集成测试
    - 测试注册→审核→房屋号绑定→车牌→Primary 设置→自动入场→出场完整流程
    - 验证每个步骤的状态转换
    - 验证审计日志完整性
    - _Requirements: 31.13_

  - [ ]* 21.2 编写 Visitor 完整流程集成测试
    - 测试 Visitor 申请→审批→24小时内首次入场激活→多次进出累计→累计24小时超时提醒→月度72小时超额规则完整流程
    - 验证时长累计正确性
    - 验证配额管理正确性
    - _Requirements: 31.14_

  - [ ]* 21.3 编写车位并发一致性集成测试
    - 测试并发入场和出场操作
    - 验证车位数量一致性
    - 验证 Available_Spaces 不出现负数
    - 验证分布式锁保护
    - _Requirements: 31.4_

  - [ ]* 21.4 编写 Primary 约束并发集成测试
    - 测试并发设置 Primary 车辆
    - 验证最终只有1个 Primary 车辆
    - 验证数据库唯一索引约束
    - 验证行级锁保护
    - _Requirements: 31.10_

  - [ ]* 21.5 编写幂等性集成测试
    - 测试入场操作幂等性
    - 测试审批操作幂等性
    - 测试批量操作幂等性
    - 验证幂等键机制
    - _Requirements: 31.5, 31.12_

  - [ ]* 21.6 编写防重放机制集成测试
    - 测试 timestamp 验证
    - 测试 nonce 唯一性验证
    - 测试签名验证
    - 验证错误码返回
    - _Requirements: 31.5_

  - [ ]* 21.7 编写跨小区越权访问集成测试
    - 测试跨小区数据访问被拒绝
    - 测试 community_id 强制校验
    - 验证错误码 PARKING_12001
    - _Requirements: 31.16_

  - [ ]* 21.8 编写同房屋号多业主数据同步集成测试
    - 测试同房屋号下多个业主账号
    - 验证数据查询一致性
    - 验证数据修改可见性
    - 验证缓存失效机制
    - _Requirements: 31.17_

  - [ ]* 21.9 编写重复入场事件幂等处理集成测试
    - 测试5分钟内重复入场事件
    - 验证幂等键机制
    - 验证入场记录数量不增加
    - _Requirements: 31.18_

  - [ ]* 21.10 编写无入场记录出场异常处理集成测试
    - 测试无入场记录直接出场
    - 验证异常出场记录创建
    - 验证物业通知
    - _Requirements: 31.19_

  - [ ]* 21.11 编写总车位动态修改边界测试
    - 测试修改 total_spaces 导致 Visitor 名额变化
    - 验证不可用授权状态更新
    - 验证修改约束 (≥ 当前在场车辆数)
    - _Requirements: 31.15_

  - [ ]* 21.12 编写批量操作与审计完整性集成测试
    - 测试批量审核操作
    - 验证每条记录的操作日志
    - 验证事务原子性
    - 验证幂等性保护
    - _Requirements: 31.20_

- [ ] 22. 性能测试
  - [ ]* 22.1 编写接口响应时间性能测试
    - 使用 JMeter 测试所有接口
    - 验证平均响应时间 ≤ 500ms
    - 验证 P95 响应时间 ≤ 1s
    - _Requirements: 25.1, 25.7_

  - [ ]* 22.2 编写报表查询性能测试
    - 测试1年数据报表查询
    - 验证响应时间 ≤ 3s
    - 验证 P95 响应时间 ≤ 5s
    - _Requirements: 21.2, 25.2_

  - [ ]* 22.3 编写并发性能测试
    - 模拟1000并发用户
    - 测试入场/出场接口
    - 测试 Primary 设置接口
    - 验证系统稳定性
    - _Requirements: 25.1_


- [ ] 23. 属性测试覆盖
  - [ ]* 23.1 编写 Property 5: 审核状态前置条件属性测试
    - **Property 5: 审核状态前置条件**
    - **Validates: Requirements 2.1, 2.2, 2.3**
    - 测试非 pending 状态拒绝审核
    - _Requirements: 2.3_

  - [ ]* 23.2 编写 Property 6: 车牌数量上限约束属性测试
    - **Property 6: 车牌数量上限约束**
    - **Validates: Requirements 3.1, 3.5**
    - 测试车牌数量 ≤ 5
    - 测试第6个车牌被拒绝
    - _Requirements: 3.1, 3.5_

  - [ ]* 23.3 编写 Property 8: 车牌社区内唯一性属性测试
    - **Property 8: 车牌社区内唯一性**
    - **Validates: Requirements 3.3**
    - 测试同小区内车牌唯一性
    - _Requirements: 3.3_

  - [ ]* 23.4 编写 Property 9: 删除车牌前置条件属性测试
    - **Property 9: 删除车牌前置条件**
    - **Validates: Requirements 3.6, 3.7**
    - 测试在场车辆拒绝删除
    - _Requirements: 3.6, 3.7_

  - [ ]* 23.5 编写 Property 10: Primary 切换前置条件属性测试
    - **Property 10: Primary 切换前置条件**
    - **Validates: Requirements 4.2, 4.7**
    - 测试有车辆在场拒绝切换
    - _Requirements: 4.2, 4.7_

  - [ ]* 23.6 编写 Property 11: 入场记录状态转换属性测试
    - **Property 11: 入场记录状态转换**
    - **Validates: Requirements 6.2**
    - 测试状态转换规则 (entered → exited)
    - _Requirements: 6.2_

  - [ ]* 23.7 编写 Property 12: 异常出场记录创建属性测试
    - **Property 12: 异常出场记录创建**
    - **Validates: Requirements 6.3**
    - 测试无入场记录创建异常出场记录
    - _Requirements: 6.3_

  - [ ]* 23.8 编写 Property 13: Visitor 月度配额验证属性测试
    - **Property 13: Visitor 月度配额验证**
    - **Validates: Requirements 7.2, 7.3, 10.2**
    - 测试配额超限拒绝申请
    - _Requirements: 7.2, 7.3, 10.2_

  - [ ]* 23.9 编写 Property 14: Visitor 激活窗口验证属性测试
    - **Property 14: Visitor 激活窗口验证**
    - **Validates: Requirements 8.2, 8.3**
    - 测试超过24小时窗口拒绝入场
    - _Requirements: 8.2, 8.3_

  - [ ]* 23.10 编写 Property 16: Visitor 可开放车位数验证属性测试
    - **Property 16: Visitor 可开放车位数验证**
    - **Validates: Requirements 9.2**
    - 测试车位不足拒绝申请
    - _Requirements: 9.2_

  - [ ]* 23.11 编写 Property 17: 车位配置修改约束属性测试
    - **Property 17: 车位配置修改约束**
    - **Validates: Requirements 9.6, 9.7**
    - 测试新 total_spaces ≥ 当前在场车辆数
    - _Requirements: 9.6, 9.7_

  - [ ]* 23.12 编写 Property 18: 月度配额重置属性测试
    - **Property 18: 月度配额重置**
    - **Validates: Requirements 10.4**
    - 测试每月1日配额重置
    - _Requirements: 10.4_

  - [ ]* 23.13 编写 Property 19: 房屋号数据域查询一致性属性测试
    - **Property 19: 房屋号数据域查询一致性**
    - **Validates: Requirements 11.1, 11.2, 11.3, 11.5**
    - 测试同房屋号下所有业主查询相同数据
    - _Requirements: 11.1, 11.2, 11.3, 11.5_

  - [ ]* 23.14 编写 Property 20: 房屋号数据域修改可见性属性测试
    - **Property 20: 房屋号数据域修改可见性**
    - **Validates: Requirements 11.6**
    - 测试修改后立即可见
    - _Requirements: 11.6_

  - [ ]* 23.15 编写 Property 21: 多小区数据隔离属性测试
    - **Property 21: 多小区数据隔离**
    - **Validates: Requirements 12.5, 12.7**
    - 测试跨小区访问被拒绝
    - _Requirements: 12.5, 12.7_

  - [ ]* 23.16 编写 Property 22: 入场记录分表路由正确性属性测试
    - **Property 22: 入场记录分表路由正确性**
    - **Validates: Requirements 15.2**
    - 测试记录路由到正确月份分表
    - _Requirements: 15.2_

  - [ ]* 23.17 编写 Property 23: 跨月查询合并正确性属性测试
    - **Property 23: 跨月查询合并正确性**
    - **Validates: Requirements 15.3**
    - 测试跨月查询结果正确合并
    - _Requirements: 15.3_

  - [ ]* 23.18 编写 Property 24: 数据脱敏规则属性测试
    - **Property 24: 数据脱敏规则**
    - **Validates: Requirements 17.1, 17.2**
    - 测试手机号脱敏格式
    - 测试身份证号脱敏格式
    - _Requirements: 17.1, 17.2_

  - [ ]* 23.19 编写 Property 25: 审计日志完整性属性测试
    - **Property 25: 审计日志完整性**
    - **Validates: Requirements 1.6, 18.1, 18.2**
    - 测试所有写操作记录日志
    - 测试日志包含所有必需字段
    - _Requirements: 1.6, 18.1, 18.2_

  - [ ]* 23.20 编写 Property 26: 防重放时间窗口验证属性测试
    - **Property 26: 防重放时间窗口验证**
    - **Validates: Requirements 19.5, 19.6**
    - 测试超出5分钟窗口拒绝请求
    - _Requirements: 19.5, 19.6_

  - [ ]* 23.21 编写 Property 27: Nonce 唯一性验证属性测试
    - **Property 27: Nonce 唯一性验证**
    - **Validates: Requirements 19.7, 19.8**
    - 测试重复 nonce 拒绝请求
    - _Requirements: 19.7, 19.8_

  - [ ]* 23.22 编写 Property 28: 签名验证正确性属性测试
    - **Property 28: 签名验证正确性**
    - **Validates: Requirements 19.9, 19.10**
    - 测试签名不匹配拒绝请求
    - _Requirements: 19.9, 19.10_

  - [ ]* 23.23 编写 Property 29: 僵尸车辆识别规则属性测试
    - **Property 29: 僵尸车辆识别规则**
    - **Validates: Requirements 22.1**
    - 测试连续在场 > 7天识别为僵尸车辆
    - _Requirements: 22.1_

  - [ ]* 23.24 编写 Property 30: 统一响应格式属性测试
    - **Property 30: 统一响应格式**
    - **Validates: Requirements 26.1, 26.2, 26.3, 26.4**
    - 测试所有响应包含必需字段
    - _Requirements: 26.1, 26.2, 26.3, 26.4_

  - [ ]* 23.25 编写 Property 31: 注册信息格式验证属性测试
    - **Property 31: 注册信息格式验证**
    - **Validates: Requirements 1.1**
    - 测试所有字段格式验证
    - _Requirements: 1.1_

  - [ ]* 23.26 编写 Property 32: 账号创建与数据域绑定属性测试
    - **Property 32: 账号创建与数据域绑定**
    - **Validates: Requirements 1.4, 1.5**
    - 测试账号正确绑定到 community_id 和 house_no
    - _Requirements: 1.4, 1.5_

  - [ ]* 23.27 编写 Property 33: 同房屋号多业主支持属性测试
    - **Property 33: 同房屋号多业主支持**
    - **Validates: Requirements 1.7**
    - 测试同房屋号允许多个业主账号
    - _Requirements: 1.7_


- [ ] 24. 最终 Checkpoint - 系统完整性验证
  - 运行所有单元测试，确保覆盖率 ≥ 90%
  - 运行所有属性测试，确保所有 33 个 Correctness Properties 通过
  - 运行所有集成测试，确保完整业务流程正常
  - 运行性能测试，确保接口响应时间达标
  - 验证所有错误码正确返回
  - 验证审计日志完整性
  - 验证数据脱敏正确执行
  - 验证安全机制 (签名、防重放、限流、IP 白名单) 正常工作
  - 验证并发场景 (车位一致性、Primary 约束、幂等性) 正常工作
  - 确保所有测试通过，询问用户是否有问题

- [ ] 25. 部署与文档
  - [ ] 25.1 编写部署文档
    - 编写 Docker Compose 配置文件
    - 编写 Kubernetes 部署配置
    - 编写环境变量配置说明
    - 编写数据库初始化脚本
    - 编写部署步骤文档

  - [ ] 25.2 编写 API 文档
    - 使用 Swagger/OpenAPI 生成 API 文档
    - 编写接口调用示例
    - 编写错误码说明文档
    - 编写认证鉴权说明

  - [ ] 25.3 编写运维文档
    - 编写监控配置文档
    - 编写日志查询文档
    - 编写备份恢复文档
    - 编写故障排查文档

  - [ ] 25.4 编写用户手册
    - 编写业主小程序使用手册
    - 编写管理后台使用手册
    - 编写常见问题解答

## Notes

- 任务标记 `*` 的为可选测试任务，可根据项目进度和资源情况选择性实施
- 所有核心实现任务必须完成，确保系统功能完整
- 每个 Checkpoint 任务是关键验证点，必须确保所有测试通过后再继续
- 属性测试使用 jqwik 框架，每个属性至少测试 100 次迭代
- 集成测试使用 Testcontainers 提供真实的 MySQL 和 Redis 环境
- 性能测试使用 JMeter，模拟真实用户并发场景
- 所有代码必须遵循命名规范：Java 代码 camelCase，数据库字段 snake_case
- 所有接口必须返回统一响应格式 {code, message, data, requestId}
- 所有关键操作必须记录审计日志
- 所有敏感数据必须执行脱敏处理

## Test Coverage Summary

**单元测试覆盖：**
- 业主注册与审核流程
- 车牌管理与 Primary 设置
- 车辆入场出场流程
- Visitor 权限申请与审批
- Visitor 激活与时长累计
- 车位配置与计算
- 报表统计
- 僵尸车辆识别与处理
- 审计日志记录
- 导出功能
- 批量操作

**属性测试覆盖（33 个 Properties）：**
- Property 1-33: 覆盖所有核心业务规则和不变量

**集成测试覆盖：**
- 完整注册审核流程
- 完整 Visitor 流程
- 车位并发一致性
- Primary 约束并发
- 幂等性机制
- 防重放机制
- 跨小区越权访问
- 同房屋号多业主数据同步
- 重复入场事件幂等处理
- 无入场记录出场异常处理
- 总车位动态修改边界
- 批量操作与审计完整性

**性能测试覆盖：**
- 接口响应时间测试
- 报表查询性能测试
- 并发性能测试

## Implementation Order Rationale

1. **基础设施优先**：先搭建项目骨架、数据库、认证鉴权、通用组件，为后续开发提供基础
2. **核心模块次之**：实现用户管理、车辆管理、入场出场等核心业务模块
3. **扩展功能再次**：实现 Visitor 权限、报表统计、僵尸车辆等扩展功能
4. **前端页面最后**：后端接口完成后再实现前端页面，确保接口稳定
5. **测试贯穿始终**：每个模块实现后立即编写测试，确保质量
6. **Checkpoint 验证**：在关键节点设置 Checkpoint，确保阶段性目标达成

## Risk Mitigation

**并发风险：**
- 使用分布式锁保护车位计算
- 使用行级锁保护 Primary 设置
- 使用幂等键防止重复操作
- 使用数据库唯一索引确保约束

**性能风险：**
- 使用 Redis 缓存热点数据
- 使用预聚合表优化报表查询
- 使用游标分页避免深分页
- 使用覆盖索引优化查询

**安全风险：**
- 使用签名验证防止篡改
- 使用防重放机制防止重放攻击
- 使用限流机制防止恶意攻击
- 使用 IP 白名单保护高危操作
- 使用数据脱敏保护隐私

**数据风险：**
- 使用审计日志确保可追溯
- 使用事务确保数据一致性
- 使用备份确保数据安全
- 使用归档确保数据保留
