"use client";

/**
 * OtpCodeInput — shared per-digit verification-code entry.
 *
 * Extracted from /auth/mfa so the MFA flow and the R1 "Reachable" contact-OTP
 * registration flow (/auth/register/contact) share one accessible primitive:
 * N boxes (default 6), auto-advance on entry, backspace-to-previous when empty,
 * paste-split across boxes, numeric inputMode, and auto-fire of onComplete when
 * every box is filled.
 *
 * Controlled component: `value` is the concatenated code string; `onChange` emits
 * the new string on every edit; `onComplete` fires once the code reaches `length`.
 */

import { useEffect, useRef, type ClipboardEvent, type KeyboardEvent } from "react";

export interface OtpCodeInputProps {
  /** Number of digit boxes. */
  length?: number;
  /** Concatenated code string (may be shorter than `length` while typing). */
  value: string;
  /** Called with the new concatenated code on every change. */
  onChange: (value: string) => void;
  /** Called once with the full code when all boxes are filled. */
  onComplete?: (code: string) => void;
  /** Disable all inputs (e.g. while verifying). */
  disabled?: boolean;
  /** Focus the first box on mount. */
  autoFocus?: boolean;
}

export function OtpCodeInput({
  length = 6,
  value,
  onChange,
  onComplete,
  disabled = false,
  autoFocus = false,
}: OtpCodeInputProps) {
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  // Digits view derived from the controlled value (padded to `length`).
  const digits = Array.from({ length }, (_, i) => value[i] ?? "");

  useEffect(() => {
    if (autoFocus) {
      inputRefs.current[0]?.focus();
    }
  }, [autoFocus]);

  function emit(nextDigits: string[]) {
    const code = nextDigits.join("");
    onChange(code);
    if (code.length === length && nextDigits.every((d) => d !== "")) {
      onComplete?.(code);
    }
  }

  function handleChange(index: number, raw: string) {
    if (!/^\d*$/.test(raw)) return;

    const nextDigits = [...digits];
    nextDigits[index] = raw.slice(-1);

    // Auto-advance to the next box.
    if (raw && index < length - 1) {
      inputRefs.current[index + 1]?.focus();
    }

    emit(nextDigits);
  }

  function handleKeyDown(index: number, e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Backspace" && !digits[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  }

  function handlePaste(e: ClipboardEvent<HTMLInputElement>) {
    e.preventDefault();
    const pasted = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, length);
    if (!pasted) return;

    const nextDigits = [...digits];
    for (let i = 0; i < pasted.length; i++) {
      nextDigits[i] = pasted[i];
    }

    // Focus the first empty box, or the last box when full.
    const nextEmpty = nextDigits.findIndex((d) => d === "");
    inputRefs.current[nextEmpty >= 0 ? nextEmpty : length - 1]?.focus();

    emit(nextDigits);
  }

  return (
    <div className="flex justify-center gap-2">
      {digits.map((digit, index) => (
        <input
          key={index}
          ref={(el) => {
            inputRefs.current[index] = el;
          }}
          type="text"
          inputMode="numeric"
          maxLength={1}
          value={digit}
          onChange={(e) => handleChange(index, e.target.value)}
          onKeyDown={(e) => handleKeyDown(index, e)}
          onPaste={index === 0 ? handlePaste : undefined}
          disabled={disabled}
          className="w-12 h-14 text-center text-xl font-semibold border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400 disabled:opacity-50 disabled:cursor-not-allowed"
          aria-label={`Digit ${index + 1}`}
        />
      ))}
    </div>
  );
}
