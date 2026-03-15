/**
 * 车牌管理相关 API
 */
import { get, post, del } from '@/utils/request'

/** 查询车牌列表 */
export function getVehicleList() {
  return get('/vehicles')
}

/** 添加车牌 */
export function addVehicle(data) {
  return post('/vehicles', data)
}

/** 删除车牌 */
export function deleteVehicle(vehicleId) {
  return del(`/vehicles/${vehicleId}`)
}

/** 设置 Primary 车辆 */
export function setPrimary(vehicleId, data) {
  return post(`/vehicles/${vehicleId}/primary`, data)
}
