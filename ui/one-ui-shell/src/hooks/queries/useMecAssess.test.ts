import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const post = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiClient: { post: (...args: unknown[]) => post(...args) },
}));

import { useMecAssess } from "./useMecAssess";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe("useMecAssess", () => {
  it("calls the MEC assess BFF endpoint", async () => {
    post.mockResolvedValue({ data: { methods: [] } });
    const { result } = renderHook(() => useMecAssess(), { wrapper });
    result.current.mutate({ facts: { ageYears: 25 } });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/clinical/contraception/mec/assess",
      expect.objectContaining({ usePhase: "INITIATION", facts: { ageYears: 25 } }),
    );
  });
});
