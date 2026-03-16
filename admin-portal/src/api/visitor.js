/**
 * Visitor 管理相关 API
 * 对接后端 VisitorController
 */
import request from './request'

/**
 * 查询 Visitor 申请列表（支持按状态筛选）
 * GET /api/v1/visitors?status=xxx&communityId=xxx&page=1&pageSize=10
 * @param {Object} params - { status, communityId, page, pageSize }
 * @returns {Promise<Object>} - { records: [...], total }
 */
export function getVisitorList(params) {
  return request.get('/visitors', { params })
}

/**
 * 审批单个 Visitor 申请
 * POST /api/v1/visitors/{visitorId}/audit?adminId=xxx&communityId=xxx
 * @param {number} visitorId - Visitor 申请 ID
 * @param {Object} data - { action: "approve" | "reject", rejectReason: "xxx" }
 * @param {Object} params - { adminId, communityId }
 * @returns {Promise<Object>}
 */
export function auditVisitor(visitorId, data, params) {
  return request.post(`/visitors/${visitorId}/audit`, data, { params })
}

/**
 * 批量审批 Visitor 申请
 * POST /api/v1/visitors/batch-audit?adminId=xxx&communityId=xxx
 * @param {Object} data - { visitorIds: [1,2,3], action: "approve" | "reject", rejectReason: "xxx" }
 * @param {Object} params - { adminId, communityId }
 * @returns {Promise<Object>} - { successCount, failCount, failDetails: [{visitorId, reason}] }
 */
export function batchAuditVisitors(data, params) {
  return request.post('/visitors/batch-audit', data, { params })
}
