# 地下停车场管理系统 - 部署文档

## 1. 环境要求

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | 运行时环境 |
| Maven | 3.x | 构建工具 |
| MySQL | 8.0+ | 主数据库 |
| Redis | 6.x+ | 缓存、分布式锁、防重放 |
| Docker | 20.10+ | 容器化部署（可选） |
| Docker Compose | 2.x+ | 编排工具（可选） |

## 2. 快速启动（Docker Compose）

### 2.1 准备环境变量

```bash
cp .env.example .env
# 编辑 .env，修改数据库密码和密钥
vim .env
```

### 2.2 启动所有服务

```bash
# 构建并启动
docker compose up -d --build

# 查看日志
docker compose logs -f parking-api

# 查看服务状态
docker compose ps
```

### 2.3 验证服务

```bash
# 等待服务启动完成（约 30-60 秒）
curl http://localhost:8080/api/v1/auth/login
```

### 2.4 停止服务

```bash
docker compose down

# 停止并清除数据卷（慎用）
docker compose down -v
```

## 3. 手动部署（非 Docker）

### 3.1 安装 MySQL 并初始化数据库

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE parking_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 执行建表脚本
mysql -u root -p parking_db < src/main/resources/sql/schema.sql
```

### 3.2 安装并启动 Redis

```bash
# macOS
brew install redis
brew services start redis

# Linux (Ubuntu/Debian)
sudo apt install redis-server
sudo systemctl start redis
```

### 3.3 构建应用

```bash
# 编译并打包（跳过测试）
mvn clean package -DskipTests

# 运行测试
mvn test
```

### 3.4 启动应用

```bash
java -jar target/underground-parking-management-1.0.0-SNAPSHOT.jar \
  --spring.datasource.url="jdbc:mysql://localhost:3306/parking_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
  --spring.datasource.username=root \
  --spring.datasource.password=your_password \
  --spring.data.redis.host=localhost \
  --spring.data.redis.port=6379
```

或通过环境变量：

```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/parking_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=your_password
export SPRING_DATA_REDIS_HOST=localhost

java -jar target/underground-parking-management-1.0.0-SNAPSHOT.jar
```

## 4. 环境变量说明

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/parking_db?...` | MySQL 连接地址 |
| `SPRING_DATASOURCE_USERNAME` | `root` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | `root` | 数据库密码（生产环境必须修改） |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis 地址 |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis 端口 |
| `SPRING_DATA_REDIS_PASSWORD` | 空 | Redis 密码（生产环境建议设置） |
| `JWT_SECRET_KEY` | 见 `.env.example` | JWT 签名密钥（Base64 编码，生产环境必须更换） |
| `SIGNATURE_SECRET_KEY` | 见 `.env.example` | 接口签名密钥（Base64 编码，生产环境必须更换） |
| `SERVER_PORT` | `8080` | 应用监听端口 |

## 5. 数据库初始化

建表脚本位于 `src/main/resources/sql/schema.sql`，包含所有核心表和索引。

```bash
# 手动执行建表
mysql -u root -p parking_db < src/main/resources/sql/schema.sql
```

Docker Compose 部署时会自动执行该脚本（挂载到 `/docker-entrypoint-initdb.d/`）。

**注意：** 停车记录分表 `parking_car_record_yyyymm` 需通过存储过程 `create_parking_record_tables()` 创建。首次部署后需手动调用：

```sql
CALL create_parking_record_tables();
```

## 6. 生产环境注意事项

### 6.1 安全配置

- **必须更换** `JWT_SECRET_KEY` 和 `SIGNATURE_SECRET_KEY`，使用随机生成的 Base64 密钥
- **必须修改** MySQL root 密码，建议创建专用数据库账号并限制权限
- **建议设置** Redis 密码
- 配置 IP_Whitelist 限制高危操作的访问来源
- 确保 `.env` 文件不被提交到版本控制（已在 `.gitignore` 中排除）

### 6.2 性能调优

- MySQL 连接池：根据并发量调整 `hikari.maximum-pool-size`（默认 50）
- Redis 连接池：根据并发量调整 `lettuce.pool.max-active`（默认 20）
- JVM 堆内存：根据服务器配置调整 `-Xms` 和 `-Xmx`（建议 1-4 GB）
- 开启 MySQL 慢查询日志，监控超过 1 秒的查询

### 6.3 日志管理

- 应用日志输出到 `logs/parking.log`
- 日志格式包含 `requestId`，便于请求链路追踪
- 生产环境建议将 `root` 日志级别设为 `WARN`，`com.parking` 设为 `INFO`

### 6.4 定时任务

系统包含以下定时任务，启动后自动运行：

| 任务 | 执行频率 | 说明 |
|------|---------|------|
| Zombie_Vehicle 识别 | 每日 | 标记连续在场超过 7 天的车辆 |
| Visitor 超时检测 | 每小时 | 检查累计停放 ≥ 24 小时的 Visitor 会话 |
| Visitor 未入场取消 | 每小时 | 取消超过 24 小时激活窗口未入场的授权 |
| 统计数据回补 | 每日凌晨 2 点 | 回补前一日 `parking_stat_daily` 数据 |
| Audit_Log 归档 | 每月 1 日 | 归档 6 个月以上的审计日志 |
| 分表自动创建 | 每月 | 创建未来月份的 `parking_car_record_yyyymm` 分表 |

## 7. 前端部署

### 7.1 Admin_Portal（管理后台）

```bash
cd admin-portal
npm install
npm run build
# 构建产物在 admin-portal/dist/ 目录
```

使用 Nginx 托管静态文件，并配置反向代理到后端 API：

```nginx
server {
    listen 80;
    server_name admin.parking.example.com;

    location / {
        root /var/www/admin-portal/dist;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 7.2 Owner_App（业主小程序）

Owner_App 基于 uni-app 开发，需通过 HBuilderX 或命令行工具编译为微信小程序：

```bash
cd owner-app
npm install
# 使用 HBuilderX 打开项目并发布到微信小程序平台
# 或使用 uni-app CLI 构建
```

**注意：** 小程序发布需要微信公众平台账号，属于人工运维操作。
