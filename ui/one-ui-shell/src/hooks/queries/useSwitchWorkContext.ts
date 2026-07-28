/**
 * switchWorkContext (Phase F8/F9) — the mode-aware companion to useStartWorkSession's
 * facility-only mint. Mints against POST /internal/v1/work-context/session/mode, which takes
 * a resolved `contextId` + `workMode` (any family: facility, organisation, jurisdiction,
 * programme) and re-proves it from source before issuing a token — never trusts the client
 * value as a bearer credential (same posture as the C3 session mint this reuses).
 *
 * Sequence, in order: cancel in-flight queries → mint (with previousJti so the old token is
 * revoked before the new one is issued) → set the new session + sweep/repopulate the
 * context-scoped sessionStorage keys api-client.ts reads for outbound headers →
 * `queryClient.clear()` → navigate.
 *
 * `queryClient.clear()`, not `invalidateQueries`: invalidate keeps the previous facility's
 * cached data on screen while refetching in the background, so a switch briefly (or, on a
 * slow network, not-so-briefly) renders the OLD context's content under the NEW context's
 * chrome. A full clear means every consumer starts from a genuine loading state.
 */

import { useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useWorkSessionStore } from "@/hooks/useWorkSessionStore";
import type { ResolvedWorkContextView } from "@/lib/trust";

interface WorkModeSessionAttributes {
  token?: string;
  jti?: string;
  expiresAt?: string | null;
  contextId?: string;
  workMode?: string;
}

interface WorkModeSessionResource {
  id: string;
  type: "work-mode-session";
  attributes: WorkModeSessionAttributes;
}

type WorkModeSessionResponse = ApiResponse<WorkModeSessionResource>;

/**
 * Context-scoped sessionStorage keys api-client.ts reads for outbound trust headers. Swept on
 * every switch so a stale value from the PREVIOUS context can never leak into a request made
 * under the NEW one — department_id/ward_id/programme_id in particular had no writer anywhere
 * in the codebase before this, so nothing previously guaranteed they were ever cleared.
 */
const CONTEXT_SCOPED_STORAGE_KEYS = [
  "exp:facility",
  "exp:workspace",
  "exp:shift",
  "exp:department_id",
  "exp:ward_id",
  "exp:programme_id",
  "exp:work_mode",
] as const;

function sweepAndRepopulateContextStorage(context: ResolvedWorkContextView, workMode: string) {
  if (typeof window === "undefined") return;
  for (const key of CONTEXT_SCOPED_STORAGE_KEYS) {
    sessionStorage.removeItem(key);
  }
  if (context.facilityId) {
    sessionStorage.setItem("exp:facility", JSON.stringify({ id: context.facilityId }));
  }
  if (context.departmentId) {
    sessionStorage.setItem("exp:department_id", context.departmentId);
  }
  if (context.programmeId) {
    sessionStorage.setItem("exp:programme_id", context.programmeId);
  }
  sessionStorage.setItem("exp:work_mode", workMode);
}

export function useSwitchWorkContext() {
  const queryClient = useQueryClient();
  const setSession = useWorkSessionStore((s) => s.setSession);
  const currentJti = useWorkSessionStore((s) => s.session?.jti);

  /** @param targetHref where to land after the switch — defaults to the Work Home page. */
  return async function switchWorkContext(
    context: ResolvedWorkContextView,
    workMode: string,
    targetHref: string = "/work",
  ): Promise<void> {
    await queryClient.cancelQueries();

    const body: Record<string, unknown> = { contextId: context.contextId, workMode };
    if (currentJti) body.previousJti = currentJti;

    const res = await apiClient.post<WorkModeSessionResponse>("/internal/v1/work-context/session/mode", body);
    const a = res.data.attributes;

    setSession({
      jti: a.jti ?? "",
      token: a.token ?? "",
      facilityId: context.facilityId ?? "",
      workspaceId: null,
      roleTemplateId: context.roleTemplateId ?? null,
      expiresAt: a.expiresAt ?? null,
      contextId: a.contextId ?? context.contextId,
      workMode: a.workMode ?? workMode,
    });

    sweepAndRepopulateContextStorage(context, workMode);

    queryClient.clear();

    const href = `${targetHref}${targetHref.includes("?") ? "&" : "?"}context_id=${encodeURIComponent(context.contextId)}`;
    window.location.assign(href);
  };
}
