import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.SMOKE_BASE_URL || 'http://localhost:5173'

export default defineConfig({
  testDir: './tests/smoke',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  reporter: [['list'], ['./tests/smoke/pass-fail-reporter.ts']],
  use: {
    ...devices['Desktop Chrome'],
    baseURL,
    trace: 'retain-on-failure'
  },
  webServer: process.env.SMOKE_SKIP_WEBSERVER
    ? undefined
    : {
        command: 'npm run dev',
        url: baseURL,
        reuseExistingServer: true,
        timeout: 120_000
      }
})
