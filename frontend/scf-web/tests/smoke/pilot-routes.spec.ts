import { test, expect, type Page } from '@playwright/test'

test.describe.configure({ mode: 'serial' })

async function loginAsAdmin(page: Page) {
  await page.goto('/login')
  await page.getByPlaceholder('platform_admin').fill('platform_admin')
  await page.locator('input[type="password"]').fill('Admin@123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).not.toHaveURL(/\/login\/?$/, { timeout: 20_000 })
  await expect(page.locator('.el-menu-item').filter({ hasText: '工作台' })).toBeVisible({ timeout: 10_000 })
}

async function openMenu(page: Page, label: string) {
  await page.locator('.el-menu-item').filter({ hasText: label }).click()
}

test('SMOKE-01 登录', async ({ page }) => {
  await loginAsAdmin(page)
})

test('SMOKE-02 试点闭环', async ({ page }) => {
  await loginAsAdmin(page)
  await openMenu(page, '试点闭环')
  await expect(page.getByRole('heading', { name: '试点闭环向导' })).toBeVisible()
})

test('SMOKE-03 Saga 监控', async ({ page }) => {
  await loginAsAdmin(page)
  await openMenu(page, 'Saga 监控')
  await expect(page.getByRole('heading', { name: 'Saga 运营监控台' })).toBeVisible()
})

test('SMOKE-04 融资管理', async ({ page }) => {
  await loginAsAdmin(page)
  await openMenu(page, '融资管理')
  await expect(page.getByRole('heading', { name: '融资管理' })).toBeVisible()
})

test('SMOKE-05 清分中心', async ({ page }) => {
  await loginAsAdmin(page)
  await openMenu(page, '清分中心')
  await expect(page.getByRole('heading', { name: '清分中心' })).toBeVisible()
})

test('SMOKE-06 经营看板', async ({ page }) => {
  await loginAsAdmin(page)
  await openMenu(page, '经营看板')
  await expect(page.getByRole('heading', { name: '经营看板' })).toBeVisible()
})
