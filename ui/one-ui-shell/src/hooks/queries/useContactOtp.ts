/**
 * Experience UI — Contact-OTP Query Hooks (R1 "Reachable" rung).
 *
 * Wires the two public contact-verification endpoints on the Experience BFF
 * (AuthContactOtpController) for the phone/email-OTP registration flow:
 *   - POST /internal/v1/auth/contact/otp/request  → issue a verification code
 *   - POST /internal/v1/auth/contact/otp/verify    → verify + REGISTER (auto-login)
 *
 * Both are public (pre-auth) — called with no token, exactly like register /
 * forgot-password. OTP *login* for existing users does not exist; verify is
 * REGISTER-only from this surface.
 */

import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

// ── request ──────────────────────────────────────────────────────

export interface RequestContactOtpPayload {
  /** Optional explicit channel; the BFF infers PHONE/EMAIL from `value` when omitted. */
  channel?: "PHONE" | "EMAIL";
  value: string;
}

export interface ContactOtpRequestResource {
  type: "contact-otp";
  attributes: {
    status: "SENT";
    channel: "PHONE" | "EMAIL";
    maskedValue: string;
    expiresInSeconds: number;
  };
}

/** Issue a contact-verification OTP to a phone/email. */
export function useRequestContactOtp() {
  return useMutation<
    ApiResponse<ContactOtpRequestResource>,
    unknown,
    RequestContactOtpPayload
  >({
    mutationFn: (payload) =>
      apiClient.post<ApiResponse<ContactOtpRequestResource>>(
        "/internal/v1/auth/contact/otp/request",
        payload,
      ),
  });
}

// ── verify (REGISTER) ────────────────────────────────────────────

export interface VerifyContactOtpPayload {
  value: string;
  code: string;
  purpose: "REGISTER";
  firstName?: string;
  lastName?: string;
  fullName?: string;
}

/** Registration outcome when auto-login could not mint a token (HTTP 201). */
export interface ContactRegistrationResource {
  type: "registration";
  attributes: {
    status: "REGISTERED";
    channel: "PHONE" | "EMAIL";
    maskedValue: string;
    message?: string;
  };
}

/**
 * Verify a contact OTP and REGISTER. On success the BFF returns either the same
 * `auth_token` envelope as login (HTTP 200, auto-login) or a `registration`
 * envelope (HTTP 201) when a token could not be minted. The caller distinguishes
 * by `data.type`.
 */
export function useVerifyContactOtp() {
  return useMutation<
    ApiResponse<ContactRegistrationResource>,
    unknown,
    VerifyContactOtpPayload
  >({
    mutationFn: (payload) =>
      apiClient.post<ApiResponse<ContactRegistrationResource>>(
        "/internal/v1/auth/contact/otp/verify",
        payload,
      ),
  });
}
