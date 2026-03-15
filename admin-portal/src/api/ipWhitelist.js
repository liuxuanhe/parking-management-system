/**
 * IP 白名单相关 API
 * 对接后端 IpWhitelistController
 */
import request from './request'

/**
 * 查询 IP 白名单列表
 * GET /api/v1/ip-whitelist
 */
export function getIpWhitelistList() {
  return request.get('/ip-whitelist')
}

/**
 * 添加 IP 白名单
 * POST /api/v1/ip-whitelist
 */
export function addIpWhitelist(params) {
  return request.post('/ip-whitelist', null, { params })
}

/**
 * 删除 IP 白名单
 * DELETE /api/v1/ip-whitelist/{id}
 */
export function deleteIpWhitelist(id) {
  return request.delete(`/ip-whitelist/${id}`)
}
