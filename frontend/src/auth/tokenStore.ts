// The access token lives in sessionStorage: it survives a page refresh but not closing the browser tab,
// which suits a shared counter PC. See docs/ARCHITECTURE.md for the trade-off.
const KEY = 'console-adda.token';

export const tokenStore = {
  get(): string | null {
    try {
      return sessionStorage.getItem(KEY);
    } catch {
      return null;
    }
  },
  set(token: string): void {
    try {
      sessionStorage.setItem(KEY, token);
    } catch {
      // Storage unavailable (private mode): the user simply has to log in again after refresh.
    }
  },
  clear(): void {
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      // ignore
    }
  },
};
