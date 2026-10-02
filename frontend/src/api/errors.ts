import type { ApiErrorBody } from './types';

interface ErrorLike {
  isAxiosError?: boolean;
  response?: { status?: number; data?: unknown };
  code?: string;
  message?: string;
}

function isApiErrorBody(data: unknown): data is ApiErrorBody {
  return typeof data === 'object' && data !== null && 'message' in data && 'status' in data;
}

/** Extracts the structured backend error, if the failure came from the API. */
export function getApiError(error: unknown): ApiErrorBody | null {
  const e = error as ErrorLike | null;
  if (e?.isAxiosError && isApiErrorBody(e.response?.data)) {
    return e.response.data;
  }
  return null;
}

/**
 * Converts any thrown value into a short message suitable for the counter operator.
 * Never exposes stack traces or technical details.
 */
export function toUserMessage(error: unknown): string {
  const apiError = getApiError(error);
  if (apiError) {
    const firstViolations = (apiError.validationErrors ?? [])
      .slice(0, 3)
      .map((v) => `${v.field}: ${v.message}`);
    return firstViolations.length > 0
      ? `${apiError.message} (${firstViolations.join('; ')})`
      : apiError.message;
  }
  const e = error as ErrorLike | null;
  if (e?.isAxiosError && !e.response) {
    return 'Cannot reach the server. Check the network connection and try again.';
  }
  return 'Something went wrong. Please try again.';
}
