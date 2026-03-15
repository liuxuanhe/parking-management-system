/**
 * 认证相关 API
 */
import { request } from '@/utils/request'

/**
 * 发送验证码
 * @param {string} phoneNumber - 手机号
 */
export function sendVerificationCode(phoneNumber) {
  return request({
    url: '/auth/verification-code',
    method: 'POST',
    data: { phoneNumber },
    noAuth: true
  })
}

/**
 * 业主注册
 * @param {Object} data - 注册信息
 * @param {string} data.phoneNumber - 手机号
 * @param {string} data.verificationCode - 验证码
 * @param {number} data.communityId - 小区 ID
 * @param {string} data.houseNo - 房屋号
 * @param {string} data.idCardLast4 - 身份证后4位
 */
export function register(data) {
  return request({
    url: '/owners/register',
    method: 'POST',
    data,
    noAuth: true
  })
}

/**
 * 业主登录（验证码登录）
 * @param {Object} data - 登录信息
 * @param {string} data.phoneNumber - 手机号
 * @param {string} data.verificationCode - 验证码
 */
export function login(data) {
  return request({
    url: '/auth/owner-login',
    method: 'POST',
    data,
    noAuth: true
  })
}
