import { http, HttpResponse } from 'msw';
import type { DocumentSummary, ExtractionStatus, ManagedUser } from '@/api/types';

const API = import.meta.env.VITE_API_URL ?? '/api/v1';

interface StoredUser extends ManagedUser {
  password: string;
  organizationId: string;
  organizationName: string;
}

function seedUsers(): StoredUser[] {
  return [
    {
      id: 'user-1',
      organizationId: 'org-1',
      organizationName: 'Test organization',
      email: 'admin@test.local',
      displayName: 'Test Admin',
      role: 'ADMIN',
      enabled: true,
      createdAt: '2026-01-01T00:00:00Z',
      password: 'TestPassword123!',
    },
  ];
}

let userStore: StoredUser[] = seedUsers();

function toManagedUser(user: StoredUser): ManagedUser {
  return {
    id: user.id,
    email: user.email,
    displayName: user.displayName,
    role: user.role,
    enabled: user.enabled,
    createdAt: user.createdAt,
  };
}

interface StoredExtraction {
  id: string;
  status: ExtractionStatus;
  fileName: string;
  mimeType: string;
  pageCount: number;
  fullText: string;
  pages: { number: number; text: string; method: string; confidence: number }[];
  durationMs: number | null;
  error: string | null;
  createdAt: string;
  languages: string;
  sizeBytes: number;
}

let extractionStore: Record<string, StoredExtraction> = {};

function toSummary(entry: StoredExtraction): DocumentSummary {
  return {
    id: entry.id,
    fileName: entry.fileName,
    mimeType: entry.mimeType,
    status: entry.status,
    pageCount: entry.pageCount,
    sizeBytes: entry.sizeBytes,
    languages: entry.languages,
    durationMs: entry.durationMs,
    error: entry.error,
    createdAt: entry.createdAt,
    completedAt: entry.status === 'COMPLETED' ? entry.createdAt : null,
  };
}

export const handlers = [
  http.post(`${API}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string };
    const found = userStore.find(
      (candidate) =>
        candidate.email.toLowerCase() === body.email?.toLowerCase() &&
        candidate.password === body.password &&
        candidate.enabled,
    );
    if (found) {
      return HttpResponse.json({
        accessToken: 'test-token',
        tokenType: 'Bearer',
        expiresInSeconds: 3600,
        user: {
          id: found.id,
          organizationId: found.organizationId,
          organizationName: found.organizationName,
          email: found.email,
          displayName: found.displayName,
          role: found.role,
        },
      });
    }
    return HttpResponse.json(
      { title: 'Unauthorized', detail: 'Email ou mot de passe incorrect.', status: 401 },
      { status: 401 },
    );
  }),

  http.post(`${API}/auth/password`, () => new HttpResponse(null, { status: 204 })),

  http.post(`${API}/auth/logout`, () => new HttpResponse(null, { status: 204 })),

  http.patch(`${API}/auth/me`, async ({ request }) => {
    const body = (await request.json()) as { displayName?: string; email?: string };
    const existing = userStore.find((candidate) => candidate.id === 'user-1');
    if (!existing) {
      return HttpResponse.json(
        { title: 'Not found', detail: 'Compte introuvable.', status: 404 },
        { status: 404 },
      );
    }
    const updated: StoredUser = {
      ...existing,
      displayName: body.displayName ?? existing.displayName,
      email: body.email ?? existing.email,
    };
    userStore = userStore.map((candidate) =>
      candidate.id === updated.id ? updated : candidate,
    );
    return HttpResponse.json({
      id: updated.id,
      organizationId: updated.organizationId,
      organizationName: updated.organizationName,
      email: updated.email,
      displayName: updated.displayName,
      role: updated.role,
    });
  }),

  http.post(`${API}/users/:id/password`, () => new HttpResponse(null, { status: 204 })),

  http.get(`${API}/users`, () => HttpResponse.json(userStore.map(toManagedUser))),

  http.post(`${API}/users`, async ({ request }) => {
    const body = (await request.json()) as {
      email?: string;
      displayName?: string;
      password?: string;
      role?: ManagedUser['role'];
    };
    if (userStore.some((candidate) => candidate.email.toLowerCase() === body.email?.toLowerCase())) {
      return HttpResponse.json(
        { title: 'Conflict', detail: 'Un compte existe déjà pour cet email.', status: 409 },
        { status: 409 },
      );
    }
    const created: StoredUser = {
      id: `user-${Date.now()}`,
      organizationId: 'org-1',
      organizationName: 'Test organization',
      email: body.email ?? '',
      displayName: body.displayName ?? '',
      role: body.role ?? 'USER',
      enabled: true,
      createdAt: new Date().toISOString(),
      password: body.password ?? '',
    };
    userStore = [...userStore, created];
    return HttpResponse.json(toManagedUser(created), { status: 201 });
  }),

  http.patch(`${API}/users/:id`, async ({ params, request }) => {
    const id = params.id as string;
    const body = (await request.json()) as {
      role?: ManagedUser['role'];
      enabled?: boolean;
    };
    const existing = userStore.find((candidate) => candidate.id === id);
    if (!existing) {
      return HttpResponse.json(
        { title: 'Not found', detail: 'Utilisateur introuvable.', status: 404 },
        { status: 404 },
      );
    }
    const updated: StoredUser = {
      ...existing,
      role: body.role ?? existing.role,
      enabled: body.enabled ?? existing.enabled,
    };
    userStore = userStore.map((candidate) => (candidate.id === id ? updated : candidate));
    return HttpResponse.json(toManagedUser(updated));
  }),

  http.get(`${API}/auth/me`, ({ request }) => {
    const authorization = request.headers.get('authorization');
    if (authorization !== 'Bearer test-token') {
      return HttpResponse.json(
        { title: 'Unauthorized', detail: 'Authentification requise.', status: 401 },
        { status: 401 },
      );
    }
    const current = userStore.find((candidate) => candidate.id === 'user-1') ?? userStore[0];
    return HttpResponse.json({
      id: current.id,
      organizationId: current.organizationId,
      organizationName: current.organizationName,
      email: current.email,
      displayName: current.displayName,
      role: current.role,
    });
  }),

  http.post(`${API}/extractions`, async ({ request }) => {
    // In the jsdom/node test environment, Axios sets Content-Type to
    // 'multipart/form-data' without a boundary, which causes undici to reject
    // formData() parsing. We read the body as text instead and derive a filename
    // from headers or fall back to a sensible default.
    let fileName = 'upload.pdf';
    try {
      const contentDisposition = request.headers.get('content-disposition');
      if (contentDisposition) {
        const match = /filename="?([^"\s]+)"?/.exec(contentDisposition);
        if (match?.[1]) fileName = match[1];
      }
    } catch {
      // ignore - use default filename
    }

    const id = `ext-${Date.now()}`;
    const entry: StoredExtraction = {
      id,
      status: 'PENDING',
      fileName,
      mimeType: 'application/pdf',
      pageCount: 1,
      fullText: '',
      pages: [],
      durationMs: null,
      error: null,
      createdAt: new Date().toISOString(),
      languages: 'fra',
      sizeBytes: 12,
    };
    extractionStore[id] = entry;
    return HttpResponse.json(entry, { status: 202 });
  }),

  http.get(`${API}/extractions/:id`, ({ params }) => {
    const id = params.id as string;
    const entry = extractionStore[id];
    if (!entry) {
      return HttpResponse.json(
        { title: 'Not found', detail: `Extraction ${id} not found`, status: 404 },
        { status: 404 },
      );
    }
    if (entry.status === 'PENDING' || entry.status === 'PROCESSING') {
      entry.status = 'COMPLETED';
      entry.fullText = 'Mock extracted text.';
      entry.pages = [{ number: 1, text: 'Mock extracted text.', method: 'NATIVE', confidence: 1 }];
      entry.durationMs = 800;
    }
    return HttpResponse.json(entry);
  }),

  http.get(`${API}/extractions/:id/file`, ({ params }) => {
    const id = params.id as string;
    const entry = extractionStore[id];
    if (!entry) {
      return HttpResponse.json(
        { title: 'Not found', detail: `Extraction ${id} not found`, status: 404 },
        { status: 404 },
      );
    }
    return HttpResponse.text('mock source file', {
      headers: { 'Content-Type': entry.mimeType },
    });
  }),

  http.delete(`${API}/extractions/:id`, ({ params }) => {
    const id = params.id as string;
    delete extractionStore[id];
    return new HttpResponse(null, { status: 204 });
  }),

  http.get(`${API}/documents`, ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? 20);
    const query = (url.searchParams.get('query') ?? '').trim().toLowerCase();
    const status = url.searchParams.get('status');
    const items = Object.values(extractionStore)
      .filter((entry) => {
        if (status && entry.status !== status) return false;
        if (!query) return true;
        return (
          entry.fileName.toLowerCase().includes(query) ||
          entry.fullText.toLowerCase().includes(query)
        );
      })
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(toSummary);
    return HttpResponse.json({
      items: items.slice(page * size, page * size + size),
      total: items.length,
      page,
      size,
    });
  }),

  http.delete(`${API}/documents`, () => {
    extractionStore = {};
    return new HttpResponse(null, { status: 204 });
  }),
];

export function resetStore() {
  extractionStore = {};
  userStore = seedUsers();
}

interface SeedOptions {
  fileName?: string;
  mimeType?: string;
}

export function seedExtraction(id: string, status: ExtractionStatus, options: SeedOptions = {}) {
  extractionStore[id] = {
    id,
    status,
    fileName: options.fileName ?? 'seed.pdf',
    mimeType: options.mimeType ?? 'application/pdf',
    pageCount: 1,
    fullText: 'seed text',
    pages: [{ number: 1, text: 'seed text', method: 'OCR', confidence: 0.95 }],
    durationMs: 100,
    error: null,
    createdAt: new Date().toISOString(),
    languages: 'fra',
    sizeBytes: 12,
  };
}

export function seedFailed(id: string, status: ExtractionStatus, error: string) {
  extractionStore[id] = {
    id,
    status,
    fileName: 'failed.pdf',
    mimeType: 'application/pdf',
    pageCount: 0,
    fullText: '',
    pages: [],
    durationMs: null,
    error,
    createdAt: new Date().toISOString(),
    languages: 'fra',
    sizeBytes: 12,
  };
}
