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

const ROUTE_BY_MENU: Record<string, string> = {
  '试点闭环': '/pilot/closure',
  '补偿池': '/saga/ops',
  '融资管理': '/finance/applications',
  '清分中心': '/accounts/clearing',
  '经营看板': '/bi/dashboard',
  '功能上线': '/launch/hub',
  'UAT 验收': '/uat/acceptance',
  '签章中心': '/documents/center',
  '客户/KYC': '/customers'
}

async function openMenu(page: Page, label: string) {
  const currentPath = new URL(page.url()).pathname
  const item = page.getByRole('menuitem', { name: label, exact: true })
  if (await item.isVisible()) {
    await item.click()
    await page.waitForTimeout(300)
  }
  const target = ROUTE_BY_MENU[label]
  if (target && new URL(page.url()).pathname === currentPath) {
    await page.goto(target)
  }
}

test('SMOKE-01 登录', async ({ page }) => {
  await loginAsAdmin(page)
})

test('SMOKE-02 试点闭环', async ({ page }) => {
  await loginAsAdmin(page)
  await openMenu(page, '试点闭环')
  await expect(page.getByRole('heading', { name: '试点闭环向导' })).toBeVisible()
})

test('SMOKE-03 补偿池', async ({ page }) => {
  await loginAsAdmin(page)
  await openMenu(page, '补偿池')
  await expect(page.getByRole('heading', { name: /补偿池/ })).toBeVisible()
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

test('SMOKE-07 功能上线收口', async ({ page }) => {
  await loginAsAdmin(page)
  await openMenu(page, '功能上线')
  await expect(page.getByRole('heading', { name: '功能上线收口' })).toBeVisible()
  await page.goto('/uat/acceptance')
  await expect(page.getByRole('heading', { name: 'UAT 验收入口' })).toBeVisible()
  await expect(page.getByText('M1', { exact: true })).toBeVisible()
  await expect(page.getByText('M12', { exact: true })).toBeVisible()
})

test('SMOKE-08 签章中心', async ({ page }) => {
  await loginAsAdmin(page)
  await openMenu(page, '签章中心')
  await expect(page.getByRole('heading', { name: '签章中心' })).toBeVisible()
})

test('SMOKE-09 客户/KYC', async ({ page }) => {
  await loginAsAdmin(page)
  await openMenu(page, '客户/KYC')
  await expect(page.getByRole('heading', { name: '客户 / KYC' })).toBeVisible()
})
