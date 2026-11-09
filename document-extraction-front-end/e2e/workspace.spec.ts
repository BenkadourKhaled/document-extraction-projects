import { expect, test, type Page } from '@playwright/test';
import { PDFDocument, StandardFonts, rgb } from 'pdf-lib';

const adminEmail = process.env.E2E_ADMIN_EMAIL!;
const adminPassword = process.env.E2E_ADMIN_PASSWORD!;

async function login(page: Page) {
  await page.goto('/login');
  await page.locator('#email').fill(adminEmail);
  await page.locator('#password').fill(adminPassword);
  await page.getByRole('button', { name: /Se connecter|Sign in/ }).click();
  await expect(page).toHaveURL(/\/$/, { timeout: 20_000 });
  await expect(page.getByRole('button', { name: /Lancer l.extraction/ })).toBeVisible();
}

async function makePdf(fileName: string): Promise<Buffer> {
  const pdf = await PDFDocument.create();
  const page = pdf.addPage([595, 842]);
  const font = await pdf.embedFont(StandardFonts.Helvetica);
  const lines = [
    'Document de test E2E',
    fileName,
    'Facture numero 2026-001',
    'Client: Administration DocuScan',
    'Montant total: 12 500,00 DZD',
  ];
  lines.forEach((line, index) => {
    page.drawText(line, {
      x: 60,
      y: 780 - index * 32,
      size: 16,
      font,
      color: rgb(0.08, 0.1, 0.15),
    });
  });
  return Buffer.from(await pdf.save());
}

async function uploadPdf(page: Page, fileName: string) {
  await page.locator('input[type="file"]').setInputFiles({
    name: fileName,
    mimeType: 'application/pdf',
    buffer: await makePdf(fileName),
  });
  await expect(page.getByText(fileName, { exact: true })).toBeVisible();
  // Regression guard: selecting a file must enable the submit button.
  const start = page.getByRole('button', { name: /Lancer l.extraction/ });
  await expect(start).toBeEnabled();
  await start.click();
  await expect(page).toHaveURL(/\/extraction\/[0-9a-f-]+/);
  await expect(page.getByLabel('Texte éditable')).toHaveValue(/Document de test E2E/, {
    timeout: 90_000,
  });
}

test.describe.configure({ mode: 'serial' });

test('login, upload, poll and reopen an extraction from history', async ({ page }) => {
  await login(page);
  const fileName = `e2e-${Date.now()}.pdf`;
  await uploadPdf(page, fileName);

  await expect(page.getByRole('button', { name: /Nouvelle extraction/ })).toBeVisible();
  await expect(page.locator('canvas').first()).toBeVisible({ timeout: 30_000 });

  await page.getByRole('tab', { name: 'Champs' }).click();
  const fieldRow = page.getByTestId('field-reference_number');
  await expect(fieldRow).toBeVisible();
  await expect(fieldRow).toContainText('2026-001');

  await fieldRow.getByRole('button', { name: 'Page 1' }).click();
  await expect(page.getByTestId('field-highlight')).toBeVisible();

  await fieldRow.getByRole('button', { name: 'Corriger' }).click();
  await page.getByLabel('Valeur corrigée').fill('FACT-2026-777');
  await page.getByRole('button', { name: 'Enregistrer la correction' }).click();
  await expect(page.getByText('Correction enregistrée.')).toBeVisible();
  await expect(page.getByTestId('field-reference_number')).toContainText('FACT-2026-777');

  await page.goto('/');
  const search = page.getByPlaceholder(/Rechercher un document/);
  await search.fill(fileName);
  const historyEntry = page.getByRole('button', {
    name: new RegExp(`^Rouvrir ${fileName.replace('.', '\\.')}`),
  });
  await expect(historyEntry).toBeVisible();
  await historyEntry.click();
  await expect(page).toHaveURL(/\/extraction\/[0-9a-f-]+/);
  await expect(page.getByLabel('Texte éditable')).toHaveValue(/Facture numero 2026-001/);

  // Corrections are persisted server-side and survive a reload of the document.
  await page.getByRole('tab', { name: 'Champs' }).click();
  await expect(page.getByTestId('field-reference_number')).toContainText('FACT-2026-777');
});

test('administrator creates a user and resets their password', async ({ page }) => {
  await login(page);
  await page.goto('/admin/users');
  await expect(page.getByRole('heading', { name: 'Utilisateurs' })).toBeVisible();

  const email = `e2e-user-${Date.now()}@test.local`;
  const displayName = `E2E User ${Date.now()}`;
  await page.getByRole('button', { name: /Créer un utilisateur/ }).click();
  await page.locator('#user-display-name').fill(displayName);
  await page.locator('#user-email').fill(email);
  await page.locator('#user-password').fill('InitialPass123!');
  await page.getByRole('button', { name: /Créer le compte/ }).click();
  await expect(page.getByText('Utilisateur créé.')).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();

  const row = page.locator('tr', { hasText: email });
  await row.getByRole('button', { name: /Réinitialiser le mot de passe/ }).click();
  await page.locator('#reset-password').fill('ResetPass456!');
  await page.getByRole('button', { name: /Réinitialiser$/ }).click();
  await expect(
    page.getByText('Mot de passe réinitialisé. Les sessions de l’utilisateur ont été révoquées.'),
  ).toBeVisible();
});

test('profile update is persisted and logout revokes the local session', async ({ page }) => {
  await login(page);
  const suffix = Date.now();
  const displayName = `Admin E2E ${suffix}`;

  await page.getByRole('button', { name: 'Compte' }).click();
  await page.getByRole('menuitem', { name: 'Mon profil' }).click();
  await page.locator('#profile-display-name').fill(displayName);
  await page.getByRole('button', { name: /Enregistrer/ }).click();
  await expect(page.getByText('Profil mis à jour.')).toBeVisible();
  const accountButton = page.locator('button[aria-label="Compte"]');
  await expect(accountButton).toContainText(displayName);

  // The Radix dropdown stays open after selecting "Mon profil" because the
  // item prevents the default close. Use the menu item directly.
  await page.getByRole('menuitem', { name: 'Se déconnecter' }).click();
  await expect(page).toHaveURL(/\/login$/);

  await page.goto('/admin/users');
  await expect(page).toHaveURL(/\/login$/);
});
