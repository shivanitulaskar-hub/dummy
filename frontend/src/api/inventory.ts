import { api } from './client';
import type { InventoryTxn, InventoryTxnType, StockRow } from './types';

export async function fetchStock(from?: string, to?: string): Promise<StockRow[]> {
  const { data } = await api.get<StockRow[]>('/inventory/stock', { params: { from, to } });
  return data;
}

export async function fetchInventoryTxns(params: { foodItemId?: number; from?: string; to?: string }): Promise<InventoryTxn[]> {
  const { data } = await api.get<InventoryTxn[]>('/inventory/transactions', { params });
  return data;
}

export async function recordInventoryTxn(request: {
  foodItemId: number;
  type: InventoryTxnType;
  quantity: number;
  unitCost?: number | null;
  notes?: string;
}): Promise<InventoryTxn> {
  const { data } = await api.post<InventoryTxn>('/inventory/transactions', request);
  return data;
}
