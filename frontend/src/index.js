import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import 'bootstrap/dist/css/bootstrap.min.css';
import './styles/theme.css';
import './styles/tables.css';
import './styles/designer.css';
import './styles/expenses.css';
import './styles/responsive.css';
import App from './App';
import { applyTheme, readTheme } from './hooks/useTheme';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';

applyTheme(readTheme());

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </React.StrictMode>
);

if ('serviceWorker' in navigator && process.env.NODE_ENV === 'production') {
  window.addEventListener('load', () => navigator.serviceWorker.register('/service-worker.js').catch(() => {}));
}
