import type { FullConfig, FullResult, Reporter, Suite, TestCase, TestResult } from '@playwright/test/reporter'

class PassFailReporter implements Reporter {
  private results: { title: string; status: string }[] = []

  onTestEnd(test: TestCase, result: TestResult) {
    this.results.push({ title: test.title, status: result.status })
  }

  onEnd(result: FullResult) {
    console.log('\n=== EA-031 Browser Smoke Summary ===')
    for (const row of this.results) {
      const mark = row.status === 'passed' ? 'PASS' : 'FAIL'
      console.log(`${mark}  ${row.title}`)
    }
    console.log(result.status === 'passed' ? '\n>>> SMOKE: PASS <<<' : '\n>>> SMOKE: FAIL <<<')
  }

  onBegin(_config: FullConfig, _suite: Suite) {
    console.log('Running pilot smoke (requires backend on :8080 when using dev proxy)...')
  }
}

export default PassFailReporter
