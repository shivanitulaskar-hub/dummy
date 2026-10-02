import { api } from './client';
import type { BillDetail, BillStatus, BillSummary, Invoice, Page, PaymentMode } from './types';

export async function searchBills(params: {
  q?: string;
  from?: string;
  to?: string;
  status?: BillStatus;
  page?: number;
  size?: number;
}): Promise<Page<BillSummary>> {
  const { data } = await api.get<Page<BillSummary>>('/bills', { params });
  return data;
}

export async function fetchBill(id: number): Promise<BillDetail> {
  const { data } = await api.get<BillDetail>(`/bills/${id}`);
  return data;
}

export async function fetchInvoice(id: number): Promise<Invoice> {
  const { data } = await api.get<Invoice>(`/bills/${id}/invoice`);
  return data;
}

export async function createWalkInBill(
  request: {
    customerName?: string;
    customerMobile?: string;
    items: { foodItemId: number; quantity: number }[];
    discountId?: number | null;
    paymentMode: PaymentMode;
    referenceNo?: string;
  },
  idempotencyKey: string,
): Promise<BillDetail> {
  const { data } = await api.post<BillDetail>('/bills', request, { headers: { 'Idempotency-Key': idempotencyKey } });
  return data;
}

export async function cancelBill(id: number, reason: string): Promise<BillDetail> {
  const { data } = await api.post<BillDetail>(`/bills/${id}/cancel`, { reason });
  return data;
}
