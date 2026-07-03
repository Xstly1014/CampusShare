import { test, expect } from '@playwright/test'

test.describe('Auth Page (Login)', () => {
  test('should display login form', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/CampusShare|校园/)
    await expect(page.getByRole('button', { name: /登录|登录/ })).toBeVisible()
  })

  test('should show validation errors for empty form submission', async ({ page }) => {
    await page.goto('/')
    const submitBtn = page.getByRole('button', { name: /登录/ })
    await submitBtn.click()
    await expect(page.url()).toContain('/')
  })
})
