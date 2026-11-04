import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Header } from '@/features/extraction/components/Header';
import { ExtractionPage } from '@/features/extraction/pages/ExtractionPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { ProtectedRoute } from '@/features/auth/ProtectedRoute';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { UserManagementPage } from '@/features/users/UserManagementPage';
import { Toaster } from '@/components/ui/sonner';
import '@/i18n';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000,
      refetchOnWindowFocus: false,
      retry: false,
    },
  },
});

function AppShell() {
  return (
    <div className="flex h-dvh flex-col overflow-hidden bg-background">
      <Header />
      <main className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <Routes>
          <Route path="/" element={<ExtractionPage />} />
          <Route path="/extraction/:id" element={<ExtractionPage />} />
          <Route path="/admin/users" element={<UserManagementPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/*"
              element={
                <ProtectedRoute>
                  <AppShell />
                </ProtectedRoute>
              }
            />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
      <Toaster position="bottom-right" richColors />
    </QueryClientProvider>
  );
}
