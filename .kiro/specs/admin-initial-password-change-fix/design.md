# Admin 首次登录强制修改密码 Bugfix 设计

## 概述

Admin_Portal 登录流程中，当管理员使用初始密码首次登录时，`handleLogin()` 方法在检查 `mustChangePassword` 标志之前就调用了 `authStore.setLoginInfo(data)`，导致 `isLoggedIn` 变为 `true`。路由守卫 `beforeEach` 检测到已登录用户在 `/login` 页面后自动重定向到 `/`，密码修改对话框来不及显示。

修复策略：将 `authStore.setLoginInfo(data)` 的调用延迟到 `mustChangePassword` 检查之后。当 `mustChangePassword=true` 时，不设置登录态，而是将登录响应数据临时保存，显示密码修改对话框。只有在 `mustChangePassword=false` 时才正常保存登录态并跳转。

## 术语表

- **Bug_Condition (C)**: 触发 bug 的条件 — 后端登录响应中 `mustChangePassword=true`
- **Property (P)**: bug 条件下的期望行为 — `isLoggedIn` 保持 `false`，密码修改对话框正常显示
- **Preservation**: 修复不应影响的现有行为 — `mustChangePassword=false` 时的正常登录流程
- **`handleLogin()`**: `LoginView.vue` 中的登录提交处理函数，调用 `login()` API 并根据响应决定后续流程
- **`authStore.setLoginInfo(data)`**: `auth.js` store 中的方法，将 `accessToken` 等信息写入 Pinia store 和 `localStorage`，使 `isLoggedIn` 变为 `true`
- **`isLoggedIn`**: `auth.js` store 中的计算属性，`!!accessToken.value`，路由守卫依赖此值判断登录状态
- **`mustChangePassword`**: 后端登录 API 响应字段，`true` 表示管理员使用初始密码，需强制修改

## Bug 详情

### Bug 条件

当管理员使用初始密码登录时，后端返回 `mustChangePassword=true`。当前代码在检查该标志之前就调用了 `authStore.setLoginInfo(data)`，导致 `isLoggedIn` 立即变为 `true`。路由守卫的 `beforeEach` 钩子检测到已登录用户在 `/login` 页面，触发重定向到 `/`，密码修改对话框虽然被设为可见但页面已被导航离开。

**形式化规约：**
```
FUNCTION isBugCondition(input)
  INPUT: input of type LoginResponse
  OUTPUT: boolean

  RETURN input.mustChangePassword = true
END FUNCTION
```

### 示例

- 管理员 A 使用初始密码 `Admin@123` 登录，后端返回 `{ accessToken: "xxx", mustChangePassword: true }`。期望：停留在 `/login` 页面并弹出密码修改对话框。实际：被重定向到 `/`，对话框未显示。
- 管理员 B 已修改过密码，使用新密码登录，后端返回 `{ accessToken: "yyy", mustChangePassword: false }`。期望：正常跳转到首页。实际：正常跳转（此场景无 bug）。
- 管理员 C 使用初始密码登录，URL 中带有 `?redirect=/vehicles`。期望：弹出密码修改对话框，修改完成后提示重新登录。实际：被重定向到 `/`。
- 边界情况：管理员 D 使用初始密码登录后，在密码修改对话框中调用 `changePassword` API。期望：API 请求能携带正确的 `accessToken`。实际：由于 `setLoginInfo` 已被调用，token 在 store 中，API 可以正常调用（但页面已被重定向，无法操作对话框）。

## 期望行为

### 保持要求

**不变行为：**
- `mustChangePassword=false` 时的正常登录流程必须保持不变：调用 `authStore.setLoginInfo(data)` → `isLoggedIn=true` → 跳转到目标页面
- 已登录管理员访问 `/login` 页面时，路由守卫仍将其重定向到首页 `/`
- 未登录用户访问需要认证的页面时，路由守卫仍将其重定向到 `/login`
- `changePassword` API 调用仍需携带正确的 `accessToken` 进行鉴权
- 密码修改对话框的表单验证逻辑（密码强度、确认密码一致性）保持不变
- `request.js` 拦截器的 token 注入和错误处理逻辑保持不变

**范围：**
所有不涉及 `mustChangePassword=true` 的登录场景应完全不受此修复影响。包括：
- 非首次登录的正常登录流程
- 登录失败（用户名/密码错误）的错误处理
- 路由守卫的所有现有重定向逻辑
- `authStore` 的 `setLoginInfo` 和 `logout` 方法本身的行为

## 假设根因

基于 bug 描述和代码分析，根因明确：

1. **`setLoginInfo` 调用时序错误**：在 `handleLogin()` 中，`authStore.setLoginInfo(data)` 在第 148 行被无条件调用，而 `mustChangePassword` 检查在第 151 行。这意味着无论是否需要修改密码，`isLoggedIn` 都会立即变为 `true`。

2. **路由守卫的竞争条件**：`isLoggedIn` 变为 `true` 后，Vue Router 的 `beforeEach` 守卫在下一个导航周期检测到已登录用户在 `/login` 页面（`router/index.js` 第 89-91 行），触发重定向到 `/`。这发生在 `changePasswordVisible` 被设为 `true` 之前或几乎同时，导致对话框无法显示。

3. **临时数据保存缺失**：当前代码没有临时保存登录响应数据的机制。修复后需要在 `mustChangePassword=true` 时临时保存 `accessToken` 等数据，以便密码修改对话框中的 `changePassword` API 调用能携带正确的 token。

## 正确性属性

Property 1: Bug Condition — 首次登录时延迟设置登录态

_For any_ 登录响应 `input`，当 `isBugCondition(input)` 为 `true`（即 `input.mustChangePassword=true`）时，修复后的 `handleLogin'` 函数 SHALL 不调用 `authStore.setLoginInfo(data)`，确保 `isLoggedIn` 保持为 `false`，同时 `changePasswordVisible` 被设为 `true`，用户停留在 `/login` 页面。

**Validates: Requirements 2.1, 2.2**

Property 2: Preservation — 非首次登录行为不变

_For any_ 登录响应 `input`，当 `isBugCondition(input)` 为 `false`（即 `input.mustChangePassword=false`）时，修复后的 `handleLogin'` 函数 SHALL 产生与原始 `handleLogin` 函数完全相同的结果：调用 `authStore.setLoginInfo(data)`，`isLoggedIn=true`，跳转到目标页面。

**Validates: Requirements 3.1, 3.2, 3.3**

## 修复实现

### 所需变更

假设根因分析正确：

**文件**: `admin-portal/src/views/login/LoginView.vue`

**函数**: `handleLogin()`

**具体变更**:

1. **移除提前调用 `setLoginInfo`**：删除 `mustChangePassword` 检查之前的 `authStore.setLoginInfo(data)` 调用

2. **添加临时登录数据存储**：新增一个组件级响应式变量 `tempLoginData`，用于在 `mustChangePassword=true` 时临时保存登录响应数据（包含 `accessToken`）

3. **条件分支处理**：
   - 当 `mustChangePassword=true` 时：将登录响应数据保存到 `tempLoginData`，显示密码修改对话框，不设置登录态
   - 当 `mustChangePassword=false` 时：正常调用 `authStore.setLoginInfo(data)` 并跳转

4. **修改 `handleChangePassword` 中的 API 调用**：由于 `mustChangePassword=true` 时 `accessToken` 不在 store 中，`changePassword` API 调用需要手动携带临时保存的 token。可以通过在请求头中直接传入 token，或在调用 `changePassword` 前临时设置 store 中的 token

5. **清理临时数据**：在密码修改成功或取消后，清除 `tempLoginData`

**修复后的 `handleLogin` 伪代码**：
```
async function handleLogin():
  data = await login(credentials)

  if data.mustChangePassword:
    tempLoginData = data                    // 临时保存，不写入 store
    tempLoginPassword = loginForm.password
    changePwdForm.oldPassword = tempLoginPassword
    changePasswordVisible = true
    message.warning('首次登录，请修改初始密码')
    return

  // 非首次登录，正常流程
  authStore.setLoginInfo(data)
  message.success('登录成功')
  router.push(redirect)
```

**修复后的 `handleChangePassword` 伪代码**：
```
async function handleChangePassword():
  // 临时设置 token 以便 API 调用携带鉴权信息
  authStore.setLoginInfo(tempLoginData)

  try:
    await changePassword({ oldPassword, newPassword })
    message.success('密码修改成功，请使用新密码重新登录')
    changePasswordVisible = false
    authStore.logout()                      // 清除登录态
    tempLoginData = null
    loginForm.password = ''
  catch:
    authStore.logout()                      // 失败时也清除临时登录态
```

## 测试策略

### 验证方法

测试策略分为两个阶段：首先在未修复代码上运行探索性测试以确认 bug 存在并验证根因假设，然后在修复后验证 bug 已修复且现有行为未被破坏。

### 探索性 Bug 条件检查

**目标**：在实施修复之前，通过测试用例复现 bug，确认或否定根因分析。如果否定，需要重新假设根因。

**测试计划**：编写组件测试，模拟 `login()` API 返回 `mustChangePassword=true` 的响应，验证 `handleLogin()` 执行后 `isLoggedIn` 的状态和 `changePasswordVisible` 的值。在未修复代码上运行以观察失败。

**测试用例**：
1. **首次登录状态检查**：模拟 `mustChangePassword=true` 的登录响应，断言 `isLoggedIn` 应为 `false`（在未修复代码上将失败，因为 `setLoginInfo` 已被调用）
2. **对话框显示检查**：模拟 `mustChangePassword=true` 的登录响应，断言 `changePasswordVisible` 应为 `true` 且页面未被导航离开（在未修复代码上将失败）
3. **路由重定向检查**：模拟 `mustChangePassword=true` 的登录响应，断言 `router.push` 未被调用（在未修复代码上将失败，因为路由守卫会触发重定向）

**预期反例**：
- `isLoggedIn` 在 `mustChangePassword=true` 时为 `true`（因为 `setLoginInfo` 被提前调用）
- 可能原因：`setLoginInfo` 在 `mustChangePassword` 检查之前被无条件调用

### 修复检查

**目标**：验证对于所有满足 bug 条件的输入，修复后的函数产生期望行为。

**伪代码：**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := handleLogin'(input)
  ASSERT isLoggedIn = false
  ASSERT changePasswordVisible = true
  ASSERT router.push 未被调用
  ASSERT tempLoginData 包含完整的登录响应数据
END FOR
```

### 保持检查

**目标**：验证对于所有不满足 bug 条件的输入，修复后的函数与原始函数产生相同结果。

**伪代码：**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT handleLogin(input) = handleLogin'(input)
  // 即 mustChangePassword=false 时：
  //   authStore.setLoginInfo(data) 被调用
  //   isLoggedIn = true
  //   router.push(redirect) 被调用
END FOR
```

**测试方法**：推荐使用属性测试进行保持检查，因为：
- 可以自动生成大量测试用例覆盖输入域
- 能捕获手动单元测试可能遗漏的边界情况
- 对非 bug 输入的行为不变性提供强保证

**测试计划**：先在未修复代码上观察 `mustChangePassword=false` 时的正常登录行为，然后编写属性测试捕获该行为，确保修复后行为一致。

**测试用例**：
1. **正常登录保持**：验证 `mustChangePassword=false` 时，`setLoginInfo` 被调用且 `router.push` 跳转到目标页面
2. **登录态保持**：验证 `mustChangePassword=false` 时，`isLoggedIn` 为 `true`
3. **路由守卫保持**：验证已登录用户访问 `/login` 仍被重定向到 `/`
4. **Token 注入保持**：验证 `request.js` 拦截器仍能正确注入 `accessToken`

### 单元测试

- 测试 `handleLogin()` 在 `mustChangePassword=true` 时不调用 `setLoginInfo`
- 测试 `handleLogin()` 在 `mustChangePassword=true` 时设置 `changePasswordVisible=true`
- 测试 `handleLogin()` 在 `mustChangePassword=false` 时正常调用 `setLoginInfo` 并跳转
- 测试 `handleChangePassword()` 能正确携带临时保存的 token 调用 API
- 测试 `handleChangePassword()` 成功后清除临时数据并调用 `logout()`
- 测试边界情况：`tempLoginData` 为空时的防御性处理

### 属性测试

- 生成随机的 `LoginResponse`（`mustChangePassword` 为 `true` 或 `false`），验证 `handleLogin'` 在 bug 条件下不设置登录态
- 生成随机的 `LoginResponse`（`mustChangePassword=false`），验证修复后行为与原始行为一致
- 生成随机的非登录输入（鼠标点击、其他键盘事件），验证不受修复影响

### 集成测试

- 测试完整的首次登录 → 密码修改 → 重新登录流程
- 测试首次登录时密码修改对话框的表单验证（密码强度、确认密码一致性）
- 测试首次登录密码修改失败后的错误处理和重试
