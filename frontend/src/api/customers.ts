import { api } from './client';
import type { Customer, CustomerHistory, CustomerRequest, Page } from './types';

export async function searchCustomers(params: { q?: string; page?: number; size?: number }): Promise<Page<Customer>> {
  const { data } = await api.get<Page<Customer>>('/customers', { params });
  return data;
}

export async function fetchCustomerHistory(id: number): Promise<CustomerHistory> {
  const { data } = await api.get<CustomerHistory>(`/customers/${id}/history`);
  return data;
}

export async function createCustomer(request: CustomerRequest): Promise<Customer> {
  const { data } = await api.post<Customer>('/customers', request);
  return data;
}

export async function updateCustomer(id: number, request: CustomerRequest): Promise<Customer> {
  const { data } = await api.put<Customer>(`/customers/${id}`, request);
  return data;
}
