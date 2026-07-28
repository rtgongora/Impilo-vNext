/**
 * Work Context service (Phase G1) — mints the same duty-scoped work-context
 * token web's `useSwitchWorkContext` mints, via experience-bff's
 * WorkContextController. Every call rides the standard trust headers already
 * injected by @impilo/mobile-api-client; the BFF re-proves `contextId` from
 * source (VASHANDI/WGV/ORG_REGISTRY/TUSO/SUPPORT) rather than trusting the
 * client-submitted value — see WorkContextResolutionService#proveContext.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { ResolvedWorkContextsAttributes, WorkModeSessionAttributes } from "@impilo/mobile-trust";

interface JsonApiEnvelope<TAttributes> {
  data: { id: string; type: string; attributes: TAttributes };
  meta?: { request_id?: string; correlation_id?: string };
}

/** `GET /internal/v1/work-context/resolved` — every work context this identity can enter. */
export async function listResolvedWorkContexts(): Promise<ResolvedWorkContextsAttributes> {
  const res = await apiClient.get<JsonApiEnvelope<ResolvedWorkContextsAttributes>>(
    "/internal/v1/work-context/resolved"
  );
  return res.data.data.attributes;
}

/**
 * `POST /internal/v1/work-context/session/mode` — mints a fresh duty token for
 * `contextId`+`workMode`. Pass `previousJti` when replacing an existing minted
 * session so the BFF can revoke it. Never call with a client-invented
 * `contextId` — only ids returned by `listResolvedWorkContexts()`.
 */
export async function mintWorkContextSession(
  contextId: string,
  workMode: string,
  previousJti?: string
): Promise<WorkModeSessionAttributes> {
  const body: Record<string, unknown> = { contextId, workMode };
  if (previousJti) body.previousJti = previousJti;

  const res = await apiClient.post<JsonApiEnvelope<WorkModeSessionAttributes>>(
    "/internal/v1/work-context/session/mode",
    body
  );
  return res.data.data.attributes;
}
