import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn(() => Promise.resolve({ data: [] })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
  },
}));

import { apiClient } from "@/lib/api-client";
import {
  fetchProviderAccessRequest,
  fetchProviderAccessRequests,
  providerAccessErrorMessage,
  submitProviderAccessRequest,
} from "./useProviderAccessRequest";

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

describe("useProviderAccessRequest network contract", () => {
  beforeEach(() => vi.clearAllMocks());

  it("submits a durable access request to the BFF access-request path", () => {
    const input = { requestType: "NEW_PROVIDER" as const, profession: "Medical Officer" };
    void submitProviderAccessRequest(input);
    expect(post).toHaveBeenCalledWith("/api/v1/provider-claim/access-request", input);
  });

  it("lists the applicant's requests from the status path", () => {
    void fetchProviderAccessRequests();
    expect(get).toHaveBeenCalledWith("/api/v1/provider-claim/status");
  });

  it("reads one request by public id", () => {
    void fetchProviderAccessRequest("PAR-ABC12345");
    expect(get).toHaveBeenCalledWith("/api/v1/provider-claim/status/PAR-ABC12345");
  });

  it("surfaces the honest BFF error message", () => {
    expect(
      providerAccessErrorMessage({ error: { message: "Awaiting organisation confirmation." } }, "fallback"),
    ).toBe("Awaiting organisation confirmation.");
    expect(providerAccessErrorMessage({}, "fallback")).toBe("fallback");
  });
});
