import axios from 'axios';
import { tokenStore } from '../auth/tokenStore';
import { getApiError } from './errors';

export const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

type Handler = () => void;
let onUnauthorized: Handler = () => {};
let onPasswordChangeRequired: Handler = () => {};

/** Registered by AuthProvider so API failures can drive navigation. */
export function registerAuthHandlers(handlers: { unauthorized: Handler; passwordChangeRequired: Handler }) {
  onUnauthorized = handlers.unauthorized;
  onPasswordChangeRequired = handlers.passwordChangeRequired;
}

api.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    const isLogin = typeof error?.config?.url === 'string' && error.config.url.endsWith('/auth/login');
    if (status === 401 && !isLogin) {
      onUnauthorized();
    } else if (status === 403 && getApiError(error)?.code === 'PASSWORD_CHANGE_REQUIRED') {
      onPasswordChangeRequired();
    }
    return Promise.reject(error);
  },
);
