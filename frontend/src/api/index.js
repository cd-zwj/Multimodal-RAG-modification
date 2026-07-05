import axios from 'axios'

const API = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 60000
})

// Request interceptor to automatically add token
API.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['satoken'] = token
  }
  return config
}, error => {
  return Promise.reject(error)
})

// Response interceptor to handle errors
API.interceptors.response.use(
  response => {
    // If response is SSE, return response directly
    if (response.headers['content-type']?.includes('text/event-stream')) {
      return response
    }
    return response.data
  },
  error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    const msg = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export const auth = {
  login: (username, password) => API.post('/auth/login', { username, password }),
  register: (username, password, email) => API.post('/auth/register', { username, password, email }),
  logout: () => API.post('/auth/logout'),
  me: () => API.get('/auth/me'),
  changePassword: (currentPassword, newPassword, confirmNewPassword) => 
    API.post('/auth/password/change', { currentPassword, newPassword, confirmNewPassword }),
  resetPassword: (username, newPassword, confirmNewPassword) => 
    API.post('/auth/password/reset', { username, newPassword, confirmNewPassword }),
  forgotRequest: (username) => API.post('/auth/password/forgot/request', { username }),
  forgotConfirm: (username, resetCode, newPassword, confirmNewPassword) => 
    API.post('/auth/password/forgot/confirm', { username, resetCode, newPassword, confirmNewPassword })
}

export const ai = {
  createSession: (userId) => API.post('/ai/session/create', { userId }),
  listSessions: (userId) => API.post('/ai/session/list', { userId }),
  deleteSession: (sessionId, userId) => API.post('/ai/session/delete', { sessionId, userId }),
  extractProfile: (sessionId, userId) => API.post('/ai/session/extract-profile', { sessionId, userId }),
  getHistory: (sessionId) => API.get('/ai/session/history', { params: { sessionId } }),
  asr: (audioBlob) => {
    const formData = new FormData()
    formData.append('file', audioBlob, 'voice.pcm')
    return API.post('/ai/asr', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

export const llm = {
  listProviders: (params = {}) => API.get('/llm/providers', { params }),
  createProvider: (payload) => API.post('/llm/providers', payload),
  updateProvider: (id, payload) => API.put(`/llm/providers/${id}`, payload),
  enableProvider: (id) => API.post(`/llm/providers/${id}/enable`),
  disableProvider: (id) => API.post(`/llm/providers/${id}/disable`),
  deleteProvider: (id) => API.delete(`/llm/providers/${id}`),
  listModels: (params = {}) => API.get('/llm/models', { params }),
  createModel: (payload) => API.post('/llm/models', payload),
  updateModel: (id, payload) => API.put(`/llm/models/${id}`, payload),
  enableModel: (id) => API.post(`/llm/models/${id}/enable`),
  disableModel: (id) => API.post(`/llm/models/${id}/disable`),
  deleteModel: (id) => API.delete(`/llm/models/${id}`),
  debugProvider: (payload) => API.post('/llm/debug', payload),
  opsMetrics: () => API.get('/llm/ops/metrics'),
  debugProviderStream: async (payload, onEvent) => {
    const token = localStorage.getItem('token')
    const response = await fetch(`${API.defaults.baseURL || ''}/llm/debug/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { satoken: token } : {})
      },
      body: JSON.stringify(payload)
    })
    if (!response.ok || !response.body) {
      throw new Error(`流式调试失败：${response.status}`)
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() || ''
      events.forEach(block => {
        const eventName = block.match(/^event:\s*(.+)$/m)?.[1] || 'message'
        const data = block
          .split(/\r?\n/)
          .filter(line => line.startsWith('data:'))
          .map(line => line.slice(5).trimStart())
          .join('\n')
        onEvent?.({ event: eventName, data })
      })
    }
  }
}

export const documents = {
  list: (params) => API.get('/api/documents', { params }),
  getStatus: (fileHash, userId) => API.get(`/api/documents/status/${fileHash}`, { params: { userId } }),
  delete: (fileHash, userId) => API.delete(`/api/documents/${fileHash}`, { params: { userId } }),
  reprocess: (fileHash, userId) => API.post(`/api/documents/${fileHash}/reprocess`, null, { params: { userId } }),
  getDeleteStatus: (taskId) => API.get(`/api/documents/delete-status/${taskId}`)
}

export const upload = {
  check: (fileHash, userId) => API.get('/api/upload/check', { params: { fileHash, userId } }),
  upload: (file, fileHash, userId) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('fileHash', fileHash)
    formData.append('userId', userId)
    return API.post('/api/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  uploadBatch: (files, hashes, userId) => {
    const formData = new FormData()
    files.forEach(f => formData.append('files', f))
    hashes.forEach(h => formData.append('fileHashes', h))
    formData.append('userId', userId)
    return API.post('/api/upload/batch', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  chunkCheck: (fileHash, filename, fileSize, totalChunks, userId) => 
    API.get('/api/upload/chunk/check', { params: { fileHash, filename, fileSize, totalChunks, userId } }),
  uploadChunk: (fileHash, chunkNumber, chunkBlob, userId) => {
    const formData = new FormData()
    formData.append('fileHash', fileHash)
    formData.append('chunkNumber', chunkNumber)
    formData.append('chunk', chunkBlob)
    formData.append('userId', userId)
    return API.post('/api/upload/chunk', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  mergeChunks: (fileHash, filename, userId) => {
    const params = new URLSearchParams()
    params.append('fileHash', fileHash)
    params.append('filename', filename)
    params.append('userId', userId)
    return API.post('/api/upload/chunk/merge', params)
  }
}

export default API
