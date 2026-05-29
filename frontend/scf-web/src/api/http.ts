import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  config.headers['X-Request-Id'] = crypto.randomUUID()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  if (auth.operatorId) {
    config.headers['X-Operator-Id'] = auth.operatorId
  }
  if (auth.projectId) {
    config.headers['X-Project-Id'] = auth.projectId
  }
  return config
})

export default http
