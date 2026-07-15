/**
 * Pure (RN-runtime-free) helpers for the OTP code input — unit-testable in isolation.
 * The visual component (OtpCodeInput) is a thin shell over these rules.
 */

/** Default number of digits in a one-time verification code. */
export const DEFAULT_OTP_LENGTH = 6;

/**
 * Normalizes raw keyboard/paste input into a clean OTP string:
 * digits only, clamped to `length`. A pasted "123 456" becomes "123456".
 */
export function sanitizeOtp(raw: string, length: number = DEFAULT_OTP_LENGTH): string {
  return (raw ?? "").replace(/\D/g, "").slice(0, Math.max(0, length));
}

/** True once the code has exactly `length` digits. */
export function isOtpComplete(value: string, length: number = DEFAULT_OTP_LENGTH): boolean {
  return sanitizeOtp(value, length).length === length;
}

/**
 * Splits a value into per-cell characters for rendering `length` boxes.
 * Empty cells are the empty string.
 */
export function otpCells(value: string, length: number = DEFAULT_OTP_LENGTH): string[] {
  const clean = sanitizeOtp(value, length);
  return Array.from({ length }, (_, i) => clean[i] ?? "");
}
