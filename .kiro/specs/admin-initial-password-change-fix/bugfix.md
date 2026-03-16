# Bugfix 需求文档

## 简介

Admin_Portal 登录流程中，当管理员使用初始密码首次登录时，系统应弹出强制修改密码对话框。但由于 `handleLogin()` 方法中 `authStore.setLoginInfo(data)` 在检查 `mustChangePassword` 之前就被调用，导致 `isLoggedIn` 变为 `true`，路由守卫随即将用户从 `/login` 页面重定向到 `/`，密码修改对话框来不及显示。最终结果是首次登录的管理员无法修改初始密码，系统不可用。

## Bug 分析

### 当前行为（缺陷）

1.1 WHEN 管理员使用初始密码登录且后端返回 `mustChangePassword=true` THEN 系统在检查 `mustChangePassword` 之前就调用 `authStore.setLoginInfo(data)` 将 `accessToken` 写入 store，使 `isLoggedIn` 变为 `true`

1.2 WHEN `isLoggedIn` 为 `true` 且用户仍在 `/login` 页面 THEN 路由守卫 `beforeEach` 检测到已登录用户在登录页，自动重定向到 `/`，导致密码修改对话框（`changePasswordVisible`）虽被设为 `true` 但页面已被导航离开，对话框无法显示

1.3 WHEN 首次登录管理员被重定向到首页 THEN 管理员无法完成初始密码修改流程，系统处于不安全状态（仍使用初始密码）

### 期望行为（正确）

2.1 WHEN 管理员使用初始密码登录且后端返回 `mustChangePassword=true` THEN 系统 SHALL 延迟设置登录态（不将 `accessToken` 写入 store），先检查 `mustChangePassword` 标志，确保 `isLoggedIn` 在密码修改完成前保持为 `false`

2.2 WHEN `mustChangePassword=true` 且用户仍在 `/login` 页面 THEN 系统 SHALL 正常显示密码修改对话框，路由守卫不会触发重定向（因为 `isLoggedIn` 仍为 `false`），管理员可以在对话框中完成密码修改

2.3 WHEN 管理员在密码修改对话框中成功提交新密码 THEN 系统 SHALL 清除临时登录信息、关闭对话框，并提示管理员使用新密码重新登录

### 不变行为（回归防护）

3.1 WHEN 管理员使用非初始密码登录且后端返回 `mustChangePassword=false` THEN 系统 SHALL CONTINUE TO 正常保存登录信息到 store 并跳转到目标页面

3.2 WHEN 已登录管理员访问 `/login` 页面 THEN 路由守卫 SHALL CONTINUE TO 将其重定向到首页 `/`

3.3 WHEN 未登录用户访问需要认证的页面 THEN 路由守卫 SHALL CONTINUE TO 将其重定向到 `/login` 页面

3.4 WHEN 管理员正常登录成功后 THEN `changePassword` API 调用 SHALL CONTINUE TO 携带正确的 `accessToken` 进行鉴权

## Bug 条件推导

### Bug 条件函数

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type LoginResponse
  OUTPUT: boolean

  // 当后端返回 mustChangePassword=true 时触发 bug
  RETURN X.mustChangePassword = true
END FUNCTION
```

### 属性规约 — 修复检查

```pascal
// Property: Fix Checking — 首次登录密码修改流程
FOR ALL X WHERE isBugCondition(X) DO
  result ← handleLogin'(X)
  ASSERT isLoggedIn = false                          // 登录态未被提前设置
    AND changePasswordVisible = true                  // 密码修改对话框正常显示
    AND 用户未被路由守卫重定向离开 /login 页面          // 页面保持在 /login
END FOR
```

### 属性规约 — 保持检查

```pascal
// Property: Preservation Checking — 非首次登录行为不变
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT handleLogin(X) = handleLogin'(X)
  // 即 mustChangePassword=false 时，登录流程与修复前完全一致：
  //   保存登录信息 → isLoggedIn=true → 跳转目标页面
END FOR
```
