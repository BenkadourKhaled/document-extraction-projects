import { defineConfig, devices } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

function loadBackendEnv(): Record<string, string> {
  const envPath = path.resolve(process.cwd(), '..', 'document-extraction-api', '.env');
  if (!fs.existsSync(envPath)) return {};
  const result: Record<string, string> = {};
  for (const line of fs.readFileSync(envPath, 'utf8').split(/\r?\n/)) {
    const match = /^([A-Z0-9_]+)=(.*)$/.exec(line.trim());
    if (match) result[match[1]] = match[2];
  }
  return result;
}

const backendEnv = loadBackendEnv();

const adminEmail =
  process.env.E2E_ADMIN_EMAIL ?? backendEnv.BOOTSTRAP_ADMIN_EMAIL ?? process.env.BOOTSTRAP_ADMIN_EMAIL;
const adminPassword =
  process.env.E2E_ADMIN_PASSWORD ??
  backendEnv.BOOTSTRAP_ADMIN_PASSWORD ??
  process.env.BOOTSTRAP_ADMIN_PASSWORD;

if (!adminEmail || !adminPassword) {
  throw new Error(
    'E2E credentials are required: set E2E_ADMIN_EMAIL/E2E_ADMIN_PASSWORD or provide ' +
      '../document-extraction-api/.env with BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD.',
  );
}

process.env.E2E_ADMIN_EMAIL = adminEmail;
process.env.E2E_ADMIN_PASSWORD = adminPassword;

export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8081',
    locale: 'fr-FR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], locale: 'fr-FR' },
    },
  ],
});
