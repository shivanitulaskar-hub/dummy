// Display helpers only. All money is calculated by the backend; the UI never does arithmetic on amounts.

const inr = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

export function formatINR(amount: number | null | undefined): string {
  return amount == null ? '–' : inr.format(amount);
}

const inrInvoice = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Invoice style: always two decimals, sign in front of the symbol ("₹ 43.90", "− ₹ 0.10"). */
export function formatInvoiceAmount(amount: number): string {
  const text = `₹ ${inrInvoice.format(Math.abs(amount))}`;
  return amount < 0 ? `− ${text}` : text;
}

/** 90 → "1h 30m", 60 → "1h", 45 → "45m" */
export function formatMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m}m`;
  return m === 0 ? `${h}h` : `${h}h ${m}m`;
}

/** Seconds → "01:24:35" */
export function formatElapsed(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const hh = String(Math.floor(s / 3600)).padStart(2, '0');
  const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
  const ss = String(s % 60).padStart(2, '0');
  return `${hh}:${mm}:${ss}`;
}

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '–';
  return new Date(iso).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '–';
  return new Date(iso).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });
}
