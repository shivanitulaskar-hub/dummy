import { api } from './client';
import type { GamingSession, PaymentMode, StartSessionRequest } from './types';

export async function fetchActiveSessions(): Promise<GamingSession[]> {
  const { data } = await api.get<GamingSession[]>('/sessions/active');
  return data;
}

export async function startSession(request: StartSessionRequest): Promise<GamingSession> {
  const { data } = await api.post<GamingSession>('/sessions', request);
  return data;
}

export async function updateSession(
  id: number,
  request: { playerCount?: number; customerName?: string; customerMobile?: string; notes?: string },
): Promise<GamingSession> {
  const { data } = await api.patch<GamingSession>(`/sessions/${id}`, request);
  return data;
}

/** END: calculates the bill as of now; nothing is saved. */
export async function previewEnd(id: number): Promise<GamingSession> {
  const { data } = await api.post<GamingSession>(`/sessions/${id}/end`);
  return data;
}

export async function completeSession(
  id: number,
  request: { endedAt: string; paymentMode: PaymentMode; referenceNo?: string },
  idempotencyKey: string,
): Promise<GamingSession> {
  const { data } = await api.post<GamingSession>(`/sessions/${id}/complete`, request, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
  return data;
}

export async function cancelSession(id: number, reason: string): Promise<GamingSession> {
  const { data } = await api.post<GamingSession>(`/sessions/${id}/cancel`, { reason });
  return data;
}
