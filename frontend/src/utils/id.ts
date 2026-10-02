/**
 * Random id for Idempotency-Key headers. crypto.randomUUID only exists on HTTPS/localhost, but counter
 * tablets reach the app over plain HTTP on the cafe Wi-Fi, so fall back to getRandomValues (always available).
 */
export function randomId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}
