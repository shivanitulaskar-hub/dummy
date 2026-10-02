import { api } from './client';
import type { ClosingSummary, DailyClosing } from './types';

export async function fetchClosingDay(date: string, openingCash?: number): Promise<DailyClosing> {
  const { data } = await api.get<DailyClosing>(`/daily-closing/${date}`, { params: { openingCash } });
  return data;
}

export async function fetchClosingToday(): Promise<DailyClosing> {
  const { data } = await api.get<DailyClosing>('/daily-closing/today');
  return data;
}

export async function fetchClosingHistory(from?: string, to?: string): Promise<ClosingSummary[]> {
  const { data } = await api.get<ClosingSummary[]>('/daily-closing', { params: { from, to } });
  return data;
}

export async function closeDay(date: string, request: { openingCash: number; actualCash: number; notes?: string }): Promise<DailyClosing> {
  const { data } = await api.post<DailyClosing>(`/daily-closing/${date}/close`, request);
  return data;
}

export async function reopenDay(date: string, reason: string): Promise<DailyClosing> {
  const { data } = await api.post<DailyClosing>(`/daily-closing/${date}/reopen`, { reason });
  return data;
}
