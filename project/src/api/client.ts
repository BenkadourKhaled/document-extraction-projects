import axios, { type AxiosInstance } from 'axios';
import { normalizeError } from './errors';
import { clearToken, getToken } from '@/utils/authStorage';

const baseURL = import.meta.env.VITE_API_URL ?? '/api/v1';

export const apiClient: AxiosInstance = axios.create({
  baseURL,
  // OCR and large PDFs can take longer than 30 s; align with nginx's 120 s.
  timeout: 120_000,
  headers: {
    Accept: 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (res) => res,
  (error: unknown) => {
    const status =
      typeof error === 'object' && error !== null && 'response' in error
        ? (error as { response?: { status?: number } }).response?.status
        : undefined;

    if (status === 401 && getToken()) {
      clearToken();
      window.dispatchEvent(new Event('auth:unauthorized'));
    }

    return Promise.reject(normalizeError(error));
  },
);

export { baseURL };
