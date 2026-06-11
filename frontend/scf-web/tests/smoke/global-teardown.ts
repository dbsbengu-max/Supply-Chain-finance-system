/** Force clean process exit after Playwright finishes (Windows dev-server hang workaround). */
import type { FullConfig } from '@playwright/test'

export default async function globalTeardown(_config: FullConfig) {
  // Allow Playwright reporters to flush stdout before exit.
  await new Promise((r) => setTimeout(r, 200))
}
