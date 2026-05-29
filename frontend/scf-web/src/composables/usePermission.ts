import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'

export function usePermission() {
  const auth = useAuthStore()
  const has = (code: string) => computed(() => auth.permissions.includes(code))
  return {
    hasPermission: (code: string) => auth.permissions.includes(code),
    has
  }
}
