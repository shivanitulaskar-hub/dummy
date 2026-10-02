import { api } from './client';
import type { LoginResponse, UserInfo } from './types';

export async function login(username: string, password: string): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/login', { username, password });
  return data;
}

export async function fetchMe(): Promise<UserInfo> {
  const { data } = await api.get<UserInfo>('/auth/me');
  return data;
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/change-password', { currentPassword, newPassword });
  return data;
}
