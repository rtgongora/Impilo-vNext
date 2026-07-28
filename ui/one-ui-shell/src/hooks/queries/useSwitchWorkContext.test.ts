import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { afterAll, beforeEach, describe, expect, it, vi } from "vitest";
import { useSwitchWorkContext } from "./useSwitchWorkContext";
import type { ResolvedWorkContextView } from "@/lib/trust";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({
  apiClient: { post },
}));

const { sessionState, setSessionMock } = vi.hoisted(() => ({
  sessionState: { session: null as { jti: string } | null },
  setSessionMock: vi.fn(),
}));
vi.mock("@/hooks/useWorkSessionStore", () => ({
  useWorkSessionStore: (selector: (s: typeof sessionState & { setSession: typeof setSessionMock }) => unknown) =>
    selector({ ...sessionState, setSession: setSessionMock }),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

const CONTEXT: ResolvedWorkContextView = {
  contextId: "ctx-9",
  contextKind: "facility",
  sourceSystem: "VASHANDI",
  facilityId: "facility-uuid-1",
  availableModes: ["CLINICAL_CARE"],
  defaultMode: "CLINICAL_CARE",
  restrictions: [],
  label: "Test facility",
  groupHint: "today",
};

describe("useSwitchWorkContext", () => {
  const originalAssign = window.location.assign;

  beforeEach(() => {
    post.mockReset();
    setSessionMock.mockReset();
    sessionState.session = null;
    sessionStorage.clear();
    Object.defineProperty(window, "location", {
      writable: true,
      value: { ...window.location, assign: vi.fn() },
    });
  });

  afterAll(() => {
    window.location.assign = originalAssign;
  });

  it("mints against /internal/v1/work-context/session/mode with contextId and workMode", async () => {
    post.mockResolvedValue({
      data: { id: "actor-1", type: "work-mode-session", attributes: { token: "tok", jti: "jti-2", contextId: "ctx-9", workMode: "CLINICAL_CARE" } },
    });
    const { result } = renderHook(() => useSwitchWorkContext(), { wrapper });

    await result.current(CONTEXT, "CLINICAL_CARE");

    expect(post).toHaveBeenCalledWith("/internal/v1/work-context/session/mode", { contextId: "ctx-9", workMode: "CLINICAL_CARE" });
  });

  it("passes previousJti when a session is already active, so the old token is revoked", async () => {
    sessionState.session = { jti: "old-jti" };
    post.mockResolvedValue({
      data: { id: "actor-1", type: "work-mode-session", attributes: { token: "tok", jti: "jti-2" } },
    });
    const { result } = renderHook(() => useSwitchWorkContext(), { wrapper });

    await result.current(CONTEXT, "CLINICAL_CARE");

    expect(post).toHaveBeenCalledWith("/internal/v1/work-context/session/mode", {
      contextId: "ctx-9",
      workMode: "CLINICAL_CARE",
      previousJti: "old-jti",
    });
  });

  it("stores the new session including contextId and workMode", async () => {
    post.mockResolvedValue({
      data: { id: "actor-1", type: "work-mode-session", attributes: { token: "tok", jti: "jti-2", contextId: "ctx-9", workMode: "CLINICAL_CARE", expiresAt: "2026-07-28T05:15:00Z" } },
    });
    const { result } = renderHook(() => useSwitchWorkContext(), { wrapper });

    await result.current(CONTEXT, "CLINICAL_CARE");

    expect(setSessionMock).toHaveBeenCalledWith(
      expect.objectContaining({ jti: "jti-2", token: "tok", contextId: "ctx-9", workMode: "CLINICAL_CARE" }),
    );
  });

  it("sweeps stale department/ward/programme sessionStorage and repopulates from the new context", async () => {
    sessionStorage.setItem("exp:department_id", "stale-dept");
    sessionStorage.setItem("exp:programme_id", "stale-programme");
    sessionStorage.setItem("exp:ward_id", "stale-ward");
    post.mockResolvedValue({
      data: { id: "actor-1", type: "work-mode-session", attributes: { token: "tok", jti: "jti-2" } },
    });
    const { result } = renderHook(() => useSwitchWorkContext(), { wrapper });

    await result.current(CONTEXT, "CLINICAL_CARE");

    expect(sessionStorage.getItem("exp:ward_id")).toBeNull();
    expect(sessionStorage.getItem("exp:facility")).toBe(JSON.stringify({ id: "facility-uuid-1" }));
    expect(sessionStorage.getItem("exp:work_mode")).toBe("CLINICAL_CARE");
  });

  it("repopulates exp:department_id and exp:programme_id from the new context when present", async () => {
    sessionStorage.setItem("exp:department_id", "stale-dept");
    sessionStorage.setItem("exp:programme_id", "stale-programme");
    post.mockResolvedValue({
      data: { id: "actor-1", type: "work-mode-session", attributes: { token: "tok", jti: "jti-2" } },
    });
    const { result } = renderHook(() => useSwitchWorkContext(), { wrapper });
    const contextWithProgrammeAndDept: ResolvedWorkContextView = {
      ...CONTEXT,
      departmentId: "dept-7",
      programmeId: "prog-3",
    };

    await result.current(contextWithProgrammeAndDept, "DEPARTMENT_MANAGEMENT");

    expect(sessionStorage.getItem("exp:department_id")).toBe("dept-7");
    expect(sessionStorage.getItem("exp:programme_id")).toBe("prog-3");
  });

  it("navigates to the target href with context_id appended after the switch completes", async () => {
    post.mockResolvedValue({
      data: { id: "actor-1", type: "work-mode-session", attributes: { token: "tok", jti: "jti-2" } },
    });
    const { result } = renderHook(() => useSwitchWorkContext(), { wrapper });

    await result.current(CONTEXT, "CLINICAL_CARE");

    await waitFor(() => expect(window.location.assign).toHaveBeenCalledWith("/work?context_id=ctx-9"));
  });
});
