import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { useBishopScoreClassifyForm } from "./useBishopScore";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { post } }));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe("useBishopScoreClassifyForm", () => {
  beforeEach(() => post.mockReset());

  it("posts classify-form with blank components omitted", async () => {
    // apiClient.post resolves the parsed body itself: `{ data: assessment, meta }`.
    post.mockResolvedValue({
      data: {
        score: null,
        interpretation: "INCOMPLETE",
        components: { dilation: 2 },
        missing: ["effacement", "station", "consistency", "position"],
        content_version: "ENGINEERING_SEED",
      },
    });
    const { result } = renderHook(() => useBishopScoreClassifyForm(), { wrapper });
    result.current.mutate({
      formKey: "impilo.maternal.bishop.v1",
      answers: { "bishop.dilation": "DIL_3_4" },
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith("/internal/v1/clinical/maternal/bishop/classify-form", {
      formKey: "impilo.maternal.bishop.v1",
      answers: { "bishop.dilation": "DIL_3_4" },
    });
    expect(result.current.data?.interpretation).toBe("INCOMPLETE");
    expect(result.current.data?.score).toBeNull();
  });
});
