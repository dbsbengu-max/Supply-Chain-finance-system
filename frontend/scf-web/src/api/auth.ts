import http from './http'

export interface LoginPayload {
  login_name: string
  password: string
}

export async function login(payload: LoginPayload) {
  const { data } = await http.post('/auth/login', payload)
  return data
}

export async function listIdentities() {
  const { data } = await http.get('/auth/identities')
  return data
}

export async function switchIdentity(identityId: string) {
  const { data } = await http.post('/auth/switch-identity', { identity_id: identityId })
  return data
}

export async function fetchPermissions() {
  const { data } = await http.get('/auth/permissions')
  return data
}

export async function listTodoTasks() {
  const { data } = await http.get('/bpm/tasks/todo')
  return data
}
