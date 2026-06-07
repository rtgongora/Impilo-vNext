import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  useCreateMvumoConsentRequest,
  useCreateMvumoTemplate,
  useMvumoAdminTemplates,
  useUpdateMvumoTemplate,
} from "../useMvumoAdmin";

const get = vi.fn();
const post = vi.fn();
const put = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
    put: (...args: unknown[]) => put(...args),
  },
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return createElement(QueryClientProvider, { client }, children);
}

describe("useMvumoAdmin", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    put.mockReset();
  });

  it("loads templates from mvumo-admin BFF", async () => {
    get.mockResolvedValue({ data: [{ templateKey: "registry-assisted-consent" }] });
    const { result } = renderHook(() => useMvumoAdminTemplates(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/mvumo-admin/templates");
  });

  it("creates templates via POST", async () => {
    post.mockResolvedValue({ data: { templateKey: "new-template" } });
    const { result } = renderHook(() => useCreateMvumoTemplate(), { wrapper });
    await result.current.mutateAsync({ templateKey: "new-template", title: "New" });
    expect(post).toHaveBeenCalledWith("/internal/v1/mvumo-admin/templates", {
      templateKey: "new-template",
      title: "New",
    });
  });

  it("updates templates via PUT", async () => {
    put.mockResolvedValue({ data: { templateKey: "registry-assisted-consent" } });
    const { result } = renderHook(() => useUpdateMvumoTemplate(), { wrapper });
    await result.current.mutateAsync({
      templateId: "tmpl-1",
      body: { title: "Updated title" },
    });
    expect(put).toHaveBeenCalledWith("/internal/v1/mvumo-admin/templates/tmpl-1", { title: "Updated title" });
  });

  it("creates consent requests via POST", async () => {
    post.mockResolvedValue({ data: { id: "cr-1" } });
    const { result } = renderHook(() => useCreateMvumoConsentRequest(), { wrapper });
    await result.current.mutateAsync({
      subjectPatientRef: "pat-001",
      templateKey: "registry-assisted-consent",
      workflowRef: "walk-in-intake",
    });
    expect(post).toHaveBeenCalledWith("/internal/v1/mvumo-admin/consent-requests", {
      subjectPatientRef: "pat-001",
      templateKey: "registry-assisted-consent",
      workflowRef: "walk-in-intake",
    });
  });
});
