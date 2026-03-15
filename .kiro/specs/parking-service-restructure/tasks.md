# 任务列表：后端服务目录重构

## 任务

- [x] 1. 后端文件迁移到 parking-service/ 子目录
  - [x] 1.1 创建 `parking-service/` 目录，将 `src/`、`pom.xml`、`Dockerfile`、`.env.example`、`.jqwik-database` 迁移到其中
  - [x] 1.2 如果 `target/` 目录存在，将其迁移到 `parking-service/target/`
  - [x] 1.3 验证 `parking-service/` 内部文件结构完整，根目录不再包含 `src/`、`pom.xml`、`Dockerfile`、`.jqwik-database`
- [x] 2. 更新 docker-compose.yml 路径配置
  - [x] 2.1 将 `parking-api` 服务的 `build.context` 从 `.` 更新为 `./parking-service`
  - [x] 2.2 将 `mysql` 服务的 volume mount 路径从 `./src/main/resources/sql/schema.sql` 更新为 `./parking-service/src/main/resources/sql/schema.sql`
- [x] 3. 处理 .dockerignore 文件
  - [x] 3.1 在 `parking-service/` 目录下创建新的 `.dockerignore` 文件，仅包含后端构建上下文相关的忽略规则（`target`、`.jqwik-database`、`.git`、`.gitignore`、`*.md`）
  - [x] 3.2 删除根目录的 `.dockerignore` 文件
- [x] 4. 更新 .gitignore 路径规则
  - [x] 4.1 将 `target/` 更新为 `parking-service/target/`，将 `.jqwik-database` 更新为 `parking-service/.jqwik-database`，新增 `parking-service/.env`
- [x] 5. 更新 .idea/misc.xml 中的 Maven 路径
  - [x] 5.1 将 `$PROJECT_DIR$/pom.xml` 更新为 `$PROJECT_DIR$/parking-service/pom.xml`
- [x] 6. 更新 docs/deployment.md 中的路径引用
  - [x] 6.1 将所有 `src/main/resources/sql/schema.sql` 引用更新为 `parking-service/src/main/resources/sql/schema.sql`
  - [x] 6.2 将 Maven 构建命令更新为在 `parking-service/` 目录下执行（如 `cd parking-service && mvn clean package -DskipTests`）
  - [x] 6.3 将 `java -jar target/underground-parking-management-1.0.0-SNAPSHOT.jar` 更新为 `java -jar parking-service/target/underground-parking-management-1.0.0-SNAPSHOT.jar`
- [x] 7. 更新 .kiro/steering/ 文档路径
  - [x] 7.1 更新 `structure.md` 中的路径前缀从 `src/` 为 `parking-service/src/`
  - [x] 7.2 更新 `tech.md` 中的配置文件路径和构建命令，添加 `parking-service/` 前缀或 `cd parking-service` 工作目录说明
- [x] 8. 构建验证
  - [x] 8.1 在 `parking-service/` 目录下执行 `mvn compile` 验证编译通过
  - [x] 8.2 在 `parking-service/` 目录下执行 `mvn test` 验证所有测试通过
