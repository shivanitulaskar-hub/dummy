import { api } from './client';
import type { Game, GameRequest, Page, PaymentMode, Rental, RentalStatus, ReturnPreview } from './types';

export async function fetchGames(includeInactive = false): Promise<Game[]> {
  const { data } = await api.get<Game[]>('/games', { params: { includeInactive } });
  return data;
}

export async function createGame(request: GameRequest): Promise<Game> {
  const { data } = await api.post<Game>('/games', request);
  return data;
}

export async function updateGame(id: number, request: GameRequest): Promise<Game> {
  const { data } = await api.put<Game>(`/games/${id}`, request);
  return data;
}

export async function searchRentals(params: {
  status?: RentalStatus;
  q?: string;
  page?: number;
  size?: number;
}): Promise<Page<Rental>> {
  const { data } = await api.get<Page<Rental>>('/rentals', { params });
  return data;
}

export async function createRental(
  request: {
    gameId: number;
    customerName: string;
    customerMobile: string;
    rentalDays?: number;
    depositAmount?: number;
    paymentMode: PaymentMode;
    referenceNo?: string;
    notes?: string;
  },
  idempotencyKey: string,
): Promise<Rental> {
  const { data } = await api.post<Rental>('/rentals', request, { headers: { 'Idempotency-Key': idempotencyKey } });
  return data;
}

export async function fetchReturnPreview(id: number): Promise<ReturnPreview> {
  const { data } = await api.get<ReturnPreview>(`/rentals/${id}/return-preview`);
  return data;
}

export async function returnRental(id: number, request: { paymentMode?: PaymentMode; referenceNo?: string; notes?: string }): Promise<Rental> {
  const { data } = await api.post<Rental>(`/rentals/${id}/return`, request);
  return data;
}

export async function cancelRental(id: number, reason: string): Promise<Rental> {
  const { data } = await api.post<Rental>(`/rentals/${id}/cancel`, { reason });
  return data;
}
