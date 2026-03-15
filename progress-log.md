# 项目进度日志

[2026-03-15 01:29] TASK-3.5 - DONE - 实现 AuthenticationInterceptor 拦截器，验证 JWT Token、签名、timestamp、nonce，更新 WebConfig 注册拦截器并排除公开路径，编写 16 个单元测试全部通过，全量 90 个测试通过

[2026-03-15 01:32] TASK-4.1 - DONE - 实现 IdempotencyService 接口和实现类（checkAndSet/getResult/generateKey），使用 Redis setIfAbsent 原子操作，5分钟过期，编写 13 个单元测试全部通过

[2026-03-15 01:38] TASK-4.2 - DONE - 实现 DistributedLockService 分布式锁组件（接口+实现+13个单元测试全部通过），使用 Redis setIfAbsent 获取锁、Lua 脚本原子释放锁、带重试的 executeWithLock 模板方法

[2026-03-15 01:42] TASK-4.3 - DONE - 实现 CacheService 缓存管理组件（接口+实现+28个单元测试全部通过），支持 get/set/delete/exists/deleteByPrefix/generateKey，预定义 REPORT_TTL=1小时、IP_WHITELIST_TTL=1小时、HOTSPOT_TTL=30分钟

[2026-03-15 01:47] TASK-4.4 - DONE - 实现 MaskingService 数据脱敏组件（接口+实现+14个单元测试全部通过），支持手机号脱敏（138****5678）、身份证号脱敏（仅显示后4位）、通用 mask 方法，null/空字符串返回原值，长度不足时全部*替换

[2026-03-15 01:54] TASK-4.5 - DONE - 实现 NotificationService 通知服务组件：创建 SubscriptionMessage 消息模型、NotificationService 接口和 NotificationServiceImpl 实现类，包含 sendSubscriptionMessage() 发送订阅消息、失败重试机制（最多3次：1/5/15分钟间隔）、retryFailedMessages() 定时补偿推送（@Scheduled 每小时执行），编写 17 个单元测试全部通过，全量 175 个测试通过。

[2026-03-15 01:57] TASK-5.1 - DONE - 实现 VerificationCodeService 验证码服务（接口+实现+9个单元测试全部通过），支持 send() 发送6位数字验证码（Redis 存储，5分钟过期）、verify() 验证码校验（成功后删除验证码和失败计数）、失败次数统计（3次失败锁定10分钟，返回 PARKING_1001）、验证码过期返回 PARKING_1002

[2026-03-15 02:03] TASK-5.2 - DONE - 实现业主注册接口 POST /api/v1/owners/register：创建 OwnerRegisterRequest/OwnerRegisterResponse DTO、OwnerMapper/OwnerHouseRelMapper/HouseMapper 接口及 XML 映射文件、OwnerService 接口和 OwnerServiceImpl 实现类、OwnerController 控制器，支持字段格式验证（手机号11位、验证码6位、身份证后4位）、验证码校验、房屋号存在性验证、创建 pending 状态业主账号、绑定 community_id + house_no、同房屋号多业主注册，编写 7 个单元测试全部通过，全量 191 个测试通过

[2026-03-15 08:12] TASK-5.7 - DONE - 实现管理员账号初始化：添加 spring-security-crypto 依赖、创建 AdminMapper 接口及 XML 映射文件（insert/selectByUsername/countByRole/updatePassword）、创建 PasswordValidator 密码强度验证工具类（至少8位+大小写+数字+特殊字符）、创建 AdminInitService 接口和 AdminInitServiceImpl 实现类（ApplicationRunner 启动时检查并创建 super_admin，生成16位随机强密码，BCrypt 加密，mustChangePassword=1），编写 14 个单元测试全部通过

[2026-03-15 08:18] TASK-5.8 - DONE - 实现管理员登录接口 POST /api/v1/auth/login 和修改密码接口 POST /api/v1/auth/change-password，包含 BCrypt 密码验证、5次失败锁定、JWT Token 生成、密码强度校验，扩展 ErrorCode（13004/13005/13006）和 AdminMapper（selectById/updateLoginSuccess/updateLoginFail/lockAccount），编写 14 个单元测试全部通过

[2026-03-15 08:24] TASK-5.9 - DONE - 实现业主账号注销接口 POST /api/v1/owners/{ownerId}/disable，包含车辆在场校验、账号禁用、车牌批量禁用、操作日志记录，5个单元测试全部通过

[2026-03-15 08:29] TASK-5.10 - DONE - 实现敏感信息修改申请和审批接口（apply/audit），含 DTO、Mapper、Service、Controller 及 12 个单元测试全部通过

[2026-03-15 08:33] TASK-6.1 - DONE - 实现 CarPlateValidator 车牌格式验证工具类，支持普通车牌（7位）和新能源车牌（8位），包含 isValid() 和 validate() 方法，编写 31 个单元测试全部通过

[2026-03-15 09:08] TASK-6.3 - DONE - 实现车牌添加接口 POST /api/v1/vehicles：创建 VehicleAddRequest/VehicleAddResponse DTO、VehicleService 接口和 VehicleServiceImpl 实现类、VehicleController 控制器，扩展 CarPlateMapper（countByOwner/countByCarNumberInCommunity/insert）及 XML 映射，支持车牌格式验证、数量上限校验（≤5，PARKING_3001）、小区内唯一性校验、创建 normal 状态记录、失效缓存 vehicles:{communityId}:{houseNo}、操作日志预留，编写 8 个单元测试全部通过

[2026-03-15 09:12] TASK-6.5 - DONE - 实现车牌删除接口 DELETE /api/v1/vehicles/{vehicleId}：扩展 CarPlateMapper（selectById/logicalDelete）及 XML 映射，VehicleService 添加 deleteVehicle() 方法，VehicleServiceImpl 实现删除逻辑（查询车牌→验证不在场→逻辑删除→失效缓存→操作日志预留），VehicleController 添加 DELETE 端点，PARKING_3002 错误码已存在无需添加，编写 6 个删除单元测试全部通过，全量 14 个 VehicleServiceTest 测试通过

[2026-03-15 09:18] TASK-6.7 - DONE - 实现车牌查询接口 GET /api/v1/vehicles：创建 VehicleQueryResponse DTO、CarPlateMapper.xml 添加 selectByHouse SQL（按 community_id + house_no 查询未删除车牌）、VehicleService 添加 listVehicles() 方法、VehicleServiceImpl 实现查询逻辑（Redis 缓存30分钟过期 + MaskingService 手机号脱敏 + OwnerMapper 查询业主信息）、VehicleController 添加 GET 端点，支持同房屋号多业主场景，编写 7 个 listVehicles 单元测试全部通过，全量 21 个 VehicleServiceTest 测试通过

[2026-03-15 09:26] TASK-5.3 - DONE - 补充业主注册单元测试：新增验证码失败3次锁定（PARKING_1001）和验证码5分钟过期（PARKING_1002）两个测试用例，共8个测试全部通过

[2026-03-15 09:42] TASK-5.5 - DONE - 编写审批操作幂等性属性测试（Property 4），使用 jqwik 验证重复审批请求被幂等处理、首次请求正常执行、并发审批只有一个成功，共3个属性测试全部通过

[2026-03-15 10:00] TASK-6.8 - DONE - 实现 Primary 车辆设置接口 PUT /api/v1/vehicles/{vehicleId}/primary：新增 SetPrimaryRequest DTO、CarPlateMapper 添加 selectForUpdate/updatePrimaryToNormal/updateStatusToPrimary 及 XML 映射、VehicleServiceImpl 实现分布式锁+行级锁双重保护的 Primary 切换逻辑（验证所有车辆不在场、旧 Primary 改 normal、新车辆设 primary、失效缓存）、VehicleController 添加 PUT 端点，编写 8 个单元测试全部通过，全量 29 个 VehicleServiceTest 测试通过

[2026-03-15 10:12] TASK-7.1 - DONE - 实现车位配置管理接口：创建 ParkingConfigController、ParkingConfigService、ParkingConfigMapper 及 XML 映射文件、ParkingConfigUpdateRequest/ParkingConfigResponse DTO，实现 GET/PUT /api/v1/parking/config 接口，包含 total_spaces ≥ 在场车辆数校验（PARKING_9002）、乐观锁并发冲突检测、Visitor_Available_Spaces 重算、缓存失效、操作日志记录，编写 11 个单元测试全部通过

[2026-03-15 11:10] TASK-7.2 - DONE - 实现车位数量计算器 ParkingSpaceCalculator：新增接口和实现类，提供 calculateAvailableSpaces()、calculateVisitorAvailableSpaces()、checkSpaceAvailable() 三个方法，使用分布式锁确保车位计算一致性，Available_Spaces 不会出现负数，编写 15 个单元测试全部通过

[2026-03-15 10:25] TASK-7.2 - DONE - 实现车位数量计算器：创建 ParkingSpaceCalculator 接口和 ParkingSpaceCalculatorImpl 实现类，实现 calculateAvailableSpaces/calculateVisitorAvailableSpaces/checkSpaceAvailable 三个方法，使用分布式锁确保计算一致性，Available_Spaces 保证不为负数，编写 15 个单元测试全部通过

[2026-03-15 11:18] TASK-8.1 - DONE - 实现车辆入场接口 POST /api/v1/parking/entry，包含幂等检查、车牌查询、分布式锁车位校验、分表路由、报表缓存失效，9 个单元测试全部通过

[2026-03-15 10:40] TASK-8.1 - DONE - 实现车辆入场接口 POST /api/v1/parking/entry：创建 EntryController/EntryService/EntryServiceImpl、ParkingCarRecordMapper 及 XML、EntryRequest/EntryResponse DTO，实现幂等键检查→车牌状态查询→分布式锁保护→Available_Spaces 校验→分表路由写入入场记录→缓存失效，编写 9 个单元测试全部通过

[2026-03-15 11:24] TASK-8.4 - DONE - 实现车辆出场接口 POST /api/v1/parking/exit：创建 ExitRequest/ExitResponse DTO、ExitService 接口和 ExitServiceImpl 实现类、ExitController 控制器，ParkingCarRecordMapper 新增 selectEnteredRecord/updateExitRecord 方法及 XML 映射。支持正常出场（查找入场记录→更新 status='exited'→记录 exit_time→计算停放时长）和异常出场（无入场记录→创建 exit_exception 记录→通知物业管理员），使用分布式锁 lock:space:{communityId} 确保车位一致性，失效报表缓存，Visitor 车辆时长累计预留接口，支持跨月分表查询（当前月+上个月），编写 10 个单元测试全部通过，全量 349 个测试通过

[2026-03-15 10:55] TASK-8.4 - DONE - 实现车辆出场接口 POST /api/v1/parking/exit：创建 ExitController/ExitService/ExitServiceImpl、ExitRequest/ExitResponse DTO，ParkingCarRecordMapper 新增 selectEnteredRecord/updateExitRecord 方法，实现正常出场（更新 status='exited'、计算停放时长）和异常出场（创建 exit_exception 记录、通知物业），支持跨月分表查询，编写 10 个单元测试全部通过
[2026-03-15 11:29] TASK-8.6 - DONE - 实现异常出场处理接口 POST /api/v1/parking/exit-exception/handle，新增 ExitExceptionHandleRequest DTO、Mapper 方法、Service 实现、Controller 端点、错误码 PARKING_5002/PARKING_5003，编写5个单元测试全部通过。

[2026-03-15 11:10] TASK-8.6 - DONE - 实现异常出场处理接口 POST /api/v1/parking/exit-exception/handle：创建 ExitExceptionHandleRequest DTO，ParkingCarRecordMapper 新增 selectById/updateExceptionHandle 方法，ExitServiceImpl 实现 handleExitException 逻辑（验证状态为 exit_exception、更新 handler_admin_id/handle_time/handle_remark），新增 PARKING_5002/PARKING_5003 错误码，编写 5 个单元测试全部通过

[2026-03-15 11:36] TASK-8.7 - DONE - 实现入场记录查询接口 GET /api/v1/parking/records：新增 ParkingRecordQueryRequest/ParkingRecordQueryResponse DTO、ParkingRecordService 接口及实现类、ParkingCarRecordMapper 跨月分表 UNION ALL 查询、EntryController 查询端点，支持时间范围必填校验、游标分页（enter_time + id）、数据脱敏，编写 15 个单元测试全部通过，全量 369 个测试通过

[2026-03-15 11:30] TASK-8.7 - DONE - 实现入场记录查询接口 GET /api/v1/parking/records：创建 ParkingRecordQueryRequest/ParkingRecordQueryResponse DTO、ParkingRecordService/ParkingRecordServiceImpl，ParkingCarRecordMapper 新增 selectRecordsByUnionAll 方法（foreach + UNION ALL 跨月分表查询），支持游标分页（enter_time + id DESC）、时间范围必填校验、车牌号数据脱敏，编写 15 个单元测试全部通过

[2026-03-15 11:37] TASK-9 - DONE - Checkpoint 核心功能验证：运行 mvn test 全量测试，369 个测试全部通过（0 失败、0 错误、0 跳过）。已验证核心功能模块覆盖：业主注册/审核/注销、车牌添加/删除/查询/Primary 设置、车辆入场/出场/异常出场处理/入场记录查询、车位配置管理/车位数量计算、幂等性（IdempotencyService + 属性测试）、防重放（AntiReplayService）、数据脱敏（MaskingService）、审计日志预留、JWT 认证/签名验证/权限校验、分布式锁/缓存管理/通知服务、敏感信息修改申请与审批。共 25 个测试文件覆盖 common/controller/exception/interceptor/service 各层。

[2026-03-15 11:45] TASK-10.1 - DONE - 实现 VisitorQuotaManager 接口及实现类，含 VisitorSessionMapper，11 个单元测试全部通过

[2026-03-15 11:48] TASK-10.2 - DONE - 实现 Visitor 申请接口，含 VisitorService、VisitorController、Mapper 及 XML，6 个单元测试全部通过

[2026-03-15 11:52] TASK-10.4 - DONE - 实现 Visitor 审批接口 POST /api/v1/visitors/{visitorId}/audit，含幂等键、行级锁、24小时激活窗口授权、驳回原因、订阅消息通知，6个单元测试全部通过

[2026-03-15 11:56] TASK-10.6 - DONE - 实现 Visitor 首次入场激活逻辑：在 EntryServiceImpl 中添加 handleVisitorEntry 方法，支持24小时激活窗口校验、授权激活、visitor_session 创建、窗口过期自动取消（PARKING_8001），3个新增测试全部通过，全量12个 EntryServiceTest 通过

[2026-03-15 12:00] TASK-10.8 - DONE - 实现 Visitor 多次进出时长累计：EntryServiceImpl 添加再次入场逻辑（out_of_park→in_park），ExitServiceImpl 添加 handleVisitorExit 方法（累加 accumulated_duration→out_of_park），新增 VisitorSessionMapper 查询方法，4个新增测试全部通过，全量29个测试通过

[2026-03-15 12:02] TASK-10.11 - DONE - 实现 Visitor 超时检测定时任务 VisitorTimeoutScheduler（每小时执行），检测累计≥1440分钟的会话并发送通知，4个单元测试全部通过

[2026-03-15 12:08] TASK-10.13 - DONE - 实现 Visitor 权限查询接口 GET /api/v1/visitors，按 community_id + house_no 查询申请和授权记录，车牌号脱敏，3个新增测试全部通过，共9个 VisitorServiceTest 通过

[2026-03-15 12:11] TASK-10.14 - DONE - 实现月度配额查询接口 GET /api/v1/visitors/quota，返回已使用/剩余时长及接近超限提醒，4个新增测试全部通过，共13个 VisitorServiceTest 通过

[2026-03-15 12:23] TASK-11.2 - DONE - 实现预聚合表定时回补任务（StatReconciliationScheduler），每日凌晨2点回补前一日统计数据，新增 Mapper 统计方法和 upsert 回补方法，5个单元测试全部通过

[2026-03-15 12:29] TASK-11.4 - DONE - 实现车位使用率报表接口 GET /api/v1/reports/space-usage，新增 SpaceUsageResponse DTO，计算使用率（上限100%），Redis 缓存1小时，5个新增测试全部通过，共9个 ReportServiceTest 通过

[2026-03-15 12:32] TASK-11.5 - DONE - 实现峰值时段报表接口 GET /api/v1/reports/peak-hours，新增 PeakHoursResponse DTO，查询 peak_hour/peak_count，Redis 缓存1小时，4个新增测试全部通过，共13个 ReportServiceTest 通过

[2026-03-15 12:35] TASK-11.6 - DONE - 实现僵尸车辆统计报表接口 GET /api/v1/reports/zombie-vehicles，新增 ZombieVehicleStatResponse DTO，查询 zombie_vehicle_count/exception_exit_count，Redis 缓存1小时，4个新增测试全部通过，共17个 ReportServiceTest 通过

[2026-03-15 12:39] TASK-12.1 - DONE - 实现 Zombie_Vehicle 识别定时任务（每日凌晨3点），扫描连续在场超过7天的车辆，创建 zombie_vehicle 记录并通知物业，新增 ZombieVehicleMapper/XML、selectZombieCandidates 方法，5个单元测试全部通过

[2026-03-15 12:43] TASK-12.2 - DONE - 实现僵尸车辆查询接口 GET /api/v1/zombie-vehicles，支持按 communityId 和 status 筛选，车牌号脱敏（mask 前2后2），3个单元测试全部通过

[2026-03-15 12:47] TASK-12.3 - DONE - 实现僵尸车辆处理接口 POST /api/v1/zombie-vehicles/{zombieId}/handle，支持 contacted/resolved/ignored 三种处理方式，新增错误码 PARKING_22001/22002/22003，6个新增测试全部通过，共9个 ZombieVehicleServiceTest 通过

[2026-03-15 12:49] TASK-13.1 - DONE - 实现操作日志记录切面 OperationLogAspect，新增 @OperationLogAnnotation 注解、OperationLogMapper 及 XML，支持成功/失败日志记录、IP 获取、参数截断，5个单元测试全部通过

[2026-03-15 12:52] TASK-13.2 - DONE - 实现访问日志记录拦截器 AccessLogInterceptor，新增 AccessLogMapper 及 XML，在 WebConfig 注册（order=2），支持 IP 获取和响应时间计算，5个单元测试全部通过

[2026-03-15 12:54] TASK-13.3 - DONE - 实现审计日志查询接口 GET /api/v1/audit/operation-logs 和 GET /api/v1/audit/access-logs，支持多条件筛选，4个单元测试全部通过

[2026-03-15 12:58] TASK-13.4 - DONE - 实现审计日志导出接口 POST /api/v1/audit/logs/export，新增 ExportTaskMapper.xml、AuditLogService 导出方法、AuditLogController 导出端点，3个新增测试全部通过，共7个 AuditLogServiceTest 通过

[2026-03-15 13:01] TASK-13.5 - DONE - 实现审计日志归档定时任务 AuditLogArchiveScheduler（每月1日凌晨4点），分批迁移6个月以上日志到归档表，扩展 Mapper 归档方法，6个单元测试全部通过

[2026-03-15 13:03] TASK-14.1 - DONE - 实现异步导出任务处理器 ExportTaskProcessor，支持三种导出类型，限制单次 ≤ 100000 条，72小时过期，7个单元测试全部通过

[2026-03-15 13:05] TASK-14.2 - DONE - 实现入场记录导出接口 POST /api/v1/exports/parking-records，新增 ExportService/ExportServiceImpl/ExportController，4个单元测试全部通过

[2026-03-15 13:06] TASK-14.3 - DONE - 实现导出状态查询接口 GET /api/v1/exports/{exportId}/status，复用已有 ExportService 方法，测试通过

[2026-03-15 13:10] TASK-14.4 - DONE - 实现导出文件下载接口 GET /api/v1/exports/{exportId}/download，验证任务存在/已完成/未过期，4个新增测试全部通过，共8个 ExportServiceTest 通过

[2026-03-15 13:12] TASK-14.5 - DONE - 实现原始数据导出审批流程 POST /api/v1/exports/parking-records/raw，验证超级管理员权限和 IP 白名单，3个新增测试全部通过，共11个 ExportServiceTest 通过

[2026-03-15 13:17] TASK-14.6 - DONE - 实现数据脱敏导出，ExportTaskProcessor 根据 needRawData 决定是否脱敏，默认脱敏手机号和身份证号，needRawData=1 跳过脱敏，5个新增测试全部通过，共12个 ExportTaskProcessorTest 通过

[2026-03-15 13:19] TASK-15.1 - DONE - 实现 IP 白名单配置接口 POST/DELETE/GET /api/v1/ip-whitelist，新增 Mapper/Service/Controller，变更时失效缓存，5个单元测试全部通过

[2026-03-15 13:21] TASK-15.2 - DONE - 实现 IP 白名单缓存管理：修改 AuthorizationServiceImpl 注入 IpWhitelistMapper，替换 loadWhitelistFromDatabase 存根为真实数据库查询，28 个 AuthorizationServiceTest 测试全部通过

[2026-03-15 13:24] TASK-15.3 - DONE - 高危操作 IP 验证功能已在 TASK 3.4 和 15.2 中完整实现（checkIpWhitelist 支持精确 IP + CIDR 匹配 + Redis 缓存 + 数据库加载），28 个测试全部通过，无需额外代码变更

[2026-03-15 13:28] TASK-16.1 - DONE - 实现 IP 级限流拦截器 RateLimitInterceptor，使用 Redis 计数器实现滑动窗口限流（注册10次/小时、登录20次/小时、其他60次/分钟），新增 PARKING_19004 错误码，WebConfig 注册拦截器，10个单元测试全部通过

[2026-03-15 13:30] TASK-16.2 - DONE - 实现账号级限流拦截器 AccountRateLimitInterceptor，每账号每分钟最多 100 次请求，错误码 PARKING_19005，7 个单元测试全部通过

[2026-03-15 13:40] TASK-17.1 - DONE - 实现批量审核业主接口 POST /api/v1/owners/batch-audit，支持最多50条记录批量审核，事务原子性+幂等键+逐条操作日志
[2026-03-15 13:40] TASK-17.2 - DONE - 实现批量审批 Visitor 接口 POST /api/v1/visitors/batch-audit，审批通过自动创建24小时激活窗口授权记录
[2026-03-15 13:40] TASK-17 - DONE - 批量操作模块完成，含 BatchAuditService 服务层、DTO、Mapper 扩展、10个单元测试全部通过

[2026-03-15 13:42] TASK-18 - DONE - 后端核心功能 Checkpoint 验证完成。全部 519 个测试通过（0 失败），覆盖以下模块：用户管理（注册/审核/注销/敏感信息修改）、车辆管理（添加/删除/查询/Primary 设置）、入场出场（Primary 入场/出场/异常出场/分表路由/游标分页）、Visitor 权限（申请/审批/激活/时长累计/超时检测/月度配额）、车位计算（可用车位/Visitor 可开放车位）、报表统计（入场趋势/车位使用率/峰值时段/僵尸车辆）、僵尸车辆识别与处理、审计日志（操作日志切面/访问日志拦截器/归档）、导出功能（异步导出/脱敏/原始数据审批）、IP 白名单、限流（IP 级/账号级）、批量操作、安全机制（JWT/签名/防重放/幂等性/分布式锁）。属性测试（jqwik）验证了审批操作幂等性。

[2026-03-15 14:00] TASK-19.1 - DONE - 创建 owner-app uni-app 项目骨架，实现登录页面（手机号+验证码）、注册页面（含小区选择、房屋号、身份证后4位）、首页菜单、统一请求封装（Token 管理、签名参数、自动刷新）
[2026-03-15 14:10] TASK-19.2 - DONE - 实现车牌列表页面（状态标签、删除操作）和添加车牌页面（车牌号、品牌、型号、颜色表单）
[2026-03-15 14:15] TASK-19.3 - DONE - 实现 Primary 车辆设置页面，支持车辆选择、当前 Primary 标识、二次确认弹窗
[2026-03-15 14:20] TASK-19.4 - DONE - 实现 Visitor 申请页面，包含月度配额进度条、普通车辆选择、申请原因输入
[2026-03-15 14:25] TASK-19.5 - DONE - 实现入场记录查询页面，支持时间范围筛选、游标分页、状态标签、停放时长格式化
[2026-03-15 14:30] TASK-19.6 - DONE - 实现月度配额查询页面，包含环形进度、进度条、超限提醒、配额说明
