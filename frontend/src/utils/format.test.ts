import { describe, expect, it } from 'vitest';
import { formatElapsed, formatINR, formatInvoiceAmount, formatMinutes } from './format';
import { randomId } from './id';

describe('format', () => {
  it('formats rupees in Indian style', () => {
    expect(formatINR(190)).toBe('₹190');
    expect(formatINR(1234567.5)).toBe('₹12,34,567.5');
    expect(formatINR(null)).toBe('–');
  });

  it('formats invoice amounts with paise and a leading sign', () => {
    expect(formatInvoiceAmount(43.9)).toBe('₹ 43.90');
    expect(formatInvoiceAmount(-0.1)).toBe('− ₹ 0.10');
    expect(formatInvoiceAmount(123456)).toBe('₹ 1,23,456.00');
  });

  it('formats minutes', () => {
    expect(formatMinutes(60)).toBe('1h');
    expect(formatMinutes(90)).toBe('1h 30m');
    expect(formatMinutes(45)).toBe('45m');
  });

  it('formats elapsed time', () => {
    expect(formatElapsed(5075)).toBe('01:24:35');
    expect(formatElapsed(-3)).toBe('00:00:00');
  });

  it('creates distinct ids', () => {
    expect(randomId()).not.toBe(randomId());
  });
});
