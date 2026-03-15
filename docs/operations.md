# 地下停车场管理系统 - 运维文档

## 1. 监控配置

### 1.1 应用健康检查

```bash
# 基础健康检查
curl http://localhost:8080/actuator/health

# Docker 容器健康检查（已内置于 docker-compose.yml）
docker inspect --format='{{.State.Health.Status}}' parking-api
```

### 1.2 关键监控指标

| 指标 | 监控方式 | 告警阈值 |
|------|---------|---------|
| 接口响应时间 | 日志分析 / APM | P95 > 1 秒 |
| 报表查询耗时 | 日志分析 | > 3 秒 |
| MySQL 连接池使用率 | HikariCP 指标 | > 80% |
| Redis 连接池使用率 | Lettuce 指标 | > 80% |
| Redis 内存使用 | `redis-cli info memory` | > 200MB |
| 磁盘空间（日志目录） | 系统监控 | > 80% |
| JVM 堆内存使用率 | JMX / APM | > 85% |

### 1.3 MySQL 慢查询监控

```sql
-- 开启慢查询日志
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;
SET GLOBAL slow_query_log_file = '/var/log/mysql/slow.log';

-- 查看慢查询统计
SHOW GLOBAL STATUS LIKE 'Slow_queries';
```

### 1.4 Redis 监控

```bash
# 查看 Redis 运行状态
redis-cli info

# 查看内存使用
redis-cli info memory

# 查看连接数
redis-cli info clients

# 查看键空间统计
redis-cli info keyspace
```

## 2. 日志查询

### 2.1 日志文件位置

| 日志类型 | 路径 | 说明 |
|---------|------|------|
| 应用日志 | `logs/parking.log` | 主日志文件，包含 `requestId` |
| MySQL 慢查询 | `/var/log/mysql/slow.log` | 超过 1 秒的查询 |
| Redis 日志 | Docker 容器内 `/data/` | Redis 持久化日志 |

### 2.2 日志格式

应用日志包含 `requestId`，格式示例：

```
2026-03-15 10:00:00.000 [http-nio-8080-exec-1] INFO  [req_1710000000000_abc123] c.p.controller.EntryController - 车辆入场请求: carNumber=京A12345, communityId=1
```

### 2.3 常用日志查询命令

```bash
# 按 requestId 追踪完整请求链路
grep "req_1710000000000_abc123" logs/parking.log

# 查看错误日志
grep "ERROR" logs/parking.log | tail -50

# 查看特定接口的请求日志
grep "EntryController" logs/parking.log | tail -20

# 查看特定时间段的日志
awk '/2026-03-15 10:00/,/2026-03-15 11:00/' logs/parking.log

# 统计每小时错误数量
grep "ERROR" logs/parking.log | awk '{print $1" "$2}' | cut -d: -f1,2 | sort | uniq -c
```

### 2.4 审计日志查询

审计日志存储在数据库中，可通过 API 或直接查询：

```sql
-- 查询最近操作日志
SELECT * FROM sys_operation_log
WHERE community_id = 1
ORDER BY operation_time DESC
LIMIT 20;

-- 查询特定操作人的日志
SELECT * FROM sys_operation_log
WHERE operator_id = 1 AND operation_time >= '2026-03-01'
ORDER BY operation_time DESC;

-- 查询访问日志
SELECT * FROM sys_access_log
WHERE community_id = 1 AND access_time >= '2026-03-15'
ORDER BY access_time DESC
LIMIT 50;
```

## 3. 备份恢复

### 3.1 MySQL 数据库备份

```bash
# 全量备份（建议每日凌晨执行）
mysqldump -u root -p --single-transaction --routines --triggers \
  parking_db > backup/parking_db_$(date +%Y%m%d_%H%M%S).sql

# 仅备份核心业务表（不含日志表）
mysqldump -u root -p --single-transaction parking_db \
  sys_community sys_admin sys_owner sys_house sys_owner_house_rel \
  sys_car_plate parking_config visitor_application visitor_authorization \
  visitor_session zombie_vehicle parking_stat_daily \
  > backup/parking_core_$(date +%Y%m%d).sql

# 备份分表数据（按月）
mysqldump -u root -p --single-transaction parking_db \
  parking_car_record_202603 > backup/records_202603.sql
```

### 3.2 Redis 数据备份

```bash
# 触发 RDB 快照
redis-cli BGSAVE

# 复制 RDB 文件
cp /data/dump.rdb backup/redis_dump_$(date +%Y%m%d).rdb

# Docker 环境下
docker cp parking-redis:/data/dump.rdb backup/redis_dump_$(date +%Y%m%d).rdb
```

### 3.3 数据恢复

```bash
# MySQL 全量恢复
mysql -u root -p parking_db < backup/parking_db_20260315_020000.sql

# Redis 恢复（需停止 Redis 服务）
# 1. 停止 Redis
docker compose stop redis
# 2. 替换 RDB 文件
docker cp backup/redis_dump_20260315.rdb parking-redis:/data/dump.rdb
# 3. 重启 Redis
docker compose start redis
```

### 3.4 自动备份脚本（建议配置 crontab）

```bash
# 编辑 crontab
crontab -e

# 每日凌晨 3 点全量备份 MySQL
0 3 * * * mysqldump -u root -pYOUR_PASSWORD --single-transaction --routines parking_db | gzip > /backup/parking_db_$(date +\%Y\%m\%d).sql.gz

# 每日凌晨 4 点备份 Redis
0 4 * * * redis-cli BGSAVE && sleep 5 && cp /data/dump.rdb /backup/redis_$(date +\%Y\%m\%d).rdb

# 保留最近 30 天备份，自动清理旧文件
0 5 * * * find /backup -name "*.sql.gz" -mtime +30 -delete
0 5 * * * find /backup -name "*.rdb" -mtime +30 -delete
```

**注意：** 以上 crontab 配置需运维人员手工执行，根据实际环境调整路径和密码。

## 4. 故障排查

### 4.1 常见问题与解决方案

#### 应用无法启动

```bash
# 检查端口占用
lsof -i :8080

# 检查 MySQL 连接
mysql -u root -p -h localhost -P 3306 -e "SELECT 1"

# 检查 Redis 连接
redis-cli -h localhost -p 6379 ping

# 查看应用启动日志
docker compose logs parking-api | tail -100
```

#### MySQL 连接池耗尽

```sql
-- 查看当前连接数
SHOW PROCESSLIST;
SHOW STATUS LIKE 'Threads_connected';

-- 查看连接池配置
SHOW VARIABLES LIKE 'max_connections';
```

应用层面检查 HikariCP 配置（`application.yml`）：
- `hikari.maximum-pool-size`：默认 50，根据并发量调整
- `hikari.connection-timeout`：默认 30 秒

#### Redis 内存不足

```bash
# 查看内存使用详情
redis-cli info memory

# 查看大 key
redis-cli --bigkeys

# 手动清理过期键
redis-cli --scan --pattern "idempotency:*" | head -20
```

#### 分表不存在导致入场失败

```sql
-- 检查分表是否存在
SHOW TABLES LIKE 'parking_car_record_%';

-- 手动创建缺失的分表
CALL create_parking_record_tables();
```

#### 审计日志分区满

```sql
-- 查看分区信息
SELECT PARTITION_NAME, TABLE_ROWS
FROM INFORMATION_SCHEMA.PARTITIONS
WHERE TABLE_NAME = 'sys_operation_log';

-- 手动添加新分区（如需要）
ALTER TABLE sys_operation_log ADD PARTITION (
  PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01'))
);
```

### 4.2 性能问题排查

```bash
# 查看 JVM 内存使用
jps -l
jstat -gc <PID> 1000 10

# 查看线程状态
jstack <PID> > thread_dump.txt

# 查看堆内存快照（需人工执行，会短暂影响性能）
jmap -dump:format=b,file=heap_dump.hprof <PID>
```

### 4.3 数据一致性检查

```sql
-- 检查车位数量一致性
SELECT pc.total_spaces,
       (SELECT COUNT(*) FROM parking_car_record_202603
        WHERE community_id = pc.community_id AND status = 'entered') AS in_park_count,
       pc.total_spaces - (SELECT COUNT(*) FROM parking_car_record_202603
        WHERE community_id = pc.community_id AND status = 'entered') AS available_spaces
FROM parking_config pc;

-- 检查 Primary 车辆唯一性约束
SELECT community_id, house_no, COUNT(*) AS primary_count
FROM sys_car_plate
WHERE status = 'primary' AND is_deleted = 0
GROUP BY community_id, house_no
HAVING primary_count > 1;

-- 检查 Visitor 配额一致性
SELECT community_id, house_no,
       SUM(accumulated_duration) AS total_minutes
FROM visitor_session
WHERE MONTH(create_time) = MONTH(NOW()) AND YEAR(create_time) = YEAR(NOW())
GROUP BY community_id, house_no
HAVING total_minutes > 4320;
```
