# MySQL Schema 初始化修复 Bugfix Design

## Overview

`schema.sql` 作为 Docker Compose 启动时 MySQL 8.0 容器的初始化脚本（挂载到 `docker-entrypoint-initdb.d`），因多个 SQL 语法和兼容性问题导致大量表创建失败。核心问题包括：`DELIMITER $` 在 Docker entrypoint 的 shell 管道中解析异常、分区表主键未包含分区列违反 MySQL 约束、以及 `CREATE DATABASE` / `USE` 与 Docker 环境变量 `MYSQL_DATABASE` 冲突。修复策略是直接修改 `schema.sql` 文件，移除不兼容语法、修正分区表主键定义，确保所有表在 Docker 初始化时一次性成功创建。

## Glossary

- **Bug_Condition (C)**：`schema.sql` 中导致 Docker entrypoint 执行失败的 SQL 语法和结构问题，包括 `DELIMITER` 语法、冗余的 `CREATE DATABASE` / `USE`、分区表主键缺失分区列
- **Property (P)**：修复后的 `schema.sql` 在 Docker entrypoint 环境中能成功创建所有预期的表、索引、生成列
- **Preservation**：未涉及 bug 的表定义（基础业务表、分表模板、Visitor 表、辅助功能表）在修复前后保持完全一致
- **`schema.sql`**：位于 `parking-service/src/main/resources/sql/schema.sql` 的数据库初始化脚本
- **`docker-entrypoint-initdb.d`**：MySQL Docker 镜像的初始化目录，容器首次启动时自动执行其中的 `.sql` 文件
- **`MYSQL_DATABASE`**：Docker Compose 中配置的环境变量，MySQL 容器启动时自动创建该数据库并在其上下文中执行初始化脚本

## Bug Details

### Bug Condition

当 `schema.sql` 通过 Docker Compose 挂载到 `docker-entrypoint-initdb.d` 并由 MySQL 8.0 容器启动时自动执行，以下四类问题导致表创建失败：

1. `DELIMITER $` 语法在 Docker entrypoint 的 shell 管道（`mysql < file.sql`）中无法正确解析
2. `CREATE DATABASE IF NOT EXISTS parking_db` 和 `USE parking_db` 与 `MYSQL_DATABASE` 环境变量冲突
3. `sys_operation_log` 和 `sys_access_log` 分区表的主键仅为 `id`，未包含分区列
4. `sys_car_plate` 的 `ALTER TABLE` 添加生成列语法可能在特定条件下失败

**Formal Specification:**
```
FUNCTION isBugCondition(sqlFile)
  INPUT: sqlFile of type SQLFile（通过 docker-entrypoint-initdb.d 执行的 SQL 文件）
  OUTPUT: boolean

  hasDELIMITER := sqlFile.contains("DELIMITER")
  hasCreateDB := sqlFile.contains("CREATE DATABASE")
  hasUSE := sqlFile.contains("USE parking_db")
  hasInvalidPartitionPK := ANY table IN sqlFile.partitionedTables
    WHERE table.primaryKey NOT CONTAINS table.partitionColumn
  hasAlterTableIssue := sqlFile.contains("ALTER TABLE sys_car_plate ADD COLUMN primary_house_no")
    AND NOT sqlFile.alterTableSyntaxValid("sys_car_plate")

  RETURN hasDELIMITER
         OR hasCreateDB
         OR hasUSE
         OR hasInvalidPartitionPK
         OR hasAlterTableIssue
END FUNCTION
```

### Examples

- **DELIMITER 问题**：`DELIMITER $` 后的存储过程 `create_parking_record_tables()` 创建失败，且 `DELIMITER ;` 未能恢复默认分隔符，导致后续所有 SQL（`visitor_application`、`visitor_authorization`、`visitor_session`、`sys_operation_log`、`sys_access_log` 及所有辅助功能表）全部解析失败
- **CREATE DATABASE / USE 冲突**：Docker 已通过 `MYSQL_DATABASE: parking_db` 创建数据库，`schema.sql` 中的 `CREATE DATABASE` 冗余，`USE parking_db` 在管道执行模式下可能引发上下文切换问题
- **分区表主键问题**：`sys_operation_log` 定义 `id BIGINT PRIMARY KEY AUTO_INCREMENT` 并使用 `PARTITION BY RANGE (TO_DAYS(operation_time))`，MySQL 报错 `A PRIMARY KEY must include all columns in the table's partitioning function`
- **ALTER TABLE 问题**：`sys_car_plate` 的 `ALTER TABLE ADD COLUMN primary_house_no ... GENERATED ALWAYS AS (...)` 语法在表刚创建后立即执行时应能成功，但需确保语法完全正确

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- 基础业务表（`sys_community`、`sys_admin`、`sys_owner`、`sys_house`、`sys_owner_house_rel`、`sys_car_plate`、`parking_config`）的 `CREATE TABLE` 语句保持不变
- 入场记录分表模板（`parking_car_record_template`）及静态分表（`parking_car_record_202603` ~ `parking_car_record_202606`）的定义保持不变
- Visitor 相关表（`visitor_application`、`visitor_authorization`、`visitor_session`）的定义保持不变
- 辅助功能表（`parking_stat_daily`、`sys_ip_whitelist`、`zombie_vehicle`、`owner_info_modify_application`、`export_task`、`verification_code`、`hardware_device`、`parking_fee`）的定义保持不变
- Docker Compose 中 `MYSQL_DATABASE: parking_db` 配置不受影响

**Scope:**
所有不涉及 bug 条件的 SQL 语句应完全不受修复影响。修复仅针对：
- 文件头部的 `CREATE DATABASE` / `USE` 语句
- 存储过程及其 `DELIMITER` 语法
- `sys_operation_log` 和 `sys_access_log` 的主键定义
- `sys_car_plate` 的 `ALTER TABLE` 语句（如需调整）

## Hypothesized Root Cause

基于 bug 描述和 `schema.sql` 源码分析，最可能的原因如下：

1. **DELIMITER 不兼容 Docker entrypoint 执行模式**：MySQL Docker 镜像的 entrypoint 脚本通过 `mysql < /docker-entrypoint-initdb.d/01-schema.sql` 管道方式执行 SQL 文件。`DELIMITER` 是 MySQL 客户端命令而非 SQL 语句，在管道模式下 `mysql` 客户端可能不处理 `DELIMITER` 指令，导致 `$` 符号被 shell 解释为变量引用或分隔符无法切换，存储过程体中的 `;` 被错误地当作语句结束符，导致语法错误。后续所有 SQL 因分隔符状态异常而全部失败。

2. **CREATE DATABASE / USE 与 Docker 初始化流程冲突**：Docker MySQL 镜像的 entrypoint 脚本在执行 `docker-entrypoint-initdb.d` 中的文件前，已通过 `MYSQL_DATABASE` 环境变量创建了 `parking_db` 数据库，并自动将执行上下文切换到该数据库。`schema.sql` 中的 `CREATE DATABASE` 是冗余操作，`USE parking_db` 在管道模式下可能导致意外行为。

3. **分区表主键约束违反**：MySQL 要求分区表的分区表达式中引用的所有列必须包含在表的每个唯一索引（包括主键）中。`sys_operation_log` 使用 `PARTITION BY RANGE (TO_DAYS(operation_time))` 但主键仅为 `id`，不包含 `operation_time`；`sys_access_log` 同理，主键仅为 `id`，不包含 `access_time`。

4. **ALTER TABLE 生成列语法**：`sys_car_plate` 的 `ALTER TABLE ADD COLUMN primary_house_no VARCHAR(50) GENERATED ALWAYS AS (CASE WHEN status = 'primary' AND is_deleted = 0 THEN house_no ELSE NULL END) STORED` 语法在 MySQL 8.0 中是合法的，但需确认在 Docker 初始化环境中（特别是在 DELIMITER 问题修复后）能正确执行。

## Correctness Properties

Property 1: Bug Condition - Schema 初始化脚本 Docker 兼容性

_For any_ `schema.sql` 文件通过 `docker-entrypoint-initdb.d` 在 MySQL 8.0 容器中执行时，修复后的文件 SHALL 不包含 `DELIMITER` 语法、不包含 `CREATE DATABASE` / `USE` 语句、所有分区表的主键包含分区列，确保所有 28 张表（含分表）和相关索引、生成列全部成功创建。

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**

Property 2: Preservation - 未修改表定义一致性

_For any_ 不涉及 bug 条件的表定义（基础业务表、分表模板、静态分表、Visitor 表、辅助功能表），修复后的 `schema.sql` SHALL 保持这些表的 `CREATE TABLE` 语句与原始文件完全一致，包括字段定义、索引、约束、注释和引擎配置。

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

假设根因分析正确，需要对 `parking-service/src/main/resources/sql/schema.sql` 进行以下修改：

**File**: `parking-service/src/main/resources/sql/schema.sql`

**Specific Changes**:

1. **移除 CREATE DATABASE 和 USE 语句**：删除文件头部的 `CREATE DATABASE IF NOT EXISTS parking_db ...` 和 `USE parking_db;`，因为 Docker 的 `MYSQL_DATABASE` 环境变量已负责创建数据库，entrypoint 脚本会自动在该数据库上下文中执行 SQL 文件。

2. **移除存储过程及 DELIMITER 语法**：完全删除 `DELIMITER $` ... `DELIMITER ;` 包裹的 `create_parking_record_tables()` 存储过程定义。该存储过程用于动态创建未来月份的分表，但文件中已有静态分表（`parking_car_record_202603` ~ `parking_car_record_202606`），存储过程可在后续通过其他方式（如应用层定时任务或运维脚本）实现。

3. **修复 sys_operation_log 主键**：将 `id BIGINT PRIMARY KEY AUTO_INCREMENT` 改为 `id BIGINT NOT NULL AUTO_INCREMENT`，并在表定义末尾添加复合主键 `PRIMARY KEY (id, operation_time)`，使分区列 `operation_time` 包含在主键中。

4. **修复 sys_access_log 主键**：将 `id BIGINT PRIMARY KEY AUTO_INCREMENT` 改为 `id BIGINT NOT NULL AUTO_INCREMENT`，并在表定义末尾添加复合主键 `PRIMARY KEY (id, access_time)`，使分区列 `access_time` 包含在主键中。

5. **确认 sys_car_plate ALTER TABLE 语法**：检查并确保 `ALTER TABLE sys_car_plate ADD COLUMN primary_house_no ...` 和 `ALTER TABLE sys_car_plate ADD UNIQUE KEY uk_community_house_primary ...` 语法在 MySQL 8.0 中正确无误。如果语法本身没有问题（仅因 DELIMITER 导致的级联失败），则保持不变。

## Testing Strategy

### Validation Approach

测试策略分为两个阶段：首先在未修复的代码上验证 bug 确实存在（探索性测试），然后在修复后验证所有表成功创建且未修改的表定义保持一致。

### Exploratory Bug Condition Checking

**Goal**: 在实施修复前，验证 bug 确实存在，确认或推翻根因分析。如果推翻，需要重新假设根因。

**Test Plan**: 使用原始 `schema.sql` 在 Docker MySQL 8.0 容器中执行，观察失败情况。也可以通过静态分析 SQL 文件内容来验证问题模式。

**Test Cases**:
1. **DELIMITER 检测测试**：检查原始 `schema.sql` 是否包含 `DELIMITER` 关键字（将在未修复代码上发现问题）
2. **CREATE DATABASE / USE 检测测试**：检查原始 `schema.sql` 是否包含 `CREATE DATABASE` 和 `USE` 语句（将在未修复代码上发现问题）
3. **分区表主键检测测试**：解析 `sys_operation_log` 和 `sys_access_log` 的 `CREATE TABLE` 语句，验证主键是否包含分区列（将在未修复代码上发现问题）
4. **ALTER TABLE 语法检测测试**：验证 `sys_car_plate` 的 `ALTER TABLE` 语句语法是否正确（可能在未修复代码上通过或失败）

**Expected Counterexamples**:
- 原始 SQL 文件包含 `DELIMITER $` 和 `DELIMITER ;`
- 原始 SQL 文件包含 `CREATE DATABASE IF NOT EXISTS parking_db` 和 `USE parking_db`
- `sys_operation_log` 主键仅为 `(id)`，不包含 `operation_time`
- `sys_access_log` 主键仅为 `(id)`，不包含 `access_time`

### Fix Checking

**Goal**: 验证对于所有触发 bug 条件的输入，修复后的函数产生预期行为。

**Pseudocode:**
```
FOR ALL sqlFile WHERE isBugCondition(sqlFile) DO
  fixedSqlFile := applyFix(sqlFile)
  ASSERT NOT fixedSqlFile.contains("DELIMITER")
  ASSERT NOT fixedSqlFile.contains("CREATE DATABASE")
  ASSERT NOT fixedSqlFile.contains("USE parking_db")
  FOR ALL table IN fixedSqlFile.partitionedTables DO
    ASSERT table.primaryKey CONTAINS table.partitionColumn
  END FOR
  result := executeSqlInDocker(fixedSqlFile)
  ASSERT result.allTablesCreated == true
  ASSERT result.tableCount == expectedTableCount
END FOR
```

### Preservation Checking

**Goal**: 验证对于所有不触发 bug 条件的输入，修复后的函数与原始函数产生相同结果。

**Pseudocode:**
```
FOR ALL tableDefinition WHERE NOT isBugAffectedTable(tableDefinition) DO
  ASSERT originalSqlFile.getTableDDL(tableDefinition) == fixedSqlFile.getTableDDL(tableDefinition)
END FOR
```

**Testing Approach**: 属性测试适用于保全性检查，因为：
- 可以自动生成多种表名组合来验证未修改表的定义一致性
- 能捕获手动单元测试可能遗漏的边界情况
- 对所有非 bug 输入提供强保证

**Test Plan**: 先在未修复代码上观察所有未受影响表的定义，然后编写属性测试验证修复后这些定义保持不变。

**Test Cases**:
1. **基础业务表保全测试**：验证 `sys_community`、`sys_admin`、`sys_owner`、`sys_house`、`sys_owner_house_rel`、`sys_car_plate`（CREATE TABLE 部分）、`parking_config` 的定义在修复前后一致
2. **分表模板和静态分表保全测试**：验证 `parking_car_record_template` 及 `parking_car_record_202603` ~ `parking_car_record_202606` 的定义在修复前后一致
3. **Visitor 表保全测试**：验证 `visitor_application`、`visitor_authorization`、`visitor_session` 的定义在修复前后一致
4. **辅助功能表保全测试**：验证 `parking_stat_daily`、`sys_ip_whitelist`、`zombie_vehicle`、`owner_info_modify_application`、`export_task`、`verification_code`、`hardware_device`、`parking_fee` 的定义在修复前后一致

### Unit Tests

- 静态分析修复后的 SQL 文件，验证不包含 `DELIMITER`、`CREATE DATABASE`、`USE` 语句
- 解析 `sys_operation_log` 的 `CREATE TABLE` 语句，验证主键为 `(id, operation_time)`
- 解析 `sys_access_log` 的 `CREATE TABLE` 语句，验证主键为 `(id, access_time)`
- 验证 `sys_car_plate` 的 `ALTER TABLE` 语句语法正确

### Property-Based Tests

- 生成随机表名列表，验证修复后 SQL 文件中所有未受影响表的定义与原始文件一致
- 对修复后的 SQL 文件进行多种模式匹配，验证不存在任何 `DELIMITER` 变体
- 验证所有分区表的主键都包含对应的分区列

### Integration Tests

- 在 Docker MySQL 8.0 容器中执行修复后的 `schema.sql`，验证所有表成功创建
- 验证创建的表数量与预期一致（28 张表，含分表）
- 验证 `sys_operation_log` 和 `sys_access_log` 的分区功能正常工作
- 验证 `sys_car_plate` 的生成列 `primary_house_no` 和唯一索引 `uk_community_house_primary` 正常创建
