# 地下停车场管理系统

企业级多小区 SaaS 平台，面向物业管理方和业主，提供停车场智能化管理、车位管控、访客授权、数据追溯与审计合规等功能。

## 项目结构

```
├── parking-service/       # 后端服务（Spring Boot）
├── admin-portal/          # 物业管理后台（Vue 3 + Ant Design Vue）
├── owner-app/             # 业主小程序（uni-app + Vue 3）
├── docs/                  # 项目文档（API、部署、运维、用户手册）
├── docker-compose.yml     # Docker 编排配置
└── .kiro/                 # Kiro Spec 文件（需求、设计、任务）
```

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行时 |
| Spring Boot | 3.2.5 | 应用框架 |
| MyBatis | 3.0.3 | ORM / SQL 映射 |
| MySQL | 8.0 | 关系型数据库 |
| Redis | 6.x | 缓存、分布式锁、幂等键、Nonce 存储 |
| JWT (jjwt 0.12.5) | - | 认证鉴权 |
| Maven | 3.x | 构建工具 |
| JUnit 5 + jqwik 1.8.4 | - | 单元测试 + 属性测试 |

### 前端

| 技术 | 用途 |
|------|------|
| Vue 3 + Vite | 前端框架 |
| Ant Design Vue 3.2.20 | 管理后台 UI 组件库 |
| ECharts 5.x | 报表图表 |
| uni-app | 业主小程序跨端框架 |
| Pinia | 状态管理 |

## 核心功能

- **多小区数据隔离**：基于 `community_id` 严格隔离不同小区数据
- **房屋号数据域**：以 `community_id + house_no` 为数据域，支持同房屋号多业主数据共享
- **Primary 车辆管理**：每个房屋号最多 1 辆 Primary_Vehicle，享有自动入场权限（先到先得）
- **Visitor 权限管理**：业主申请 → 物业审批 → 首次入场激活，24 小时单次时长 + 月度 72 小时配额
- **入场记录分表**：按月分表存储（`parking_car_record_yyyymm`），支持游标分页和跨月查询
- **僵尸车辆识别**：定时扫描连续在场超过 7 天的车辆，通知物业处理
- **完整审计链路**：操作日志 + 访问日志，永久保留，6 个月后归档
- **安全防护**：签名验证、Nonce 防重放、IP 限流、IP 白名单、数据脱敏

## 角色与权限

| 角色 | 权限范围 |
|------|---------|
| Super_Admin（超级管理员） | 跨小区操作、高风险操作（注销账号、导出原始数据）、IP 白名单管理 |
| Property_Admin（物业管理员） | 单小区范围内的审批、配置、报表、僵尸车辆处理 |
| Owner（业主） | 本 Data_Domain 内的车牌管理、Primary 设置、Visitor 申请、记录查询 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.x
- MySQL 8.0
- Redis 6.x
- Node.js 18+（前端开发）

### 1. 启动基础设施

使用 Docker Compose 快速启动 MySQL 和 Redis：

```bash
# 复制环境变量配置
cp parking-service/.env.example parking-service/.env

# 启动 MySQL + Redis
docker compose up -d
```

### 2. 启动后端服务

```bash
cd parking-service

# 编译
mvn compile

# 运行（默认端口 8080）
mvn spring-boot:run
```

系统首次启动时会自动初始化超级管理员账号并在日志中输出随机密码，首次登录需强制修改密码。

### 3. 启动管理后台（开发模式）

```bash
cd admin-portal
npm install
npm run dev
```

### 4. 启动业主小程序（开发模式）

```bash
cd owner-app
npm install
npm run dev:h5        # H5 模式
npm run dev:mp-weixin # 微信小程序模式
```

## 常用命令

### 后端（在 `parking-service/` 目录下执行）

```bash
mvn compile              # 编译
mvn test                 # 运行测试
mvn package -DskipTests  # 打包（跳过测试）
mvn clean install        # 清理并构建
```

### 管理后台（在 `admin-portal/` 目录下执行）

```bash
npm run dev      # 开发模式
npm run build    # 生产构建
npm run test     # 运行测试
```

## 配置说明

### 主要配置文件

| 文件 | 说明 |
|------|------|
| `parking-service/src/main/resources/application.yml` | 后端应用配置 |
| `parking-service/src/main/resources/logback-spring.xml` | 日志配置（含 requestId） |
| `parking-service/.env.example` | 环境变量模板 |
| `docker-compose.yml` | Docker 编排配置 |

### 关键配置项

- 服务端口：`8080`
- 数据库：`parking_db`（localhost:3306）
- Redis：localhost:6379，database 0
- JWT Access Token 有效期：2 小时
- JWT Refresh Token 有效期：7 天
- Jackson 日期格式：`yyyy-MM-dd HH:mm:ss`，时区 `Asia/Shanghai`

## API 概览

所有接口以 `/api/v1/` 为前缀，统一响应格式 `{code, message, data, requestId}`。

| 模块 | 路径前缀 | 说明 |
|------|---------|------|
| 认证 | `/api/v1/auth` | 登录、Token 刷新 |
| 业主管理 | `/api/v1/owners` | 注册、审核、注销、批量审核 |
| 车牌管理 | `/api/v1/vehicles` | 添加、删除、查询、设置 Primary |
| 入场出场 | `/api/v1/parking` | 入场、出场、记录查询、车位配置 |
| Visitor 权限 | `/api/v1/visitors` | 申请、审批、配额查询、批量审批 |
| 报表 | `/api/v1/reports` | 入场趋势、车位使用率、峰值时段、僵尸车辆 |
| 导出 | `/api/v1/exports` | 异步导出、状态查询、文件下载 |
| 审计日志 | `/api/v1/audit` | 操作日志、访问日志查询与导出 |
| 僵尸车辆 | `/api/v1/zombie-vehicles` | 查询、处理 |
| IP 白名单 | `/api/v1/ip-whitelist` | 添加、删除、查询 |

详细接口文档参见 [`docs/api.md`](docs/api.md)。

## 项目文档

| 文档 | 说明 |
|------|------|
| [`docs/api.md`](docs/api.md) | API 接口文档 |
| [`docs/deployment.md`](docs/deployment.md) | 部署指南 |
| [`docs/operations.md`](docs/operations.md) | 运维手册 |
| [`docs/user-guide-admin.md`](docs/user-guide-admin.md) | 管理员使用手册 |
| [`docs/user-guide-owner.md`](docs/user-guide-owner.md) | 业主使用手册 |

## 架构概览

```
Client Layer (Owner_App / Admin_Portal)
        │
        │ HTTPS + JWT
        ▼
   API Gateway (签名验证 / 防重放 / 限流)
        │
        ▼
   Application Layer (Spring Boot 单体)
   ┌──────────┬──────────┬──────────┐
   │ 用户模块  │ 车辆模块  │ 入场模块  │
   ├──────────┼──────────┼──────────┤
   │ 访客模块  │ 报表模块  │ 审计模块  │
   └──────────┴──────────┴──────────┘
        │
        ▼
   Data Layer (MySQL 8.0 + Redis 6.x)
```

## License

私有项目，未经授权不得使用。
