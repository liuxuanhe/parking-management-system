/**
 * 僵尸车辆相关 API
 * 对接后端 ZombieVehicleController
 */
import request from './request'

/**
 * 查询僵尸车辆列表（支持按状态筛选和分页）
 * GET /api/v1/zombie-vehicles?communityId=xxx&status=xxx&page=1&pageSize=10
 * @param {Object} params - { communityId, status, page, pageSize }
 * @returns {Promise<Object>} - { records: [...], total }
 */
export function getZombieVehicleList(params) {
  return request.get('/zombie-vehicles', { params })
}

/**
 * 处理僵尸车辆
 * POST /api/v1/zombie-vehicles/{zombieId}/handle
 * @param {number} zombieId - 僵尸车辆记录 ID
 * @param {Object} data - { handleType: "contacted"|"resolved"|"ignored", handleRecord: "处理记录" }
 * @returns {Promise<Object>}
 */
export function handleZombieVehicle(zombieId, data) {
  return request.post(`/zombie-vehicles/${zombieId}/handle`, data)
}
