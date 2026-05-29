import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, switchIdentity as switchIdentityApi, fetchPermissions } from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('scf_token'))
  const userName = ref<string | null>(localStorage.getItem('scf_user_name'))
  const operatorId = ref<string | null>(localStorage.getItem('scf_operator_id'))
  const projectId = ref<string | null>(localStorage.getItem('scf_project_id'))
  const identities = ref<any[]>([])
  const permissions = ref<string[]>(JSON.parse(localStorage.getItem('scf_permissions') || '[]'))

  async function login(loginName: string, password: string) {
    const res = await loginApi({ login_name: loginName, password })
    if (!res.success) throw new Error(res.message)
    applySession(res.data)
    await refreshPermissions()
  }

  async function switchIdentity(identityId: string) {
    const res = await switchIdentityApi(identityId)
    if (!res.success) throw new Error(res.message)
    applySession(res.data)
    await refreshPermissions()
  }

  async function refreshPermissions() {
    const res = await fetchPermissions()
    if (res.success) {
      permissions.value = res.data || []
      localStorage.setItem('scf_permissions', JSON.stringify(permissions.value))
    }
  }

  function applySession(data: any) {
    token.value = data.accessToken
    userName.value = data.userName
    identities.value = data.identities || []
    const current = identities.value.find((i: any) => i.identityId === data.currentIdentityId)
    operatorId.value = current?.operatorId ?? null
    projectId.value = current?.projectId ?? null
    localStorage.setItem('scf_token', data.accessToken)
    localStorage.setItem('scf_user_name', data.userName)
    localStorage.setItem('scf_operator_id', operatorId.value || '')
    localStorage.setItem('scf_project_id', projectId.value || '')
  }

  function logout() {
    token.value = null
    userName.value = null
    operatorId.value = null
    projectId.value = null
    identities.value = []
    permissions.value = []
    localStorage.clear()
  }

  return { token, userName, operatorId, projectId, identities, permissions, login, switchIdentity, refreshPermissions, logout }
})
