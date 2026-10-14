import axios from 'axios';

export const TOKEN_KEY = 'hq_token';

/** Base URL is empty in dev/prod: Vite (dev) and nginx (prod) proxy /api to the backend. */
export const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export const api = axios.create({ baseURL: API_BASE, timeout: 15000 });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const isAuthCall = err.config?.url?.includes('/api/auth/login') || err.config?.url?.includes('/api/auth/register');
    if (err.response?.status === 401 && localStorage.getItem(TOKEN_KEY) && !isAuthCall) {
      // Token expired or invalid: drop it and let the app send the user to the login page.
      localStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event('hq:unauthorized'));
    }
    return Promise.reject(err);
  },
);

/** Turns an axios error into a message that is safe and useful to show. */
export function errorMessage(err, fallback = 'Something went wrong. Please try again.') {
  if (!err?.response) {
    return err?.code === 'ECONNABORTED' ? 'The server took too long to respond.' : 'Cannot reach the server. Check your connection.';
  }
  const data = err.response.data;
  if (data?.fieldErrors && Object.keys(data.fieldErrors).length) {
    return Object.entries(data.fieldErrors).map(([f, m]) => `${f}: ${m}`).join('; ');
  }
  return data?.message || fallback;
}
