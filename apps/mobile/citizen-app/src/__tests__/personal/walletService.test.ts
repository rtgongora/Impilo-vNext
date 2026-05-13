/**
 * walletService Tests — Canonical Mushe Wallet plane.
 *
 * Verifies that `fetchWallet` / `fetchTransactions`:
 *   1. Hit the canonical experience-bff routes `/internal/v1/wallet/me{,/transactions}`.
 *   2. Never include a `patientId` query parameter (owner is implied by trust headers).
 *   3. Adapt the three documented BFF response shapes (flat upstream, JSON:API
 *      `attributes`, Spring Data Page `content`) onto the citizen UI types.
 *   4. Propagate ApiError (e.g. `WALLET_UPSTREAM_UNAVAILABLE`) instead of
 *      silently returning fabricated data.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mocks = vi.hoisted(() => {
  class FakeApiError extends Error {
    public readonly code: string;
    public readonly status: number;
    public readonly correlationId: string;
    constructor(code: string, status: number) {
      super("upstream unavailable");
      this.code = code;
      this.status = status;
      this.correlationId = "corr-fake";
    }
  }
  return {
    apiClient: {
      get: vi.fn(),
      post: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn(),
    },
    FakeApiError,
  };
});

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mocks.apiClient,
  ApiError: mocks.FakeApiError,
  fetchPage: vi.fn(),
}));

const mockApiClient = mocks.apiClient;
const FakeApiError = mocks.FakeApiError;

import { fetchWallet, fetchTransactions } from "../../services/walletService";

describe("walletService — canonical Mushe Wallet routes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("fetchWallet", () => {
    it("calls GET /internal/v1/wallet/me with no patientId query", async () => {
      mockApiClient.get.mockResolvedValueOnce({
        data: { data: { id: "wal-1", currency: "USD", balance: 250, status: "ACTIVE" } },
      });

      await fetchWallet();

      expect(mockApiClient.get).toHaveBeenCalledTimes(1);
      expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/wallet/me");
    });

    it("adapts the BFF local-fallback JSON:API shape (attributes wrapper)", async () => {
      // Matches WalletController's allow-local-fallback=true response.
      mockApiClient.get.mockResolvedValueOnce({
        data: {
          data: {
            id: "wal-abc",
            type: "wallet",
            attributes: {
              id: "wal-abc",
              ownerType: "PERSON",
              ownerRef: "actor-1",
              currency: "USD",
              balance: 250.0,
              availableBalance: 250.0,
              status: "ACTIVE",
            },
          },
        },
      });

      const wallet = await fetchWallet();

      expect(wallet).toEqual({
        id: "wal-abc",
        balance: 250.0,
        currency: "USD",
        status: "ACTIVE",
      });
    });

    it("adapts the flat upstream Mushe shape (no attributes wrapper)", async () => {
      mockApiClient.get.mockResolvedValueOnce({
        data: {
          data: { id: "wal-xyz", balance: 75.5, currency: "ZAR", status: "FROZEN" },
        },
      });

      const wallet = await fetchWallet();

      expect(wallet).toEqual({ id: "wal-xyz", balance: 75.5, currency: "ZAR", status: "FROZEN" });
    });

    it("returns honest defaults when upstream returns an empty object (never fabricates a balance)", async () => {
      mockApiClient.get.mockResolvedValueOnce({ data: { data: {} } });

      const wallet = await fetchWallet();

      expect(wallet.id).toBe("");
      expect(wallet.balance).toBe(0);
      expect(wallet.status).toBe("ACTIVE");
      expect(wallet.currency).toBe("USD");
    });

    it("propagates WALLET_UPSTREAM_UNAVAILABLE (does not swallow upstream failures)", async () => {
      mockApiClient.get.mockRejectedValueOnce(new FakeApiError("WALLET_UPSTREAM_UNAVAILABLE", 503));

      await expect(fetchWallet()).rejects.toMatchObject({
        code: "WALLET_UPSTREAM_UNAVAILABLE",
        status: 503,
      });
    });
  });

  describe("fetchTransactions", () => {
    it("calls GET /internal/v1/wallet/me/transactions with no patientId query", async () => {
      mockApiClient.get.mockResolvedValueOnce({ data: { data: [] } });

      await fetchTransactions();

      expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/wallet/me/transactions");
    });

    it("adapts a flat array (BFF local fallback)", async () => {
      mockApiClient.get.mockResolvedValueOnce({
        data: {
          data: [
            {
              id: "txn-1",
              method: "MUSHE_WALLET",
              amount: 12.5,
              currency: "USD",
              status: "COMPLETED",
              createdAt: "2026-05-01T10:00:00Z",
              description: "Pharmacy",
            },
          ],
        },
      });

      const txns = await fetchTransactions();

      expect(txns).toHaveLength(1);
      expect(txns[0]).toMatchObject({
        id: "txn-1",
        amount: 12.5,
        currency: "USD",
        status: "COMPLETED",
        description: "Pharmacy",
        transactionType: "MUSHE_WALLET",
      });
    });

    it("adapts the Spring Data Page shape (content array) from upstream Mushe", async () => {
      mockApiClient.get.mockResolvedValueOnce({
        data: {
          data: {
            content: [
              {
                id: "txn-7",
                txnType: "CREDIT",
                amount: 100,
                currency: "USD",
                status: "COMPLETED",
                createdAt: "2026-05-02T09:00:00Z",
              },
              {
                id: "txn-8",
                txnType: "DEBIT",
                amount: 25,
                currency: "USD",
                status: "COMPLETED",
                createdAt: "2026-05-03T09:00:00Z",
              },
            ],
            page: { number: 0, size: 50, totalElements: 2, totalPages: 1 },
          },
        },
      });

      const txns = await fetchTransactions();

      expect(txns).toHaveLength(2);
      expect(txns[0].transactionType).toBe("CREDIT");
      expect(txns[1].transactionType).toBe("DEBIT");
    });

    it("returns [] when upstream returns null/missing data (no crash)", async () => {
      mockApiClient.get.mockResolvedValueOnce({ data: { data: null } });
      expect(await fetchTransactions()).toEqual([]);
    });

    it("propagates WALLET_UPSTREAM_UNAVAILABLE", async () => {
      mockApiClient.get.mockRejectedValueOnce(new FakeApiError("WALLET_UPSTREAM_UNAVAILABLE", 503));

      await expect(fetchTransactions()).rejects.toMatchObject({
        code: "WALLET_UPSTREAM_UNAVAILABLE",
        status: 503,
      });
    });
  });
});
