/**
 * 管理员管理相关 API
 * 对接后端 AdminController
 */
import request from './request'

/**
 * 查询管理员列表
 * GET /api/v1/admins
 */
export function getAdminList() {
  return request.get('/admins')
}

/**
 * 创建物业管理员
 * POST /api/v1/admins
 * @param {Object} data - { username, realName, phoneNumber, communityId, role }
 */
export function createAdmin(data) {
  return request.post('/admins', data)
}

/**
 * 解锁管理员账号
 * POST /api/v1/admins/{adminId}/unlock
 */
export function unlockAdmin(adminId) {
  return request.post(`/admins/${adminId}/unlock`)
}

/**
 * 重置管理员密码
 * POST /api/v1/admins/{adminId}/reset-password
 */
export function resetAdminPassword(adminId) {
  return request.post(`/admins/${adminId}/reset-password`)
}
