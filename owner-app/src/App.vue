<script>
export default {
  onLaunch() {
    // 检查登录状态
    const token = uni.getStorageSync('accessToken')
    if (!token) {
      uni.reLaunch({ url: '/pages/login/login' })
    }
  }
}
</script>

<style>
page {
  background-color: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
</style>
