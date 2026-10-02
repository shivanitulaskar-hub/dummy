import { api } from './client';
import type { Discount, DiscountRequest, TaxCategory, TaxSetting } from './types';

export async function fetchDiscounts(): Promise<Discount[]> {
  const { data } = await api.get<Discount[]>('/discounts');
  return data;
}

export async function fetchUsableDiscounts(): Promise<Discount[]> {
  const { data } = await api.get<Discount[]>('/discounts/usable');
  return data;
}

export async function createDiscount(request: DiscountRequest): Promise<Discount> {
  const { data } = await api.post<Discount>('/discounts', request);
  return data;
}

export async function updateDiscount(id: number, request: DiscountRequest): Promise<Discount> {
  const { data } = await api.put<Discount>(`/discounts/${id}`, request);
  return data;
}

export async function fetchTaxSettings(): Promise<TaxSetting[]> {
  const { data } = await api.get<TaxSetting[]>('/settings/tax');
  return data;
}

export async function updateTaxSetting(category: TaxCategory, request: Omit<TaxSetting, 'category'>): Promise<TaxSetting> {
  const { data } = await api.put<TaxSetting>(`/settings/tax/${category}`, request);
  return data;
}
