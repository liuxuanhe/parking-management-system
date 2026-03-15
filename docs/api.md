# 地下停车场管理系统 - API 接口文档

## 通用说明

### 基础路径

所有接口以 `/api/v1/` 为前缀，服务默认监听 `8080` 端口。

### 统一响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "req_1710000000000_abc123"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `int` | 0 表示成功，非 0 为错误码 |
| `message` | `string` | 结果描述 |
| `data` | `object/null` | 业务数据 |
| `requestId` | `string` | 请求追踪 ID |

### 认证方式

除登录和注册接口外，所有接口需在请求头中携带 JWT Token：

```
Authorization: Bearer {accessToken}
```

### 安全机制

写操作接口需携带以下请求头：

| Header | 说明 |
|--------|------|
| `X-Timestamp` | 请求时间戳（毫秒），5 分钟有效窗口 |
| `X-Nonce` | 随机字符串，防重放 |
| `X-Signature` | 签名值，算法：`SHA256(timestamp + nonce + requestBody + secretKey)` |
| `X-Idempotency-Key` | 幂等键（写操作必填） |

---

## 1. 认证模块

### 1.1 管理员登录

- **路径**: `POST /api/v1/auth/login`
- **权限**: 无需认证
- **请求体**:

```json
{
  "username": "admin",
  "password": "Admin@123456"
}
```

- **响应**: `{ "accessToken": "...", "refreshToken": "...", "mustChangePassword": false }`
- **错误码**: `PARKING_13004`（用户名或密码错误）、`PARKING_13005`（账号已锁定）、`PARKING_13006`（必须修改初始密码）

### 1.2 管理员修改密码

- **路径**: `POST /api/v1/auth/change-password`
- **权限**: 已登录管理员
- **请求体**:

```json
{
  "oldPassword": "OldPass@123",
  "newPassword": "NewPass@456"
}
```

- **说明**: 密码强度要求至少 8 位，包含大小写字母、数字、特殊字符

---

## 2. 业主管理模块

### 2.1 业主注册

- **路径**: `POST /api/v1/owners/register`
- **权限**: 无需认证
- **请求体**:

```json
{
  "phone": "13800138000",
  "verificationCode": "123456",
  "communityId": 1,
  "houseNo": "1-1-101",
  "idCardLast4": "1234"
}
```

- **说明**: 注册后状态为 `pending`，需物业审核
- **错误码**: `PARKING_1001`（验证码错误次数过多）、`PARKING_1002`（验证码已过期）

### 2.2 业主账号注销

- **路径**: `POST /api/v1/owners/{ownerId}/disable`
- **权限**: Super_Admin
- **请求体**:

```json
{
  "reason": "业主申请注销"
}
```

- **说明**: 所有车辆必须不在场才可注销
- **错误码**: `PARKING_14001`（业主有车辆在场）

### 2.3 批量审核业主

- **路径**: `POST /api/v1/owners/batch-audit`
- **权限**: Property_Admin / Super_Admin
- **请求体**:

```json
{
  "ids": [1, 2, 3],
  "action": "approve",
  "rejectReason": null
}
```

- **说明**: 每次最多 50 条，`action` 可选 `approve` / `reject`
- **响应**: `{ "successCount": 2, "failCount": 1, "failDetails": [...] }`

---

## 3. 敏感信息修改模块

### 3.1 申请修改敏感信息

- **路径**: `POST /api/v1/owners/info-modify/apply`
- **权限**: Owner
- **请求体**:

```json
{
  "ownerId": 1,
  "communityId": 1,
  "modifyType": "phone",
  "newValue": "13900139000",
  "reason": "更换手机号"
}
```

### 3.2 审批敏感信息修改

- **路径**: `POST /api/v1/owners/info-modify/{applyId}/audit`
- **权限**: Property_Admin / Super_Admin
- **请求体**:

```json
{
  "action": "approve",
  "adminId": 1,
  "communityId": 1,
  "rejectReason": null
}
```

---

## 4. 车辆管理模块

### 4.1 添加车牌

- **路径**: `POST /api/v1/vehicles`
- **权限**: Owner
- **请求体**:

```json
{
  "carNumber": "京A12345",
  "communityId": 1,
  "houseNo": "1-1-101",
  "ownerId": 1,
  "brand": "丰田",
  "model": "卡罗拉",
  "color": "白色"
}
```

- **说明**: 每个 Data_Domain 最多 5 个车牌
- **错误码**: `PARKING_3001`（车牌数量已达上限）

### 4.2 删除车牌

- **路径**: `DELETE /api/v1/vehicles/{vehicleId}`
- **权限**: Owner
- **说明**: 逻辑删除，车辆在场时不可删除
- **错误码**: `PARKING_3002`（车辆当前在场）

### 4.3 查询车牌列表

- **路径**: `GET /api/v1/vehicles?communityId={communityId}&houseNo={houseNo}`
- **权限**: Owner / Property_Admin
- **说明**: 返回 Data_Domain 下所有车牌，敏感信息已脱敏，使用 Redis 缓存（30 分钟）

### 4.4 设置 Primary 车辆

- **路径**: `PUT /api/v1/vehicles/{vehicleId}/primary`
- **权限**: Owner
- **请求体**:

```json
{
  "communityId": 1,
  "houseNo": "1-1-101"
}
```

- **说明**: 需所有车辆不在场，使用分布式锁 + 行级锁 + 唯一索引保证 One-Primary 约束
- **错误码**: `PARKING_4001`（有车辆在场）、`PARKING_4002`（原 Primary 有未完成申请）

---

## 5. 入场出场模块

### 5.1 车辆入场

- **路径**: `POST /api/v1/parking/entry`
- **权限**: 系统接口（硬件设备调用）
- **请求体**:

```json
{
  "carNumber": "京A12345",
  "communityId": 1
}
```

- **说明**: 自动识别 Primary / Visitor 车辆类型，使用分布式锁保证车位一致性，5 分钟内重复入场幂等处理
- **错误码**: `PARKING_5001`（车位已满）

### 5.2 车辆出场

- **路径**: `POST /api/v1/parking/exit`
- **权限**: 系统接口（硬件设备调用）
- **请求体**:

```json
{
  "carNumber": "京A12345",
  "communityId": 1
}
```

- **说明**: 无入场记录时创建异常出场记录（`exit_exception`），Visitor 车辆自动累计停放时长

### 5.3 处理异常出场

- **路径**: `POST /api/v1/parking/exit-exception/handle`
- **权限**: Property_Admin
- **请求体**:

```json
{
  "recordId": 1,
  "communityId": 1,
  "handleReason": "系统故障导致入场记录丢失",
  "adminId": 1
}
```

- **错误码**: `PARKING_5002`（记录不存在）、`PARKING_5003`（状态不是异常出场）

### 5.4 查询入场记录

- **路径**: `GET /api/v1/parking/records?communityId={}&houseNo={}&startTime={}&endTime={}&cursor={}&pageSize={}`
- **权限**: Owner / Property_Admin
- **说明**: 时间范围必填，支持跨月查询（UNION ALL），使用游标分页
- **响应**:

```json
{
  "records": [...],
  "nextCursor": "2026-03-01 10:00:00_12345",
  "hasMore": true
}
```

---

## 6. Visitor 权限模块

### 6.1 申请 Visitor 权限

- **路径**: `POST /api/v1/visitors/apply?ownerId={}&communityId={}&houseNo={}`
- **权限**: Owner
- **请求体**:

```json
{
  "carNumber": "京B67890",
  "reason": "亲属来访"
}
```

- **说明**: 检查月度配额（< 72 小时）和 Visitor_Available_Spaces（> 0）
- **错误码**: `PARKING_7001`（配额已用完）、`PARKING_9001`（车位不足）

### 6.2 审批 Visitor 申请

- **路径**: `POST /api/v1/visitors/{visitorId}/audit?adminId={}&communityId={}`
- **权限**: Property_Admin
- **请求体**:

```json
{
  "action": "approve",
  "rejectReason": null,
  "idempotencyKey": "visitor_audit_123"
}
```

- **说明**: 通过后设置 24 小时激活窗口，发送订阅消息通知业主

### 6.3 查询 Visitor 权限列表

- **路径**: `GET /api/v1/visitors?communityId={}&houseNo={}`
- **权限**: Owner / Property_Admin

### 6.4 查询月度配额

- **路径**: `GET /api/v1/visitors/quota?communityId={}&houseNo={}`
- **权限**: Owner
- **响应**:

```json
{
  "totalQuotaMinutes": 4320,
  "usedMinutes": 1200,
  "remainingMinutes": 3120
}
```

### 6.5 批量审批 Visitor

- **路径**: `POST /api/v1/visitors/batch-audit?adminId={}&communityId={}`
- **权限**: Property_Admin
- **请求体**: 同批量审核业主格式，每次最多 50 条

---

## 7. 车位配置模块

### 7.1 查询车位配置

- **路径**: `GET /api/v1/parking/config?communityId={}`
- **权限**: Property_Admin

### 7.2 修改车位配置

- **路径**: `PUT /api/v1/parking/config`
- **权限**: Property_Admin
- **请求体**:

```json
{
  "communityId": 1,
  "totalSpaces": 200,
  "reservedSpaces": 10,
  "monthlyQuotaHours": 72,
  "singleSessionHours": 24,
  "version": 1
}
```

- **说明**: 使用乐观锁（`version` 字段），新 `totalSpaces` ≥ 当前在场车辆数
- **错误码**: `PARKING_9002`（新车位数小于在场车辆数）

---

## 8. 报表统计模块

### 8.1 入场趋势报表

- **路径**: `GET /api/v1/reports/entry-trend?communityId={}&startDate={}&endDate={}&aggregation={}`
- **权限**: Property_Admin
- **参数**: `aggregation` 可选 `daily` / `weekly` / `monthly`
- **说明**: 使用 Redis 缓存（1 小时），1 年数据查询 ≤ 3 秒

### 8.2 车位使用率报表

- **路径**: `GET /api/v1/reports/space-usage?communityId={}&startDate={}&endDate={}`
- **权限**: Property_Admin

### 8.3 峰值时段报表

- **路径**: `GET /api/v1/reports/peak-hours?communityId={}&startDate={}&endDate={}`
- **权限**: Property_Admin

### 8.4 僵尸车辆统计报表

- **路径**: `GET /api/v1/reports/zombie-vehicles?communityId={}&startDate={}&endDate={}`
- **权限**: Property_Admin

---

## 9. 僵尸车辆模块

### 9.1 查询僵尸车辆列表

- **路径**: `GET /api/v1/zombie-vehicles?communityId={}&status={}`
- **权限**: Property_Admin
- **参数**: `status` 可选 `unhandled` / `contacted` / `resolved` / `ignored`

### 9.2 处理僵尸车辆

- **路径**: `POST /api/v1/zombie-vehicles/{zombieId}/handle`
- **权限**: Property_Admin
- **请求体**:

```json
{
  "handleType": "contacted",
  "handleNote": "已电话联系车主"
}
```

- **错误码**: `PARKING_22001`（记录不存在）、`PARKING_22002`（已处理）、`PARKING_22003`（处理方式无效）

---

## 10. 审计日志模块

### 10.1 查询操作日志

- **路径**: `GET /api/v1/audit/operation-logs?communityId={}&operatorId={}&operationType={}&startTime={}&endTime={}&cursor={}&pageSize={}`
- **权限**: Super_Admin / Property_Admin
- **说明**: 默认查询最近 30 天，使用游标分页

### 10.2 查询访问日志

- **路径**: `GET /api/v1/audit/access-logs?communityId={}&accessorId={}&apiPath={}&startTime={}&endTime={}&cursor={}&pageSize={}`
- **权限**: Super_Admin / Property_Admin

### 10.3 导出审计日志

- **路径**: `POST /api/v1/audit/logs/export`
- **权限**: Super_Admin
- **说明**: 异步任务处理

---

## 11. 导出模块

### 11.1 导出入场记录

- **路径**: `POST /api/v1/exports/parking-records`
- **权限**: Property_Admin
- **说明**: 异步导出，单次最多 100,000 条，默认脱敏

### 11.2 导出原始数据

- **路径**: `POST /api/v1/exports/parking-records/raw`
- **权限**: Super_Admin + IP_Whitelist
- **说明**: 不脱敏导出，需 IP 白名单验证
- **错误码**: `PARKING_17001`（IP 不在白名单内）

### 11.3 查询导出状态

- **路径**: `GET /api/v1/exports/{exportId}/status`
- **权限**: 发起导出的用户

### 11.4 下载导出文件

- **路径**: `GET /api/v1/exports/{exportId}/download`
- **权限**: 发起导出的用户

---

## 12. IP 白名单模块

### 12.1 添加 IP 白名单

- **路径**: `POST /api/v1/ip-whitelist`
- **权限**: Super_Admin

### 12.2 删除 IP 白名单

- **路径**: `DELETE /api/v1/ip-whitelist/{id}`
- **权限**: Super_Admin

### 12.3 查询 IP 白名单

- **路径**: `GET /api/v1/ip-whitelist?communityId={}`
- **权限**: Super_Admin

---

## 13. 错误码说明

| 错误码 | 说明 |
|--------|------|
| `PARKING_1001` | 验证码错误次数过多，请 10 分钟后重试 |
| `PARKING_1002` | 验证码已过期，请重新获取 |
| `PARKING_2001` | 该申请已被审核，无法重复操作 |
| `PARKING_3001` | 车牌数量已达上限（5 个），无法继续添加 |
| `PARKING_3002` | 该车辆当前在场，无法删除 |
| `PARKING_4001` | 房屋号下有车辆在场，无法切换 Primary 车辆 |
| `PARKING_4002` | 原 Primary 车辆有未完成入场申请，无法切换 |
| `PARKING_5001` | 车位已满，无法入场 |
| `PARKING_5002` | 异常出场记录不存在 |
| `PARKING_5003` | 该记录状态不是异常出场，无法处理 |
| `PARKING_7001` | 本月 Visitor 时长配额已用完（72 小时），无法申请 |
| `PARKING_8001` | Visitor 激活窗口已过期，授权已自动取消 |
| `PARKING_9001` | Visitor 可开放车位不足，无法申请 |
| `PARKING_9002` | 新车位数小于当前在场车辆数，无法修改 |
| `PARKING_12001` | 无权访问该小区数据 |
| `PARKING_13001` | Token 已过期 |
| `PARKING_13002` | Token 无效 |
| `PARKING_13003` | Token 已被撤销 |
| `PARKING_13004` | 用户名或密码错误 |
| `PARKING_13005` | 账号已被锁定 |
| `PARKING_13006` | 必须修改初始密码 |
| `PARKING_14001` | 业主有车辆在场，无法注销账号 |
| `PARKING_17001` | IP 不在白名单内，无法导出原始数据 |
| `PARKING_19001` | 请求时间戳超出有效窗口 |
| `PARKING_19002` | Nonce 已被使用 |
| `PARKING_19003` | 签名验证失败 |
| `PARKING_19004` | 请求频率超出限制（IP 级） |
| `PARKING_19005` | 账号请求频率超出限制 |
| `PARKING_20001` | IP 不在白名单内，无法执行高危操作 |
| `PARKING_22001` | 僵尸车辆记录不存在 |
| `PARKING_22002` | 该僵尸车辆已处理，无法重复操作 |
| `PARKING_22003` | 处理方式无效，仅支持 contacted/resolved/ignored |
