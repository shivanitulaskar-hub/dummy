import { api } from './client';
import type { PriceQuote, PricingPlan, PricingPlanRequest } from './types';

export async function fetchPricingPlans(): Promise<PricingPlan[]> {
  const { data } = await api.get<PricingPlan[]>('/pricing');
  return data;
}

export async function fetchCurrentPlan(): Promise<PricingPlan> {
  const { data } = await api.get<PricingPlan>('/pricing/current');
  return data;
}

export async function createPricingPlan(request: PricingPlanRequest): Promise<PricingPlan> {
  const { data } = await api.post<PricingPlan>('/pricing', request);
  return data;
}

export async function updatePricingPlan(id: number, request: PricingPlanRequest): Promise<PricingPlan> {
  const { data } = await api.put<PricingPlan>(`/pricing/${id}`, request);
  return data;
}

export async function deletePricingPlan(id: number): Promise<void> {
  await api.delete(`/pricing/${id}`);
}

export async function fetchQuote(params: {
  planId?: number;
  minutes: number;
  players: number;
  creditMinutes?: number;
}): Promise<PriceQuote> {
  const { data } = await api.get<PriceQuote>('/pricing/quote', { params });
  return data;
}
