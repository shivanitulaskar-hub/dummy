import { api } from './client';
import type { Dashboard, ReportInfo, ReportTable } from './types';

export async function fetchDashboard(): Promise<Dashboard> {
  const { data } = await api.get<Dashboard>('/dashboard');
  return data;
}

export async function fetchReportList(): Promise<ReportInfo[]> {
  const { data } = await api.get<ReportInfo[]>('/reports');
  return data;
}

export async function fetchReport(type: string, from: string, to: string): Promise<ReportTable> {
  const { data } = await api.get<ReportTable>(`/reports/${type}`, { params: { from, to } });
  return data;
}

/** Downloads the export through the authenticated API client and hands it to the browser as a file. */
export async function downloadReport(type: string, format: 'csv' | 'xlsx', from: string, to: string): Promise<void> {
  const response = await api.get<Blob>(`/reports/${type}/export`, { params: { format, from, to }, responseType: 'blob', timeout: 60000 });
  const disposition = String(response.headers['content-disposition'] ?? '');
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const url = URL.createObjectURL(response.data);
  const a = document.createElement('a');
  a.href = url;
  a.download = match?.[1] ?? `console-adda-${type}.${format}`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
