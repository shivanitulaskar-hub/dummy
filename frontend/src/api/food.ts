import { api } from './client';
import type { FoodCategory, FoodItem, FoodItemRequest } from './types';

export async function fetchFoodItems(includeInactive = false): Promise<FoodItem[]> {
  const { data } = await api.get<FoodItem[]>('/food-items', { params: { includeInactive } });
  return data;
}

export async function createFoodItem(request: FoodItemRequest): Promise<FoodItem> {
  const { data } = await api.post<FoodItem>('/food-items', request);
  return data;
}

export async function updateFoodItem(id: number, request: FoodItemRequest): Promise<FoodItem> {
  const { data } = await api.put<FoodItem>(`/food-items/${id}`, request);
  return data;
}

export async function fetchFoodCategories(): Promise<FoodCategory[]> {
  const { data } = await api.get<FoodCategory[]>('/food-categories');
  return data;
}

export async function createFoodCategory(name: string, displayOrder: number): Promise<FoodCategory> {
  const { data } = await api.post<FoodCategory>('/food-categories', { name, displayOrder, active: true });
  return data;
}
