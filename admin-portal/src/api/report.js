/**
 * 报表相关 API
 * 对接后端 ReportController
 */
import request from './request'

/**
 * 查询入场趋势报表
 * GET /api/v1/reports/entry-trend
 * @param {Object} params - { communityId, startDate, endDate, granularity }
 * @returns {Promise<Object>} - { items: [{date, entryCount, exitCount}] }
 */
export function getEntryTrend(params) {
  return request.get('/reports/entry-trend', { params })
}

/**
 * 查询车位使用率报表
 * GET /api/v1/reports/space-usage
 * @param {Object} params - { communityId, startDate, endDate }
 * @returns {Promise<Object>} - { items: [{date, usageRate, avgOccupied, totalSpaces}] }
 */
export function getSpaceUsage(params) {
  return request.get('/reports/space-usage', { params })
}

/**
 * 查询峰值时段报表
 * GET /api/v1/reports/peak-hours
 * @param {Object} params - { communityId, startDate, endDate }
 * @returns {Promise<Object>} - { items: [{hour, avgCount}] }
 */
export function getPeakHours(params) {
  return request.get('/reports/peak-hours', { params })
}
