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

/** END: calculates the bill without saving. Pass the frozen end time to keep re-previews consistent. */
export async function previewEnd(id: number, discountId?: number | null, endedAt?: string | null): Promise<GamingSession> {
  const { data } = await api.post<GamingSession>(`/sessions/${id}/end`, {
    discountId: discountId ?? null,
    endedAt: endedAt ?? null,
  });
  return data;
}

export async function addSessionItem(id: number, foodItemId: number, quantity = 1): Promise<GamingSession> {
  const { data } = await api.post<GamingSession>(`/sessions/${id}/items`, { foodItemId, quantity });
  return data;
}

export async function changeSessionItem(id: number, lineId: number, quantity: number): Promise<GamingSession> {
  const { data } = await api.patch<GamingSession>(`/sessions/${id}/items/${lineId}`, { quantity });
  return data;
}

export async function removeSessionItem(id: number, lineId: number): Promise<GamingSession> {
  const { data } = await api.delete<GamingSession>(`/sessions/${id}/items/${lineId}`);
  return data;
}

export async function completeSession(
  id: number,
  request: { endedAt: string; paymentMode: PaymentMode; referenceNo?: string; discountId?: number | null },
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
