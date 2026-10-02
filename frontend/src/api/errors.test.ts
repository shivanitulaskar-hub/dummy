import { describe, expect, it } from 'vitest';
import { getApiError, toUserMessage } from './errors';

const apiError = (data: object) => ({ isAxiosError: true, response: { status: 400, data } });

describe('toUserMessage', () => {
  it('uses the backend message', () => {
    const err = apiError({ status: 409, message: 'PS5-02 already has an active session', code: 'STATION_OCCUPIED' });
    expect(toUserMessage(err)).toBe('PS5-02 already has an active session');
    expect(getApiError(err)?.code).toBe('STATION_OCCUPIED');
  });

  it('appends validation errors', () => {
    const err = apiError({
      status: 400,
      message: 'Please correct the highlighted fields',
      validationErrors: [{ field: 'playerCount', message: 'must be between 1 and 4' }],
    });
    expect(toUserMessage(err)).toBe('Please correct the highlighted fields (playerCount: must be between 1 and 4)');
  });

  it('explains network failures', () => {
    expect(toUserMessage({ isAxiosError: true, message: 'Network Error' })).toMatch(/Cannot reach the server/);
  });

  it('hides unexpected error details', () => {
    expect(toUserMessage(new TypeError('x is undefined'))).toBe('Something went wrong. Please try again.');
    expect(getApiError(new Error('boom'))).toBeNull();
  });
});
