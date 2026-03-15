# Git 提交流程规范（强制）

## 提交粒度（Commit Granularity）

- 每个 `TASK-*` 完成后，必须形成至少 1 个 Git Commit，对应该任务的完整代码改动与测试。
- 禁止将多个完全无关的任务合在一个提交中。
- 允许为同一个任务拆分为多个小提交（如 refactor + feature + test），但提交信息中必须标注同一个 `TASK-*` 编号。

## 提交流程（每次执行完一个 Task 后必须执行）

步骤顺序固定，不可跳过：

1. 运行对应任务的单元测试/集成测试（如适用），确认全部通过。
2. 执行 `git status`，确认改动范围只包含当前任务相关文件。
3. 执行 `git add` 仅包含本任务的相关文件（避免把临时/无关文件带入）。
4. 执行 `git commit`，**必须同时包含 summary 和 description**，使用两个 `-m` 参数：
   - 第一个 `-m`（summary）：`TASK-<编号>: <中文简要描述>`
   - 第二个 `-m`（description）：使用中文描述本次改动的内容、动机和影响范围
   - 禁止省略 description，每次提交必须包含两个 `-m` 参数
   - 命令格式：`git commit -m "TASK-<编号>: <中文简要描述>" -m "<中文详细说明>"`
   - 完整示例：
     ```bash
     git commit -m "TASK-1.2: 实现 CommunityMapper 及 XML 映射配置" -m "新增 CommunityMapper 接口及对应的 XML 映射文件，支持按 community_id 查询小区信息。涉及文件：CommunityMapper.java、CommunityMapper.xml。"
     ```
5. 禁止自动执行 `git push`。仅当用户明确要求时才推送到远端。

## 提交信息规范

- 提交标题（summary）和正文（description）**必须全部使用中文**，禁止使用英文句子描述改动内容。
- **每次提交必须同时包含 summary 和 description，禁止只写 summary 而省略 description。**
- 提交标题必须包含对应的 `TASK-*` 编号，禁止无编号的模糊信息（如 `修复`、`更新`）。
- 提交正文（description）应包含：改动了哪些内容、为什么改、影响范围。
- 英文仅允许出现在：类名、方法名、文件路径、API 路径、错误码等技术标识中。
- 禁止在 commit message 中泄露敏感数据。

## 完成标准（Definition of Done）

每个任务的完成标准必须包含以下全部条件：

- 代码实现完成且通过自测
- 相关测试（单测/集成测试）已编写并通过
- 已按照上述 Git 提交流程完成一次提交（含本任务 `TASK-*` 编号）

## 安全与防呆

- 禁止在未运行（或运行失败）对应测试的情况下自动提交代码。
- 禁止自动修改 Git 配置（`user.name` / `user.email` 等）。
- 禁止执行 `git push --force`。

## 能力声明

在本项目中，允许并鼓励在完成 Task 后使用以下 git 命令管理代码版本：

- `git status`、`git diff`、`git add`、`git commit`

不得擅自修改 Git 全局配置，不得使用 `git push`（除非用户明确要求）。
