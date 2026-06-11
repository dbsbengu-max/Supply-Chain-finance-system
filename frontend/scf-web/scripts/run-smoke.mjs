/**
 * Run smoke tests and exit with playwright's code (avoids Windows hanging on open handles).
 */
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const args = ['playwright', 'test', 'tests/smoke', '--config=playwright.config.ts', ...process.argv.slice(2)]

const child = spawn(process.platform === 'win32' ? 'npx.cmd' : 'npx', args, {
  cwd: root,
  stdio: 'inherit',
  shell: process.platform === 'win32',
  env: process.env
})

child.on('error', (err) => {
  console.error(err)
  process.exit(1)
})

child.on('close', (code, signal) => {
  if (signal) {
    process.exit(1)
  }
  process.exit(code ?? 1)
})
