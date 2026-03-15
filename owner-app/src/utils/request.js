/**
 * 统一请求封装
 * 自动携带 Access Token、处理 Token 刷新、统一错误处理
 */

const BASE_URL = '/api/v1'

/**
 * 生成请求签名相关参数
 */
function generateSecurityParams() {
  const timestamp = Date.now()
  const nonce = `${timestamp}_${Math.random().toString(36).substring(2, 15)}`
  return { timestamp, nonce }
}

/**
 * 发起 HTTP 请求
 * @param {Object} options - 请求配置
 * @param {string} options.url - 请求路径（不含 BASE_URL）
 * @param {string} options.method - 请求方法
 * @param {Object} options.data - 请求数据
 * @param {boolean} options.noAuth - 是否跳过鉴权
 * @returns {Promise<Object>} 响应数据
 */
export function request(options) {
  return new Promise((resolve, reject) => {
    const { timestamp, nonce } = generateSecurityParams()
    const header = {
      'Content-Type': 'application/json',
      'X-Timestamp': String(timestamp),
      'X-Nonce': nonce
    }

    // 添加 Token
    if (!options.noAuth) {
      const token = uni.getStorageSync('accessToken')
      if (token) {
        header['Authorization'] = `Bearer ${token}`
      }
    }

    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header,
      success: (res) => {
        const data = res.data
        if (data.code === 0) {
          resolve(data.data)
        } else if (data.code === 401) {
          // Token 过期，尝试刷新
          handleTokenExpired().then(() => {
            // 重试原请求
            request(options).then(resolve).catch(reject)
          }).catch(() => {
            // 刷新失败，跳转登录
            redirectToLogin()
            reject(new Error('登录已过期'))
          })
        } else {
          uni.showToast({ title: data.message || '请求失败', icon: 'none' })
          reject(new Error(data.message || '请求失败'))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常', icon: 'none' })
        reject(err)
      }
    })
  })
}

/**
 * 处理 Token 过期，尝试使用 Refresh Token 刷新
 */
async function handleTokenExpired() {
  const refreshToken = uni.getStorageSync('refreshToken')
  if (!refreshToken) {
    throw new Error('无 Refresh Token')
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + '/auth/refresh',
      method: 'POST',
      data: { refreshToken },
      header: { 'Content-Type': 'application/json' },
      success: (res) => {
        if (res.data.code === 0) {
          const { accessToken } = res.data.data
          uni.setStorageSync('accessToken', accessToken)
          resolve()
        } else {
          reject(new Error('刷新 Token 失败'))
        }
      },
      fail: reject
    })
  })
}

/**
 * 跳转到登录页
 */
function redirectToLogin() {
  uni.removeStorageSync('accessToken')
  uni.removeStorageSync('refreshToken')
  uni.reLaunch({ url: '/pages/login/login' })
}

// 快捷方法
export const get = (url, data) => request({ url, method: 'GET', data })
export const post = (url, data) => request({ url, method: 'POST', data })
export const put = (url, data) => request({ url, method: 'PUT', data })
export const del = (url, data) => request({ url, method: 'DELETE', data })
