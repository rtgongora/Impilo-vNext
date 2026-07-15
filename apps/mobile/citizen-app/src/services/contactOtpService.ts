/**
 * Contact-first OTP onboarding (R1 "Reachable" rung) — the citizen mobile mirror of the
 * web gateway flow. Runs BEFORE any session exists, so every call uses the anonymous
 * {@link publicApiClient} (no bearer) against the gateway's permitAll auth lanes:
 *
 *   POST /internal/v1/auth/contact/otp/request  — issue an OTP to a phone/email
 *   POST /internal/v1/auth/contact/otp/verify   — purpose=REGISTER: create the CITIZEN
 *                                                  account and (on success) auto-login
 *
 * Contract owner: experience-bff AuthContactOtpController. OTP-login for EXISTING users
 * does not exist (see that controller) — this is registration only.
 */
import { publicApiClient } from "@impilo/mobile-api-client";

export type OtpChannel = "PHONE" | "EMAIL";

export interface OtpRequestResult {
  status: string;
  channel: OtpChannel;
  maskedValue: string;
  expiresInSeconds: number;
}

export interface OtpRegisterUser {
  id: string;
  healthId?: string;
  identifier?: string;
  displayName?: string;
  roles?: string[];
  actorType?: string;
  method?: string;
}

export type OtpRegisterResult =
  | {
      kind: "session";
      accessToken: string;
      refreshToken: string | null;
      expiresIn: number;
      user: OtpRegisterUser;
    }
  | { kind: "login_required"; message: string };

function attributesOf<T>(payload: unknown): T | null {
  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;
  const data = record.data;
  if (data && typeof data === "object") {
    const d = data as Record<string, unknown>;
    if (d.attributes && typeof d.attributes === "object") {
      return d.attributes as T;
    }
    return data as T;
  }
  return record as T;
}

/** Issue a contact-verification OTP to a phone or email. Uniform response (no enumeration). */
export async function requestContactOtp(channel: OtpChannel, value: string): Promise<OtpRequestResult> {
  const res = await publicApiClient.post("/internal/v1/auth/contact/otp/request", {
    channel,
    value: value.trim(),
  });
  const attrs = attributesOf<Partial<OtpRequestResult>>(res.data);
  return {
    status: String(attrs?.status ?? "SENT"),
    channel: (attrs?.channel as OtpChannel) ?? channel,
    maskedValue: String(attrs?.maskedValue ?? value.trim()),
    expiresInSeconds: typeof attrs?.expiresInSeconds === "number" ? attrs.expiresInSeconds : 300,
  };
}

export interface VerifyRegisterInput {
  value: string;
  code: string;
  password: string;
  fullName: string;
}

/**
 * Verify the OTP and complete registration (purpose=REGISTER). On success the BFF
 * auto-logs the new person in and returns an auth_token envelope; otherwise it returns
 * a REGISTERED status asking the person to sign in.
 */
export async function verifyContactRegister(input: VerifyRegisterInput): Promise<OtpRegisterResult> {
  const parts = input.fullName.trim().split(/\s+/);
  const firstName = parts[0] ?? input.fullName.trim();
  const lastName = parts.length > 1 ? parts.slice(1).join(" ") : firstName;

  const res = await publicApiClient.post("/internal/v1/auth/contact/otp/verify", {
    value: input.value.trim(),
    code: input.code.trim(),
    purpose: "REGISTER",
    password: input.password,
    fullName: input.fullName.trim(),
    firstName,
    lastName,
  });

  const attrs = attributesOf<Record<string, unknown>>(res.data);
  const token = typeof attrs?.token === "string" ? attrs.token : null;
  const user = attrs?.user as OtpRegisterUser | undefined;

  if (token && user) {
    return {
      kind: "session",
      accessToken: token,
      // The refresh token is delivered as an httpOnly cookie by the BFF, never in JSON.
      refreshToken: typeof attrs?.refreshToken === "string" ? (attrs.refreshToken as string) : null,
      expiresIn: typeof attrs?.expiresIn === "number" ? (attrs.expiresIn as number) : 28_800,
      user,
    };
  }

  return {
    kind: "login_required",
    message: String(attrs?.message ?? "Account created. Please sign in."),
  };
}
