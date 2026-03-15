/**
 * 审计日志相关 API
 * 对接后端 AuditLogController
 */
import request from './request'

/**
 * 查询操作日志
 * GET /api/v1/audit/operation-logs
 * @param {Object} params - { communityId, operatorId, operationType, startTime, endTime }
 * @returns {Promise<Array>} - 操作日志列表
 */
export function getOperationLogs(params) {
  return request.get('/audit/operation-logs', { params })
}

/**
 * 查询访问日志
 * GET /api/v1/audit/access-logs
 * @param {Object} params - { communityId, userId, apiPath, startTime, endTime }
 * @returns {Promise<Array>} - 访问日志列表
 */
export function getAccessLogs(params) {
  return request.get('/audit/access-logs', { params })
}

/**
 * 导出审计日志（异步任务）
 * POST /api/v1/audit/logs/export
 * @param {Object} params - { communityId, operatorId, operatorName, exportType, queryParams, needRawData }
 * @returns {Promise<Object>} - 导出任务信息
 */
export function exportAuditLogs(params) {
  return request.post('/audit/logs/export', null, { params })
}
