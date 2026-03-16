/**
 * Visitor 权限相关 API
 */
import { get, post } from '@/utils/request'

/** 申请 Visitor 权限 */
export function applyVisitor(data) {
  return post('/visitors/apply', data)
}

/** 查询 Visitor 权限列表（按房屋号） */
export function getVisitorList() {
  return get('/visitors/by-house')
}

/** 查询月度配额 */
export function getQuota() {
  return get('/visitors/quota')
}
