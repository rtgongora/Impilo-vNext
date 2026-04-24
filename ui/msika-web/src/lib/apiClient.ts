import { useSessionStore } from "@/stores/sessionStore";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:10000";

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const url = `${API_BASE_URL}${path}`;
  const correlationId = crypto.randomUUID();
  const state = useSessionStore.getState();

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Correlation-Id": correlationId,
    "X-Purpose-Of-Use": state.purposeOfUse || "ADMIN",
    "X-Device-Fingerprint": "msika-admin-web",
  };

  if (state.accessToken) headers["Authorization"] = `Bearer ${state.accessToken}`;
  if (state.tenantId) headers["X-Tenant-Id"] = state.tenantId;
  if (state.actorId) headers["X-Actor-Id"] = state.actorId;
  if (state.actorType) headers["X-Actor-Type"] = state.actorType;
  if (state.facilityId) headers["X-Facility-Id"] = state.facilityId;
  if (state.workspaceId) headers["X-Workspace-Id"] = state.workspaceId;

  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  if (res.status === 304) return null as T;

  const envelope = await res.json();

  if (!envelope.success) {
    const err = envelope.error;
    if (err?.code === "STEP_UP_REQUIRED") {
      throw new StepUpRequiredError(err.message);
    }
    throw new Error(err?.message || "Request failed");
  }

  return envelope.data as T;
}

export class StepUpRequiredError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "StepUpRequiredError";
  }
}

/** List payloads returned in `data` from paginated Msika Admin APIs */
export type PagedList<T> = { items?: T[] };

export const apiClient = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  del: <T>(path: string) => request<T>("DELETE", path),
  uploadCsv: async <T>(path: string, file: File, params: Record<string, string>): Promise<T> => {
    const url = new URL(`${API_BASE_URL}${path}`);
    Object.entries(params).forEach(([k, v]) => url.searchParams.set(k, v));

    const correlationId = crypto.randomUUID();
    const state = useSessionStore.getState();
    const formData = new FormData();
    formData.append("file", file);

    const headers: Record<string, string> = {
      "X-Correlation-Id": correlationId,
      "X-Purpose-Of-Use": state.purposeOfUse || "ADMIN",
      "X-Device-Fingerprint": "msika-admin-web",
    };
    if (state.accessToken) headers["Authorization"] = `Bearer ${state.accessToken}`;
    if (state.tenantId) headers["X-Tenant-Id"] = state.tenantId;
    if (state.actorId) headers["X-Actor-Id"] = state.actorId;
    if (state.actorType) headers["X-Actor-Type"] = state.actorType;

    const res = await fetch(url.toString(), { method: "POST", headers, body: formData });
    const envelope = await res.json();
    if (!envelope.success) throw new Error(envelope.error?.message || "Upload failed");
    return envelope.data as T;
  },
};
