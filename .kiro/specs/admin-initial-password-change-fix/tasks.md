# 实施计划

- [x] 1. 编写 Bug 条件探索性测试
  - **Property 1: Bug Condition** — 首次登录时 `setLoginInfo` 被提前调用导致重定向
  - **重要**：此测试必须在实施修复之前编写和运行
  - **目标**：通过反例证明 bug 存在 — 当 `mustChangePassword=true` 时，`isLoggedIn` 不应为 `true`，但未修复代码会提前调用 `setLoginInfo` 导致 `isLoggedIn=true`
  - **Scoped PBT 方法**：将属性范围限定为 `mustChangePassword=true` 的登录响应
  - 编写组件级测试，模拟 `login()` API 返回 `{ accessToken: "test-token", mustChangePassword: true }` 的响应
  - 断言 `handleLogin()` 执行后：`authStore.isLoggedIn` 应为 `false`，`changePasswordVisible` 应为 `true`，`router.push` 未被调用
  - 在未修复代码上运行测试
  - **预期结果**：测试失败（这是正确的 — 证明 bug 存在）
  - 记录反例：`authStore.setLoginInfo(data)` 在 `mustChangePassword` 检查之前被无条件调用，导致 `isLoggedIn=true`，路由守卫将用户从 `/login` 重定向到 `/`
  - 测试编写完成、运行完毕、失败已记录后标记任务完成
  - _Requirements: 1.1, 1.2, 2.1, 2.2_

- [x] 2. 编写保持性属性测试（在实施修复之前）
  - **Property 2: Preservation** — 非首次登录行为不变
  - **重要**：遵循观察优先方法论
  - 观察：在未修复代码上，当 `mustChangePassword=false` 时，`authStore.setLoginInfo(data)` 被调用，`isLoggedIn=true`，`router.push(redirect)` 被调用跳转到目标页面
  - 观察：`changePasswordVisible` 保持为 `false`
  - 编写属性测试：对于所有 `mustChangePassword=false` 的登录响应，验证 `setLoginInfo` 被调用、`isLoggedIn` 为 `true`、`router.push` 跳转到正确的目标页面、`changePasswordVisible` 为 `false`
  - 在未修复代码上运行测试
  - **预期结果**：测试通过（确认基线行为已被捕获）
  - 测试编写完成、运行通过后标记任务完成
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 3. 修复 `handleLogin()` 中 `setLoginInfo` 调用时序问题

  - [x] 3.1 实施修复
    - 修改 `admin-portal/src/views/login/LoginView.vue` 中的 `handleLogin()` 函数
    - 新增组件级变量 `tempLoginData`，用于临时保存登录响应数据
    - 将 `authStore.setLoginInfo(data)` 的调用移到 `mustChangePassword` 检查之后
    - 当 `mustChangePassword=true` 时：将登录响应保存到 `tempLoginData`，设置 `changePasswordVisible=true`，不调用 `setLoginInfo`
    - 当 `mustChangePassword=false` 时：正常调用 `authStore.setLoginInfo(data)` 并跳转到目标页面
    - 修改 `handleChangePassword()` 函数：在调用 `changePassword` API 前，临时调用 `authStore.setLoginInfo(tempLoginData)` 以便请求拦截器能注入 `accessToken`
    - 密码修改成功后调用 `authStore.logout()` 清除登录态，并清空 `tempLoginData`
    - 密码修改失败时也调用 `authStore.logout()` 清除临时登录态
    - _Bug_Condition: isBugCondition(input) where input.mustChangePassword = true_
    - _Expected_Behavior: isLoggedIn = false AND changePasswordVisible = true AND 用户停留在 /login 页面_
    - _Preservation: mustChangePassword=false 时的正常登录流程保持不变_
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4_

  - [x] 3.2 验证 Bug 条件探索性测试现在通过
    - **Property 1: Expected Behavior** — 首次登录时延迟设置登录态
    - **重要**：重新运行任务 1 中编写的同一测试，不要编写新测试
    - 任务 1 的测试编码了期望行为：`isLoggedIn=false`、`changePasswordVisible=true`、`router.push` 未被调用
    - 当此测试通过时，确认期望行为已满足
    - **预期结果**：测试通过（确认 bug 已修复）
    - _Requirements: 2.1, 2.2_

  - [x] 3.3 验证保持性测试仍然通过
    - **Property 2: Preservation** — 非首次登录行为不变
    - **重要**：重新运行任务 2 中编写的同一测试，不要编写新测试
    - **预期结果**：测试通过（确认无回归）
    - 确认修复后所有保持性测试仍然通过
    - _Requirements: 3.1, 3.2, 3.3_

- [x] 4. 检查点 — 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。
