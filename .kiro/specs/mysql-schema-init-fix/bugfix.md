# Bugfix Requirements Document

## Introduction

`schema.sql` 通过 Docker Compose 挂载到 `docker-entrypoint-initdb.d` 由 MySQL 8.0 容器启动时自动执行，但由于多个 SQL 语法和兼容性问题，导致大量表创建失败。主要问题包括：`DELIMITER $` 在 Docker entrypoint 的 shell 管道中解析异常、分区表的分区列未包含在主键中违反 MySQL 约束、以及 `CREATE DATABASE` / `USE` 与 Docker 环境变量 `MYSQL_DATABASE` 的冲突。这些问题导致存储过程之后的所有表（Visitor 相关表、审计日志表、辅助功能表等）全部创建失败。

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN `schema.sql` 通过 `docker-entrypoint-initdb.d` 执行，且文件中包含 `DELIMITER $` 语法时 THEN `$` 符号在 Docker entrypoint 的 shell 环境中可能被解释为变量引用，导致存储过程 `create_parking_record_tables()` 创建失败，且 `DELIMITER` 未正确恢复为 `;`，后续所有 SQL 语句全部解析失败

1.2 WHEN `schema.sql` 中包含 `CREATE DATABASE IF NOT EXISTS parking_db` 和 `USE parking_db`，而 Docker Compose 已通过 `MYSQL_DATABASE: parking_db` 环境变量创建了同名数据库时 THEN 产生冗余的数据库创建操作，且 `USE parking_db` 可能在 entrypoint 脚本的管道执行模式下引发上下文切换问题

1.3 WHEN 创建 `sys_operation_log` 表使用 `PARTITION BY RANGE (TO_DAYS(operation_time))` 且主键仅为 `id BIGINT PRIMARY KEY AUTO_INCREMENT` 时 THEN MySQL 报错，因为分区表要求分区表达式中引用的列必须包含在表的所有唯一索引（含主键）中，导致该表创建失败

1.4 WHEN 创建 `sys_access_log` 表使用 `PARTITION BY RANGE (TO_DAYS(access_time))` 且主键仅为 `id BIGINT PRIMARY KEY AUTO_INCREMENT` 时 THEN MySQL 报错，原因同 1.3，导致该表创建失败

1.5 WHEN `sys_car_plate` 表的 `ALTER TABLE` 添加生成列 `primary_house_no` 或唯一索引 `uk_community_house_primary` 失败时 THEN 后续依赖该表结构的业务逻辑无法正常工作，但不会阻断后续 SQL 执行（除非与 `DELIMITER` 问题叠加）

### Expected Behavior (Correct)

2.1 WHEN `schema.sql` 通过 `docker-entrypoint-initdb.d` 执行时 THEN 系统 SHALL 避免使用 `DELIMITER` 语法，改为将存储过程定义移除或改写为不依赖自定义分隔符的方式（例如直接创建静态分表而不使用存储过程），确保所有 SQL 语句在 Docker entrypoint 环境中正确解析和执行

2.2 WHEN `schema.sql` 通过 `docker-entrypoint-initdb.d` 执行时 THEN 系统 SHALL 移除 `CREATE DATABASE IF NOT EXISTS parking_db` 和 `USE parking_db` 语句，因为 Docker 的 `MYSQL_DATABASE` 环境变量已负责创建数据库，且 entrypoint 脚本会自动在该数据库上下文中执行 `docker-entrypoint-initdb.d` 中的 SQL 文件

2.3 WHEN 创建 `sys_operation_log` 分区表时 THEN 系统 SHALL 将主键修改为包含分区列的复合主键（如 `PRIMARY KEY (id, operation_time)`），使分区定义符合 MySQL 的唯一索引约束要求，确保表创建成功

2.4 WHEN 创建 `sys_access_log` 分区表时 THEN 系统 SHALL 将主键修改为包含分区列的复合主键（如 `PRIMARY KEY (id, access_time)`），使分区定义符合 MySQL 的唯一索引约束要求，确保表创建成功

2.5 WHEN `sys_car_plate` 表创建后执行 `ALTER TABLE` 添加生成列和唯一索引时 THEN 系统 SHALL 确保 `ALTER TABLE` 语句语法正确且能在 MySQL 8.0 环境中成功执行，生成列 `primary_house_no` 和唯一索引 `uk_community_house_primary` 正常创建

### Unchanged Behavior (Regression Prevention)

3.1 WHEN 执行 `schema.sql` 中的基础业务表（`sys_community`、`sys_admin`、`sys_owner`、`sys_house`、`sys_owner_house_rel`、`sys_car_plate`、`parking_config`）创建语句时 THEN 系统 SHALL CONTINUE TO 成功创建这些表，表结构、字段定义、索引和约束与原始定义完全一致

3.2 WHEN 执行 `schema.sql` 中的入场记录分表模板（`parking_car_record_template`）及静态分表（`parking_car_record_202603` ~ `parking_car_record_202606`）创建语句时 THEN 系统 SHALL CONTINUE TO 成功创建这些表，表结构与模板一致

3.3 WHEN 执行 `schema.sql` 中的 Visitor 相关表（`visitor_application`、`visitor_authorization`、`visitor_session`）创建语句时 THEN 系统 SHALL CONTINUE TO 成功创建这些表，表结构、字段定义、索引与原始定义完全一致

3.4 WHEN 执行 `schema.sql` 中的辅助功能表（`parking_stat_daily`、`sys_ip_whitelist`、`zombie_vehicle`、`owner_info_modify_application`、`export_task`、`verification_code`、`hardware_device`、`parking_fee`）创建语句时 THEN 系统 SHALL CONTINUE TO 成功创建这些表，表结构、字段定义、索引与原始定义完全一致

3.5 WHEN Docker Compose 启动 MySQL 容器时 THEN 系统 SHALL CONTINUE TO 使用 `MYSQL_DATABASE: parking_db` 环境变量创建数据库，字符集和排序规则配置不受影响
