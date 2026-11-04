export type ExtractionStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export type ExtractionMethod = 'NATIVE' | 'OCR';

export type OcrLanguage = 'fr' | 'en' | 'ar';

export type UserRole = 'ADMIN' | 'REVIEWER' | 'USER';

export interface User {
  id: string;
  organizationId: string;
  organizationName: string;
  email: string;
  displayName: string;
  role: UserRole;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

export interface UpdateProfileInput {
  displayName: string;
  email: string;
}

export interface ManagedUser {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
  enabled: boolean;
  createdAt: string;
}

export interface CreateUserInput {
  email: string;
  displayName: string;
  password: string;
  role: UserRole;
}

export interface UpdateUserInput {
  role?: UserRole;
  enabled?: boolean;
}

export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
}

export interface ExtractionPage {
  number: number;
  text: string;
  method: ExtractionMethod;
  confidence: number | null;
}

export interface Extraction {
  id: string;
  status: ExtractionStatus;
  fileName: string;
  mimeType: string;
  pageCount: number;
  fullText: string;
  pages: ExtractionPage[];
  durationMs: number | null;
  error: string | null;
  createdAt: string;
  languages: string;
}

export interface DocumentSummary {
  id: string;
  fileName: string;
  mimeType: string;
  status: ExtractionStatus;
  pageCount: number;
  sizeBytes: number;
  languages: string;
  durationMs: number | null;
  error: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface PageResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  type?: string;
  instance?: string;
}

export interface ApiError {
  status: number;
  detail: string;
  isNetwork: boolean;
  isPasswordProtected: boolean;
  isTooLarge: boolean;
  raw: ProblemDetail | null;
}

export interface HistoryEntry {
  id: string;
  fileName: string;
  date: string;
  status: ExtractionStatus;
}
