import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.tsx';
import './index.css';
import { getStoredTheme, applyTheme } from '@/utils/theme';

// Apply theme before React renders to prevent flash of wrong theme
applyTheme(getStoredTheme());

// Apply stored language direction (RTL for Arabic)
try {
  const lang = localStorage.getItem('docx.lang') ?? 'fr';
  document.documentElement.lang = lang;
  document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
} catch {
  // ignore storage errors in sandboxed contexts
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
