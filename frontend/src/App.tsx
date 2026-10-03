import React, { useState } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AppShell } from './components/layout';
import { LandingPage } from './pages/LandingPage';
import { HomePage } from './pages/HomePage';
import { ExpensesPage } from './pages/ExpensesPage';
import { ExpenseDetailPage } from './pages/ExpenseDetailPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { ProfilePage } from './pages/ProfilePage';
import { AdminPage } from './pages/AdminPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';
import { OAuth2CallbackPage } from './pages/OAuth2CallbackPage';
import {
  UnauthorizedPage,
  ForbiddenPage,
  NotFoundPage,
} from './pages/ErrorPages';
import { ProtectedRoute } from './components/ProtectedRoute';
import { ToastProvider, useToast } from './components/ui/Toast';
import { ExpenseModal } from './components/ExpenseModal';
import { ExpenseRequest } from './types';
import { useCreateExpense } from './hooks/useExpenses';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

const AppContent: React.FC = () => {
  const [isQuickAddOpen, setIsQuickAddOpen] = useState(false);
  const { showToast } = useToast();
  const createExpenseMutation = useCreateExpense();

  const handleQuickAddExpense = async (data: ExpenseRequest) => {
    try {
      await createExpenseMutation.mutateAsync(data);
      showToast('success', 'Expense created', `Added ${data.description} (₹${data.amount})`);
      setIsQuickAddOpen(false);
    } catch {
      showToast('error', 'Failed to create expense', 'Please check your connection and try again.');
    }
  };

  return (
    <BrowserRouter>
      <Routes>
        {/* Public Marketing & Auth Routes */}
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/oauth2/callback" element={<OAuth2CallbackPage />} />

        {/* Status / Error Pages */}
        <Route path="/401" element={<UnauthorizedPage />} />
        <Route path="/403" element={<ForbiddenPage />} />

        {/* Authenticated App Shell Routes */}
        <Route
          element={
            <ProtectedRoute>
              <AppShell onAddExpense={() => setIsQuickAddOpen(true)} />
            </ProtectedRoute>
          }
        >
          <Route path="/dashboard" element={<HomePage />} />
          <Route path="/expenses" element={<ExpensesPage />} />
          <Route path="/expenses/:id" element={<ExpenseDetailPage />} />
          <Route path="/analytics" element={<AnalyticsPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/admin" element={<AdminPage />} />
        </Route>

        {/* 404 Wildcard Fallback */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>

      {/* Global Quick Add Modal */}
      <ExpenseModal
        isOpen={isQuickAddOpen}
        onClose={() => setIsQuickAddOpen(false)}
        onSubmit={handleQuickAddExpense}
        isLoading={createExpenseMutation.isPending}
      />
    </BrowserRouter>
  );
};

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AppContent />
      </ToastProvider>
    </QueryClientProvider>
  );
};

export default App;
