import { defineConfig, devices } from '@playwright/test'

const PORT = process.env.CI ? 4173 : 5173
const BASE_URL = `http://localhost:${PORT}`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : 'html',
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: process.env.CI
    ? {
        command: 'npm run preview -- --host 0.0.0.0 --port 4173',
        url: 'http://localhost:4173',
        reuseExistingServer: false,
        timeout: 120 * 1000,
      }
    : undefined,
})
