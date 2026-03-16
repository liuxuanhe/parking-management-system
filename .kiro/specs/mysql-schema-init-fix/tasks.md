# Implementation Plan

- [x] 1. 编写 Bug Condition 探索性测试
  - **Property 1: Bug Condition** - Schema 初始化脚本 Docker 兼容性缺陷检测
  - **CRITICAL**: 此测试必须在未修复的代码上 FAIL — 失败即确认 bug 存在
  - **DO NOT** 在测试失败时尝试修复测试或代码
  - **NOTE**: 此测试编码了预期行为 — 修复后测试通过即验证修复正确
  - **GOAL**: 通过静态分析原始 `schema.sql`，发现触发 bug 的反例
  - **Scoped PBT Approach**: 针对确定性 bug，将属性范围限定到具体的失败模式以确保可复现性
  - 测试文件：`parking-service/src/test/java/com/parking/sql/SchemaInitBugConditionPropertyTest.java`
  - 使用 jqwik 属性测试框架，读取 `schema.sql` 文件内容进行静态分析
  - 属性 1：验证 SQL 文件不包含 `DELIMITER` 关键字（来自 `isBugCondition` 中的 `hasDELIMITER` 条件）
  - 属性 2：验证 SQL 文件不包含 `CREATE DATABASE` 语句（来自 `isBugCondition` 中的 `hasCreateDB` 条件）
  - 属性 3：验证 SQL 文件不包含 `USE parking_db` 语句（来自 `isBugCondition` 中的 `hasUSE` 条件）
  - 属性 4：验证所有分区表（`sys_operation_log`、`sys_access_log`）的主键包含分区列（来自 `isBugCondition` 中的 `hasInvalidPartitionPK` 条件）
  - 在未修复代码上运行测试
  - **EXPECTED OUTCOME**: 测试 FAIL（这是正确的 — 证明 bug 存在）
  - 记录发现的反例：原始文件包含 `DELIMITER $`、`CREATE DATABASE IF NOT EXISTS parking_db`、`USE parking_db`、`sys_operation_log` 主键仅为 `(id)` 不含 `operation_time`、`sys_access_log` 主键仅为 `(id)` 不含 `access_time`
  - 任务完成标准：测试已编写、已运行、失败已记录
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 2. 编写保全性属性测试（在实施修复前）
  - **Property 2: Preservation** - 未修改表定义一致性
  - **IMPORTANT**: 遵循观察优先方法论
  - 测试文件：`parking-service/src/test/java/com/parking/sql/SchemaPreservationPropertyTest.java`
  - 使用 jqwik 属性测试框架，对比原始和修复后的 `schema.sql` 中未受影响表的 DDL
  - 观察：在未修复代码上提取所有未受 bug 影响的表定义作为基线
  - 观察：基础业务表（`sys_community`、`sys_admin`、`sys_owner`、`sys_house`、`sys_owner_house_rel`、`sys_car_plate` CREATE TABLE 部分、`parking_config`）的 DDL
  - 观察：分表模板（`parking_car_record_template`）及静态分表（`parking_car_record_202603` ~ `parking_car_record_202606`）的 DDL
  - 观察：Visitor 表（`visitor_application`、`visitor_authorization`、`visitor_session`）的 DDL
  - 观察：辅助功能表（`parking_stat_daily`、`sys_ip_whitelist`、`zombie_vehicle`、`owner_info_modify_application`、`export_task`、`verification_code`、`hardware_device`、`parking_fee`）的 DDL
  - 编写属性测试：对于所有未受 bug 影响的表名，从 `@Provide` 生成表名，验证修复后的 DDL 与原始 DDL 完全一致
  - 属性测试通过随机选取表名组合，提供比手动单元测试更强的保全性保证
  - 在未修复代码上运行测试（此时原始文件和"修复后文件"相同，测试应通过）
  - **EXPECTED OUTCOME**: 测试 PASS（确认基线行为已捕获）
  - 任务完成标准：测试已编写、已运行、在未修复代码上通过
  - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [x] 3. 修复 schema.sql 初始化脚本

  - [x] 3.1 实施修复
    - 修改文件：`parking-service/src/main/resources/sql/schema.sql`
    - 修改 1：移除文件头部的 `CREATE DATABASE IF NOT EXISTS parking_db ...` 和 `USE parking_db;` 语句
    - 修改 2：完全删除 `DELIMITER $ ... DELIMITER ;` 包裹的 `create_parking_record_tables()` 存储过程定义（含 `DELIMITER` 行本身）
    - 修改 3：将 `sys_operation_log` 的 `id BIGINT PRIMARY KEY AUTO_INCREMENT` 改为 `id BIGINT NOT NULL AUTO_INCREMENT`，并在索引列表末尾、`PARTITION BY` 之前添加 `PRIMARY KEY (id, operation_time)`
    - 修改 4：将 `sys_access_log` 的 `id BIGINT PRIMARY KEY AUTO_INCREMENT` 改为 `id BIGINT NOT NULL AUTO_INCREMENT`，并在索引列表末尾、`PARTITION BY` 之前添加 `PRIMARY KEY (id, access_time)`
    - 修改 5：确认 `sys_car_plate` 的两条 `ALTER TABLE` 语句语法在 MySQL 8.0 中正确无误，如无问题则保持不变
    - _Bug_Condition: isBugCondition(sqlFile) where hasDELIMITER OR hasCreateDB OR hasUSE OR hasInvalidPartitionPK_
    - _Expected_Behavior: 修复后文件不含 DELIMITER / CREATE DATABASE / USE，分区表主键包含分区列，所有 28 张表成功创建_
    - _Preservation: 未受 bug 影响的表定义（基础业务表、分表、Visitor 表、辅助功能表）保持不变_
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 3.2 验证 Bug Condition 探索性测试现在通过
    - **Property 1: Expected Behavior** - Schema 初始化脚本 Docker 兼容性
    - **IMPORTANT**: 重新运行任务 1 中的同一测试 — 不要编写新测试
    - 任务 1 的测试编码了预期行为，当测试通过时即确认预期行为已满足
    - 运行 `SchemaInitBugConditionPropertyTest`
    - **EXPECTED OUTCOME**: 测试 PASS（确认 bug 已修复）
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 3.3 验证保全性测试仍然通过
    - **Property 2: Preservation** - 未修改表定义一致性
    - **IMPORTANT**: 重新运行任务 2 中的同一测试 — 不要编写新测试
    - 运行 `SchemaPreservationPropertyTest`
    - **EXPECTED OUTCOME**: 测试 PASS（确认无回归）
    - 确认修复后所有保全性测试仍然通过（无回归）

- [x] 4. 检查点 - 确保所有测试通过
  - 运行完整测试套件：`cd parking-service && mvn test`
  - 确保 `SchemaInitBugConditionPropertyTest` 通过（bug 已修复）
  - 确保 `SchemaPreservationPropertyTest` 通过（无回归）
  - 确保项目中其他已有测试不受影响
  - 如有问题，询问用户
