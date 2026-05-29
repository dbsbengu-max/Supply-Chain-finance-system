import { chromium } from 'playwright'
import { execSync } from 'child_process'
import { fileURLToPath } from 'url'
import path from 'path'

const BASE = 'http://localhost:5173'
const __dirname = path.dirname(fileURLToPath(import.meta.url))
const RESET_SQL = path.resolve(__dirname, '../../../backend/scf-server/scripts/reset-clearing-demo.sql')

function resetDemoData() {
  const pgPass = process.env.PGPASSWORD || 'scf_dev_pass'
  const psql = process.env.PSQL_BIN || 'C:\\Program Files\\PostgreSQL\\16\\bin\\psql.exe'
  execSync(`"${psql}" -h localhost -U scf -d scf -f "${RESET_SQL}"`, {
    env: { ...process.env, PGPASSWORD: pgPass },
    stdio: 'pipe'
  })
  console.log('0. Reset FIN_CLEAR_OK demo finance')
}

async function waitFinanceOptions(page) {
  await page.waitForResponse(
    (r) => r.url().includes('/finance/applications') && r.status() === 200,
    { timeout: 15000 }
  ).catch(() => {})
}

/** Click Element Plus dropdown item (avoids viewport/teleport click issues). */
async function pickSelectOption(page, wrapperLocator, optionText) {
  await wrapperLocator.click()
  const item = page
    .locator('.el-select-dropdown:visible .el-select-dropdown__item')
    .filter({ hasText: optionText })
    .first()
  await item.waitFor({ timeout: 15000 })
  await item.evaluate((el) => el.click())
}

async function pickFinanceOption(page, wrapperLocator) {
  await pickSelectOption(page, wrapperLocator, 'FIN-CLR-OK')
}

async function login(page, user, pass) {
  await page.goto(`${BASE}/login`)
  await page.getByPlaceholder('platform_admin').fill(user)
  await page.locator('input[type="password"]').fill(pass)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(`${BASE}/`, { timeout: 15000 })
}

async function main() {
  resetDemoData()
  const browser = await chromium.launch({ headless: true, channel: 'chrome' })
  const page = await browser.newPage()
  page.setDefaultTimeout(20000)
  const flowNo = `UI-E2E-${Date.now()}`

  console.log('1. Login funding_user (default PJ001)')
  await login(page, 'funding_user', 'Fund@123')

  console.log('2. Bank flows + balance summary + import')
  await page.goto(`${BASE}/accounts/bank-flows`)
  await waitFinanceOptions(page)
  await page.getByText('资金账户余额').waitFor()
  await page.getByRole('button', { name: '导入流水' }).click()
  await page.locator('.el-dialog').getByPlaceholder('ACC_REPAY_001').fill('ACC_REPAY_001')
  await page.locator('.el-dialog input').nth(1).fill(flowNo)
  await page.locator('.el-dialog input').nth(2).fill('120000.00')
  await page.locator('.el-dialog input').nth(6).fill(new Date().toISOString())
  await page.getByRole('button', { name: '确认导入' }).click()
  await page.getByText('流水导入成功').waitFor()

  console.log('3. Match finance')
  await page.getByRole('button', { name: '匹配融资' }).first().click()
  await pickFinanceOption(page, page.locator('.el-dialog .el-select .el-select__wrapper'))
  await page.getByRole('button', { name: '确认匹配' }).click()
  await page.getByText('匹配成功').waitFor()

  console.log('4. Clearing: entry → calculate → execute')
  await page.goto(`${BASE}/accounts/clearing`)
  await waitFinanceOptions(page)
  await page.getByText('资金账户余额').waitFor()
  await pickFinanceOption(
    page,
    page.locator('.el-form-item').filter({ hasText: '融资单 ID' }).locator('.el-select__wrapper')
  )

  const entryResp = page.waitForResponse(
    (r) => r.url().includes('/accounts/clearing/entry') && r.status() === 200
  )
  await page.getByRole('button', { name: '加载清分入口' }).click()
  await entryResp
  await page.getByText('待还本金').waitFor()

  await pickSelectOption(
    page,
    page.locator('.el-form-item').filter({ hasText: '已匹配流水' }).locator('.el-select__wrapper'),
    flowNo
  )
  await pickSelectOption(
    page,
    page.locator('.el-form-item').filter({ hasText: '清分规则' }).locator('.el-select__wrapper'),
    '凭证融资标准清分规则'
  )

  const calcResp = page.waitForResponse(
    (r) => r.url().includes('/accounts/clearing/calculate') && r.status() === 200
  )
  await page.getByRole('button', { name: '试算' }).click()
  await calcResp
  await page.getByText('罚息').waitFor()
  await page.getByRole('button', { name: '执行清分' }).click()
  await page.getByRole('button', { name: '确认执行' }).click()
  await page.locator('.el-message__content').filter({ hasText: /清分执行成功|幂等重放/ }).waitFor({ timeout: 20000 })

  console.log('4b. Clearing rules: list → create → edit → submit → approve')
  await page.goto(`${BASE}/accounts/clearing-rules`)
  const listResp = page.waitForResponse(
    (r) => r.url().includes('/accounts/clearing-rules') && r.request().method() === 'GET' && r.status() === 200
  )
  await listResp
  await page.getByRole('button', { name: '新建规则' }).click()
  const ruleName = `UI-E2E-规则-${Date.now()}`
  await page.locator('.el-dialog input').first().fill(ruleName)
  await page.locator('.el-dialog textarea').first().fill(
    JSON.stringify({ priority: ['penalty', 'fee', 'interest', 'principal'] }, null, 2)
  )
  const createResp = page.waitForResponse(
    (r) => r.url().endsWith('/accounts/clearing-rules') && r.request().method() === 'POST' && r.status() === 200
  )
  await page.getByRole('button', { name: '保存' }).click()
  await createResp
  await page.getByText('已保存').waitFor()

  const ruleRow = () => page.locator('.el-table__body tr').filter({ hasText: ruleName })
  const updatedName = `${ruleName}-更新`
  await ruleRow().getByRole('button', { name: '编辑' }).click()
  await page.locator('.el-dialog input').first().fill(updatedName)
  const updateResp = page.waitForResponse(
    (r) => r.url().includes('/accounts/clearing-rules/') && r.request().method() === 'PUT' && r.status() === 200
  )
  await page.getByRole('button', { name: '保存' }).click()
  await updateResp
  await page.getByText('已保存').waitFor()

  const updatedRow = () => page.locator('.el-table__body tr').filter({ hasText: updatedName })
  await updatedRow().getByRole('button', { name: '提交' }).click()
  await page.getByRole('button', { name: '确认' }).click()
  await page.getByText('已提交').waitFor()

  await updatedRow().getByRole('button', { name: '批准' }).click()
  await page.getByRole('button', { name: '确认' }).click()
  await page.getByText('已批准').waitFor()
  await page.getByText(updatedName).waitFor()

  console.log('5. Route guard + menu: member_user')
  const memberContext = await browser.newContext()
  const memberPage = await memberContext.newPage()
  memberPage.setDefaultTimeout(20000)
  await login(memberPage, 'member_user', 'Member@123')
  const menuText = await memberPage.locator('.el-menu').innerText()
  for (const label of ['银行流水', '清分中心', '清分规则']) {
    if (menuText.includes(label)) {
      throw new Error(`Member menu should hide "${label}"`)
    }
  }
  await memberPage.goto(`${BASE}/accounts/clearing`)
  await memberPage.getByText('无权访问').waitFor()
  if (!memberPage.url().includes('/forbidden')) {
    throw new Error(`Expected forbidden route, got ${memberPage.url()}`)
  }
  await memberPage.goto(`${BASE}/accounts/clearing-rules`)
  await memberPage.getByText('无权访问').waitFor()
  if (!memberPage.url().includes('/forbidden')) {
    throw new Error(`Expected forbidden for clearing-rules, got ${memberPage.url()}`)
  }
  await memberPage.goto(`${BASE}/accounts/bank-flows`)
  if (!memberPage.url().includes('/forbidden')) {
    throw new Error(`Expected forbidden for bank-flows, got ${memberPage.url()}`)
  }
  await memberContext.close()

  await browser.close()
  console.log('BROWSER E2E PASSED')
}

main().catch(async (err) => {
  console.error('BROWSER E2E FAILED', err)
  process.exit(1)
})
