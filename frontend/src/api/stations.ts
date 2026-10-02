import { api } from './client';
import type { Station } from './types';

export async function fetchStations(includeInactive = false): Promise<Station[]> {
  const { data } = await api.get<Station[]>('/stations', { params: { includeInactive } });
  return data;
}
