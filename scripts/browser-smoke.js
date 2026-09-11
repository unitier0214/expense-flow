const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const baseUrl = process.env.BASE_URL || 'http://localhost:8080';
const screenshotDirectory = process.env.BROWSER_SCREENSHOT_DIR || 'test-results/browser';
fs.mkdirSync(screenshotDirectory, { recursive: true });

const screenshot = async (page, name) => {
  await page.screenshot({
    path: path.join(screenshotDirectory, name),
    fullPage: true,
  });
};

const login = async (page, username) => {
  await page.goto(`${baseUrl}/login`);
  await page.getByLabel('ユーザー名').fill(username);
  await page.getByLabel('パスワード').fill('demo-password');
  await Promise.all([
    page.waitForURL('**/expenses'),
    page.getByRole('button', { name: 'ログイン' }).click(),
  ]);
};

const logout = async (page) => {
  await Promise.all([
    page.waitForURL(/\/login\?logout$/),
    page.getByRole('button', { name: 'ログアウト' }).click(),
  ]);
};

async function run() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ locale: 'ja-JP' });
  const page = await context.newPage();
  let currentStep = 'login';

  try {
    await page.goto(`${baseUrl}/login`);
    await screenshot(page, '01-login.png');

    await page.getByLabel('ユーザー名').fill('demo.employee');
    await page.getByLabel('パスワード').fill('demo-password');
    await Promise.all([
      page.waitForURL('**/expenses'),
      page.getByRole('button', { name: 'ログイン' }).click(),
    ]);
    await screenshot(page, '02-expenses-list.png');

    currentStep = 'create';
    await page.getByRole('link', { name: '新規申請' }).click();
    const title = `ブラウザ確認-${Date.now()}`;
    await page.getByLabel('件名').fill(title);
    await page.getByLabel('用途').fill('ブラウザで作成した申請');
    await page.getByLabel('分類').selectOption('OTHER');
    await page.getByLabel('利用日').fill('2026-09-08');
    await page.getByLabel('金額（円）').fill('1500');
    await Promise.all([
      page.waitForURL(/\/expenses\/\d+$/),
      page.getByRole('button', { name: '下書きを保存' }).click(),
    ]);
    const expensePath = new URL(page.url()).pathname;
    await expectText(page.locator('#page-title'), title);
    await screenshot(page, '03-created-detail.png');

    currentStep = 'edit-validation-recovery';
    await page.getByRole('link', { name: '編集' }).click();
    await page.getByLabel('金額（円）').fill('1.5');
    await page.getByRole('button', { name: '変更を保存' }).click();
    await expectText(page.locator('#amount-error'), '半角数字の整数');
    if (!page.url().endsWith(`${expensePath}/edit`)) {
      throw new Error(`edit error returned to an unexpected URL: ${page.url()}`);
    }
    await screenshot(page, '04-edit-validation-error.png');

    await page.getByLabel('金額（円）').fill('1600');
    await Promise.all([
      page.waitForURL(new RegExp(`${expensePath}$`)),
      page.getByRole('button', { name: '変更を保存' }).click(),
    ]);
    await expectText(page.locator('#page-title'), title);
    await screenshot(page, '05-edited-detail.png');

    currentStep = 'employee-submit';
    await Promise.all([
      page.waitForURL(new RegExp(`${expensePath}$`)),
      page.getByRole('button', { name: '申請する' }).click(),
    ]);
    await expectText(page.locator('.large-status'), '申請中');
    await logout(page);

    currentStep = 'return';
    await login(page, 'demo.approver.sales.1');
    await page.getByRole('link', { name: '承認待ち' }).click();
    await page.getByRole('link', { name: title }).click();
    await expectText(page.locator('.large-status'), '申請中');
    const returnForm = page.locator(`form[action$="/return"]`);
    await returnForm.locator('textarea[name="comment"]').fill('ブラウザ確認の差戻し理由');
    await Promise.all([
      page.waitForURL(new RegExp(`${expensePath}$`)),
      returnForm.getByRole('button', { name: '差し戻す' }).click(),
    ]);
    await expectText(page.locator('.large-status'), '差戻し');
    await expectText(page.locator('body'), 'ブラウザ確認の差戻し理由');
    await screenshot(page, '06-returned-detail.png');
    await logout(page);

    currentStep = 'resubmit';
    await login(page, 'demo.employee');
    await page.goto(`${baseUrl}${expensePath}`);
    await page.getByRole('link', { name: '編集' }).click();
    await page.getByLabel('件名').fill(`${title}（修正）`);
    await page.getByLabel('用途').fill('ブラウザで差戻し修正した申請');
    await page.getByLabel('金額（円）').fill('1700');
    await Promise.all([
      page.waitForURL(new RegExp(`${expensePath}$`)),
      page.getByRole('button', { name: '変更を保存' }).click(),
    ]);
    await Promise.all([
      page.waitForURL(new RegExp(`${expensePath}$`)),
      page.getByRole('button', { name: '申請する' }).click(),
    ]);
    await expectText(page.locator('.large-status'), '申請中');
    await logout(page);

    currentStep = 'approve';
    await login(page, 'demo.approver.sales.2');
    await page.getByRole('link', { name: '承認待ち' }).click();
    await page.getByRole('link', { name: `${title}（修正）` }).click();
    const approveForm = page.locator(`form[action$="/approve"]`);
    await approveForm.locator('textarea[name="comment"]').fill('ブラウザ確認済み');
    await Promise.all([
      page.waitForURL(new RegExp(`${expensePath}$`)),
      approveForm.getByRole('button', { name: '承認する' }).click(),
    ]);
    await expectText(page.locator('.large-status'), '承認済み');
    await expectText(page.locator('body'), 'ブラウザ確認済み');
    await screenshot(page, '07-approved-detail.png');
    await logout(page);

    currentStep = 'protected-url-after-logout';
    await page.goto(`${baseUrl}${expensePath}`);
    if (!page.url().includes('/login')) {
      throw new Error(`protected URL was accessible after logout: ${page.url()}`);
    }
    await screenshot(page, '08-login-after-logout.png');
  } catch (error) {
    await page.screenshot({
      path: path.join(screenshotDirectory, 'failure.png'),
      fullPage: true,
    }).catch(() => {});
    throw new Error(`browser smoke failed at ${currentStep}: ${error.message}`, { cause: error });
  } finally {
    await browser.close();
  }
}

async function expectText(locator, text) {
  await locator.waitFor({ state: 'visible' });
  const content = await locator.textContent();
  if (!content || !content.includes(text)) {
    throw new Error(`expected text "${text}" but received "${content}"`);
  }
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
