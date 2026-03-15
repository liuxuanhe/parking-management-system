/**
 * Axios 统一封装
 * 处理 ApiResponse {code, message, data, requestId} 格式
 * 自动携带 Authorization、X-Timestamp、X-Nonce、X-Signature 头
 */
import axios from 'axios'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

/** 签名密钥，与后端 application.yml 中 signature.secret-key 保持一致 */
const SIGNATURE_SECRET_KEY = 'c2lnbmF0dXJlLXNlY3JldC1rZXktZm9yLXBhcmtpbmctc3lzdGVtLTIwMjQ='

/**
 * 生成 SHA-256 十六进制摘要
 * 使用 Web Crypto API（浏览器原生支持）
 * @param {string} data - 待签名字符串
 * @returns {Promise<string>} 十六进制小写签名
 */
async function sha256Hex(data) {
  const encoder = new TextEncoder()
  const buffer = await crypto.subtle.digest('SHA-256', encoder.encode(data))
  return Array.from(new Uint8Array(buffer))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('')
}

/**
 * 生成安全请求参数：timestamp、nonce、signature
 * 签名算法与后端一致：SHA256(timestamp + nonce + requestBody + secretKey)
 * @param {string} requestBody - 请求体 JSON 字符串
 * @returns {Promise<{timestamp: string, nonce: string, signature: string}>}
 */
async function generateSecurityHeaders(requestBody) {
  const timestamp = String(Date.now())
  const nonce = `${timestamp}_${Math.random().toString(36).substring(2, 15)}`
  const signature = await sha256Hex(timestamp + nonce + (requestBody || '') + SIGNATURE_SECRET_KEY)
  return { timestamp, nonce, signature }
}

const service = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * 请求拦截器：自动注入 Access Token 和安全头（X-Timestamp、X-Nonce、X-Signature）
 */
service.interceptors.request.use(
  async (config) => {
    const authStore = useAuthStore()
    if (authStore.accessToken) {
      config.headers['Authorization'] = `Bearer ${authStore.accessToken}`
    }

    // 后端拦截器阶段 ContentCachingRequestWrapper 尚未缓存请求体，
    // extractRequestBody 返回空字符串，因此签名计算时 requestBody 传空
    const { timestamp, nonce, signature } = await generateSecurityHeaders('')
    config.headers['X-Timestamp'] = timestamp
    config.headers['X-Nonce'] = nonce
    config.headers['X-Signature'] = signature

    return config
  },
  (error) => Promise.reject(error)
)

/**
 * 响应拦截器：统一处理 ApiResponse 格式
 * code === 0 表示成功，否则抛出错误
 */
service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 0) {
      return res.data
    }
    // 401 未授权，清除登录态并跳转登录页
    if (res.code === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      router.push('/login')
      message.error('登录已过期，请重新登录')
      return Promise.reject(new Error('未授权'))
    }
    // 其他业务错误
    message.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    message.error('网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default service
