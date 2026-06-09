import { apiClient } from "@impilo/mobile-api-client";

export type RegistrationReadiness = {
  ready: boolean;
  code?: string;
  message?: string;
};

export type RegisterRequest = {
  fullName: string;
  email: string;
  password: string;
  healthId?: string;
};

export type RegisterSuccess = {
  kind: "session";
  accessToken: string;
  refreshToken: string | null;
  expiresIn: number;
  user: {
    id: string;
    email: string;
    displayName: string;
    roles: string[];
    actorType: string;
  };
};

export type RegisterPendingLogin = {
  kind: "login_required";
  message: string;
};

export type AssuranceTier = "BASIC" | "TEMPORARY" | "FULL";

const TIER_TO_LOA: Record<AssuranceTier, { targetLevel: string; method: string }> = {
  BASIC: { targetLevel: "LOA1", method: "SELF_ASSERTED" },
  TEMPORARY: { targetLevel: "LOA2", method: "SUPERVISED_REMOTE_PROOFING" },
  FULL: { targetLevel: "LOA3", method: "IN_PERSON_VERIFICATION" },
};

function unwrapAttributes<T>(payload: unknown): T | null {
  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;
  if ("data" in record) {
    const data = record.data;
    if (data && typeof data === "object") {
      const d = data as Record<string, unknown>;
      if (d.attributes && typeof d.attributes === "object") {
        return d.attributes as T;
      }
      return data as T;
    }
  }
  if ("attributes" in record && record.attributes && typeof record.attributes === "object") {
    return record.attributes as T;
  }
  return record as T;
}

export async function fetchRegistrationReadiness(): Promise<RegistrationReadiness> {
  const response = await apiClient.get("/internal/v1/auth/register/readiness");
  const attrs = unwrapAttributes<RegistrationReadiness>(response.data);
  return attrs ?? { ready: false, code: "UNKNOWN", message: "Readiness check failed" };
}

export async function registerCitizen(
  body: RegisterRequest,
): Promise<RegisterSuccess | RegisterPendingLogin> {
  const nameParts = body.fullName.trim().split(/\s+/);
  const firstName = nameParts[0] ?? body.fullName;
  const lastName = nameParts.length > 1 ? nameParts.slice(1).join(" ") : firstName;

  const response = await apiClient.post("/internal/v1/auth/register", {
    fullName: body.fullName.trim(),
    identifier: body.email.trim(),
    email: body.email.trim(),
    password: body.password,
    firstName,
    lastName,
    healthId: body.healthId?.trim() || undefined,
  });

  const attrs = unwrapAttributes<Record<string, unknown>>(response.data);
  if (!attrs) {
    throw new Error("Registration response was empty");
  }

  const token = typeof attrs.token === "string" ? attrs.token : null;
  const user = attrs.user as RegisterSuccess["user"] | undefined;
  if (token && user) {
    return {
      kind: "session",
      accessToken: token,
      refreshToken: typeof attrs.refreshToken === "string" ? attrs.refreshToken : null,
      expiresIn: typeof attrs.expiresIn === "number" ? attrs.expiresIn : 28_800,
      user,
    };
  }

  return {
    kind: "login_required",
    message: String(attrs.message ?? "Account created. Please sign in."),
  };
}

export async function requestAssuranceUpgrade(tier: AssuranceTier): Promise<void> {
  const mapping = TIER_TO_LOA[tier];
  await apiClient.post("/internal/v1/identity/assurance/upgrade/request", mapping);
}
