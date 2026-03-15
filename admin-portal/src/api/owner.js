/**
 * 业主管理相关 API
 * 对接后端 OwnerController
 */
import request from './request'

/**
 * 查询业主列表（支持按状态筛选）
 * GET /api/v1/owners?status=xxx&communityId=xxx&page=1&pageSize=10
 * @param {Object} params - { status, communityId, page, pageSize }
 * @returns {Promise<Object>} - { records: [...], total }
 */
export function getOwnerList(params) {
  return request.get('/owners', { params })
}

/**
 * 审核单个业主
 * POST /api/v1/owners/{ownerId}/audit
 * @param {number} ownerId - 业主 ID
 * @param {Object} data - { action: "approve" | "reject", rejectReason: "xxx" }
 * @returns {Promise<Object>}
 */
export function auditOwner(ownerId, data) {
  return request.post(`/owners/${ownerId}/audit`, data)
}

/**
 * 批量审核业主
 * POST /api/v1/owners/batch-audit
 * @param {Object} data - { ownerIds: [1,2,3], action: "approve" | "reject", rejectReason: "xxx" }
 * @returns {Promise<Object>} - { successCount, failCount, failDetails: [{ownerId, reason}] }
 */
export function batchAuditOwners(data) {
  return request.post('/owners/batch-audit', data)
}

/**
 * 注销业主账号（Super_Admin 专属）
 * POST /api/v1/owners/{ownerId}/disable
 * @param {number} ownerId - 业主 ID
 * @param {Object} data - { reason: "注销原因" }
 * @returns {Promise<Object>}
 */
export function disableOwner(ownerId, data) {
  return request.post(`/owners/${ownerId}/disable`, data)
}
