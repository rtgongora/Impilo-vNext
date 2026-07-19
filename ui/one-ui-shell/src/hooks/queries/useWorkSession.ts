/**
 * Provider work-session start / switch (D-P3 / PJ5).
 *
 * Two moves:
 *  - useWorkContext(): reads BFF GET /internal/v1/work-context — the actor's
 *    ACTIVE Vashandi assignments (facility/workspace/role), the picker source.
 *    Degrades honestly to an empty, unresolved context on failure.
 *  - useStartWorkSession(): POST /internal/v1/work-context/session — the BFF
 *    proves the chosen facility/workspace against those assignments, then mints
 *    a duty-scoped WORK_CONTEXT token. A switch passes the current session's
 *    `previousJti` so the old token is revoked before the new one is minted.
 *
 * Being a linked, operational provider is necessary but NOT sufficient: the
 * chosen workplace must be a live assignment, or the BFF returns a GENERIC
 * WORK_SESSION_UNAVAILABLE (never a list of which contexts do exist).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useWorkSessionStore, type WorkSession } from "@/hooks/useWorkSessionStore";

export interface WorkAssignment {
  facilityId: string;
  facilityName: string | null;
  workspaceId: string | null;
  workspaceName: string | null;
  departmentId: string | null;
  roleTemplateId: string | null;
  roleLabel: string | null;
}

export interface WorkContext {
  activeAssignments: WorkAssignment[];
  requiresContextChooser: boolean;
  resolved: boolean;
  integrationStatus: string;
}

interface WorkContextAttributes {
  activeAssignments?: Array<Record<string, unknown>> | null;
  requiresContextChooser?: boolean;
  resolved?: boolean;
  integrationStatus?: string;
}

interface WorkContextResource {
  id: string;
  type: "work-context";
  attributes: WorkContextAttributes;
}

const UNRESOLVED_CONTEXT: WorkContext = {
  activeAssignments: [],
  requiresContextChooser: false,
  resolved: false,
  integrationStatus: "UPSTREAM_UNAVAILABLE",
};

function str(v: unknown): string | null {
  if (v === null || v === undefined) return null;
  const s = String(v).trim();
  return s === "" ? null : s;
}

function toAssignment(raw: Record<string, unknown>): WorkAssignment {
  return {
    facilityId: str(raw.facilityId) ?? "",
    facilityName: str(raw.facilityName),
    workspaceId: str(raw.workspaceId),
    workspaceName: str(raw.workspaceName),
    departmentId: str(raw.departmentId),
    roleTemplateId: str(raw.roleTemplateId),
    roleLabel: str(raw.roleLabel) ?? str(raw.roleTemplateId),
  };
}

export function useWorkContext() {
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const query = useQuery<WorkContext>({
    queryKey: ["work-context", user?.id],
    queryFn: async () => {
      try {
        const res = await apiClient.get<ApiResponse<WorkContextResource>>(
          "/internal/v1/work-context",
        );
        const attrs = res.data.attributes;
        const assignments = Array.isArray(attrs.activeAssignments)
          ? attrs.activeAssignments.map(toAssignment).filter((a) => a.facilityId !== "")
          : [];
        return {
          activeAssignments: assignments,
          requiresContextChooser: attrs.requiresContextChooser ?? false,
          resolved: attrs.resolved ?? false,
          integrationStatus: attrs.integrationStatus ?? "LIVE",
        };
      } catch {
        return UNRESOLVED_CONTEXT;
      }
    },
    enabled: isAuthenticated && !!user,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });

  return {
    workContext: query.data ?? UNRESOLVED_CONTEXT,
    isLoading: query.isLoading,
    isError: query.isError,
    refetch: query.refetch,
  };
}

export interface StartWorkSessionInput {
  facilityId: string;
  workspaceId?: string | null;
}

interface WorkSessionAttributes {
  token?: string;
  jti?: string;
  expiresAt?: string | null;
  facilityId?: string;
  workspaceId?: string | null;
  roleTemplateId?: string | null;
}

interface WorkSessionResource {
  id: string;
  type: "work-session";
  attributes: WorkSessionAttributes;
}

/**
 * Start or switch the work session. Reads the current session (if any) to pass
 * `previousJti` so a switch revokes the old token first — the BFF enforces
 * single-context-per-session; this just carries the handle.
 */
export function useStartWorkSession() {
  const queryClient = useQueryClient();
  const setSession = useWorkSessionStore((s) => s.setSession);
  const current = useWorkSessionStore((s) => s.session);

  return useMutation<WorkSession, Error, StartWorkSessionInput>({
    mutationFn: async (input) => {
      const body: Record<string, unknown> = { facilityId: input.facilityId };
      if (input.workspaceId) body.workspaceId = input.workspaceId;
      if (current?.jti) body.previousJti = current.jti;

      const res = await apiClient.post<ApiResponse<WorkSessionResource>>(
        "/internal/v1/work-context/session",
        body,
      );
      const a = res.data.attributes;
      return {
        jti: a.jti ?? "",
        token: a.token ?? "",
        facilityId: a.facilityId ?? input.facilityId,
        workspaceId: a.workspaceId ?? input.workspaceId ?? null,
        roleTemplateId: a.roleTemplateId ?? null,
        expiresAt: a.expiresAt ?? null,
      };
    },
    onSuccess: (session) => {
      setSession(session);
      void queryClient.invalidateQueries({ queryKey: ["work-context"] });
    },
  });
}

interface WorkSessionApiError {
  status?: number;
  error?: { code?: string; message?: string };
}

/** Human-readable message for a start/switch failure, defaulting to the generic denial. */
export function workSessionErrorMessage(err: unknown, fallback: string): string {
  const e = err as WorkSessionApiError | undefined;
  return e?.error?.message || fallback;
}
