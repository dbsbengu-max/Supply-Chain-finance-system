import { computed, ref, watch } from 'vue'
import {
  UAT_ACCEPTANCE_STEPS,
  UAT_STORAGE_KEY,
  type UatStepStatus
} from '../constants/uatAcceptanceSteps'

export interface UatStepRecord {
  status: UatStepStatus
  note: string
}

type StoredState = Record<string, UatStepRecord>

function loadState(): StoredState {
  try {
    const raw = localStorage.getItem(UAT_STORAGE_KEY)
    if (!raw) return {}
    return JSON.parse(raw) as StoredState
  } catch {
    return {}
  }
}

function saveState(state: StoredState) {
  localStorage.setItem(UAT_STORAGE_KEY, JSON.stringify(state))
}

export function useUatAcceptance() {
  const state = ref<StoredState>(loadState())

  watch(state, (v) => saveState(v), { deep: true })

  function recordFor(id: string): UatStepRecord {
    return state.value[id] ?? { status: 'pending', note: '' }
  }

  function setStatus(id: string, status: UatStepStatus) {
    const cur = recordFor(id)
    state.value = { ...state.value, [id]: { ...cur, status } }
  }

  function setNote(id: string, note: string) {
    const cur = recordFor(id)
    state.value = { ...state.value, [id]: { ...cur, note } }
  }

  function resetAll() {
    state.value = {}
    localStorage.removeItem(UAT_STORAGE_KEY)
  }

  const summary = computed(() => {
    let pass = 0
    let fail = 0
    let skip = 0
    let pending = 0
    for (const step of UAT_ACCEPTANCE_STEPS) {
      const s = recordFor(step.id).status
      if (s === 'pass') pass++
      else if (s === 'fail') fail++
      else if (s === 'skip') skip++
      else pending++
    }
    return { pass, fail, skip, pending, total: UAT_ACCEPTANCE_STEPS.length }
  })

  const allPassed = computed(
    () => summary.value.pass + summary.value.skip === summary.value.total && summary.value.fail === 0
  )

  function exportMarkdown(): string {
    const lines = [
      '# EA-049 UAT 签字包（EA-050）',
      '',
      `导出时间：${new Date().toLocaleString('zh-CN')}`,
      '',
      `进度：通过 ${summary.value.pass} / 跳过 ${summary.value.skip} / 未通过 ${summary.value.fail} / 待验 ${summary.value.pending}`,
      '',
      '| # | 模块 | 状态 | 备注 |',
      '|---|---|---|---|'
    ]
    for (const step of UAT_ACCEPTANCE_STEPS) {
      const rec = recordFor(step.id)
      const note = (rec.note || step.defaultNote || '').replace(/\|/g, '\\|').replace(/\n/g, ' ')
      lines.push(`| ${step.id} | ${step.module} | ${rec.status} | ${note || '—'} |`)
    }
    lines.push('', '---', '签字：__________  日期：__________')
    return lines.join('\n')
  }

  return {
    steps: UAT_ACCEPTANCE_STEPS,
    recordFor,
    setStatus,
    setNote,
    resetAll,
    summary,
    allPassed,
    exportMarkdown
  }
}
