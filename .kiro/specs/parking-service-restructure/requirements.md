# 需求文档：后端服务目录重构

## 简介

当前项目中，Java 后端的源码（`src/`）、构建配置（`pom.xml`）、容器化文件（`Dockerfile`、`.dockerignore`）等直接散落在仓库根目录，而前端项目 `admin-portal` 和 `owner-app` 各自拥有独立子目录。这种不对称的结构导致根目录混乱，不利于多模块协作和 CI/CD 维护。

本次重构的目标是将后端相关文件统一迁移到 `parking-service/` 子目录中，使其与 `admin-portal`、`owner-app` 同级，形成清晰的 monorepo 结构。这是一次纯结构重构，不涉及任何业务逻辑变更。

## 术语表

- **Parking_Service**：Java 后端服务模块，迁移后位于 `parking-service/` 子目录
- **Admin_Portal**：物业管理后台前端，位于 `admin-portal/` 子目录
- **Owner_App**：业主小程序前端，位于 `owner-app/` 子目录
- **Build_Context**：Docker 构建时的文件系统上下文路径，在 `docker-compose.yml` 中通过 `build.context` 指定
- **Init_Script_Mount**：`docker-compose.yml` 中将 SQL 初始化脚本挂载到 MySQL 容器 `/docker-entrypoint-initdb.d/` 的卷映射路径
- **IDE_Config**：`.idea/` 目录下的 IntelliJ IDEA 项目配置文件
- **Maven_Root**：`pom.xml` 所在目录，Maven 以此为项目根目录执行构建

## 需求

### 需求 1：后端文件迁移

**用户故事：** 作为开发者，我希望后端服务文件集中在 `parking-service/` 子目录中，以便仓库根目录结构清晰、各模块职责分明。

#### 验收标准

1. WHEN 重构完成后，THE Parking_Service SHALL 将以下文件和目录从仓库根目录迁移到 `parking-service/` 子目录中：`src/`、`pom.xml`、`Dockerfile`、`.env.example`、`.jqwik-database`、`target/`
2. WHEN 重构完成后，THE Parking_Service SHALL 保持 `parking-service/` 内部的文件相对路径与迁移前一致（例如 `src/main/java/com/parking/` 的包结构不变）
3. WHEN 重构完成后，THE Parking_Service SHALL 确保仓库根目录不再包含 `src/`、`pom.xml`、`Dockerfile`、`.jqwik-database`、`target/` 等后端专属文件

### 需求 2：Docker Compose 配置更新

**用户故事：** 作为运维人员，我希望 `docker-compose.yml` 中的路径在迁移后仍然正确，以便容器化部署正常工作。

#### 验收标准

1. WHEN 后端文件迁移到 `parking-service/` 后，THE Docker_Compose SHALL 将 `parking-api` 服务的 `build.context` 从 `.` 更新为 `./parking-service`
2. WHEN 后端文件迁移到 `parking-service/` 后，THE Docker_Compose SHALL 将 `mysql` 服务的 Init_Script_Mount 路径从 `./src/main/resources/sql/schema.sql` 更新为 `./parking-service/src/main/resources/sql/schema.sql`
3. WHEN 后端文件迁移到 `parking-service/` 后，THE Docker_Compose SHALL 确保 `docker compose up -d --build` 命令能够成功构建并启动所有服务

### 需求 3：.dockerignore 处理

**用户故事：** 作为开发者，我希望 Docker 构建上下文中的忽略规则在迁移后仍然有效，以便构建镜像时排除不必要的文件。

#### 验收标准

1. WHEN Build_Context 变更为 `./parking-service` 后，THE Parking_Service SHALL 在 `parking-service/` 目录下放置 `.dockerignore` 文件
2. THE `.dockerignore` SHALL 仅包含与 Parking_Service 构建上下文相关的忽略规则（如 `target`、`.jqwik-database`），移除与前端项目（`admin-portal`、`owner-app`）相关的条目
3. WHEN 根目录的 `.dockerignore` 不再被 Docker 构建引用时，THE Parking_Service SHALL 删除根目录的 `.dockerignore` 文件

### 需求 4：.gitignore 路径更新

**用户故事：** 作为开发者，我希望 `.gitignore` 中的路径规则在迁移后仍然正确匹配，以便 Git 不会误跟踪构建产物或临时文件。

#### 验收标准

1. WHEN 后端文件迁移到 `parking-service/` 后，THE `.gitignore` SHALL 将 `target/` 规则更新为 `parking-service/target/`
2. WHEN 后端文件迁移到 `parking-service/` 后，THE `.gitignore` SHALL 将 `.jqwik-database` 规则更新为 `parking-service/.jqwik-database`
3. THE `.gitignore` SHALL 确保 `parking-service/.env`（如存在）被正确忽略

### 需求 5：IDE 配置更新

**用户故事：** 作为开发者，我希望 IntelliJ IDEA 的项目配置在迁移后仍然能正确识别 Maven 项目，以便 IDE 中的代码导航、编译和运行功能正常工作。

#### 验收标准

1. WHEN 后端文件迁移到 `parking-service/` 后，THE IDE_Config SHALL 将 `.idea/misc.xml` 中的 Maven 项目文件路径从 `$PROJECT_DIR$/pom.xml` 更新为 `$PROJECT_DIR$/parking-service/pom.xml`

### 需求 6：文档路径更新

**用户故事：** 作为开发者或运维人员，我希望项目文档中引用的文件路径在迁移后保持准确，以便文档指引不会产生误导。

#### 验收标准

1. WHEN 后端文件迁移到 `parking-service/` 后，THE `docs/deployment.md` SHALL 将所有引用旧路径的命令和说明更新为新路径（例如 `src/main/resources/sql/schema.sql` 更新为 `parking-service/src/main/resources/sql/schema.sql`）
2. WHEN 后端文件迁移到 `parking-service/` 后，THE `docs/deployment.md` SHALL 将 Maven 构建命令的工作目录说明更新为在 `parking-service/` 目录下执行（例如 `cd parking-service && mvn clean package -DskipTests`）
3. WHEN 后端文件迁移到 `parking-service/` 后，THE `docs/deployment.md` SHALL 将 `java -jar` 启动命令中的 jar 路径更新为 `parking-service/target/underground-parking-management-1.0.0-SNAPSHOT.jar`

### 需求 7：Steering 规范文档路径更新

**用户故事：** 作为开发者，我希望 `.kiro/steering/` 下的技术规范和项目结构文档中引用的路径在迁移后保持准确，以便 AI 辅助工具和团队成员获取正确的项目结构信息。

#### 验收标准

1. WHEN 后端文件迁移到 `parking-service/` 后，THE `structure.md` SHALL 将项目结构描述中的路径前缀从 `src/` 更新为 `parking-service/src/`
2. WHEN 后端文件迁移到 `parking-service/` 后，THE `tech.md` SHALL 将构建命令的工作目录说明更新为在 `parking-service/` 目录下执行

### 需求 8：构建验证

**用户故事：** 作为开发者，我希望迁移后 Maven 构建能够正常通过，以便确认重构没有破坏项目的编译和测试流程。

#### 验收标准

1. WHEN 后端文件迁移到 `parking-service/` 后，THE Maven_Build SHALL 在 `parking-service/` 目录下执行 `mvn compile` 成功完成编译
2. WHEN 后端文件迁移到 `parking-service/` 后，THE Maven_Build SHALL 在 `parking-service/` 目录下执行 `mvn test` 成功通过所有测试
3. IF `mvn compile` 或 `mvn test` 失败，THEN THE 开发者 SHALL 检查 `pom.xml` 中是否存在硬编码的绝对路径或相对路径引用需要调整
