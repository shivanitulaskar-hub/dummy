import { api } from './client';
import type { Station, StationRequest } from './types';

export async function fetchStations(includeInactive = false): Promise<Station[]> {
  const { data } = await api.get<Station[]>('/stations', { params: { includeInactive } });
  return data;
}

export async function createStation(request: StationRequest): Promise<Station> {
  const { data } = await api.post<Station>('/stations', request);
  return data;
}

export async function updateStation(id: number, request: StationRequest): Promise<Station> {
  const { data } = await api.put<Station>(`/stations/${id}`, request);
  return data;
}

export async function deleteStation(id: number): Promise<void> {
  await api.delete(`/stations/${id}`);
}
