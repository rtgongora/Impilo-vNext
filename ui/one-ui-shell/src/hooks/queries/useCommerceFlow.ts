/**
 * Experience UI — Commerce flow (MSIKA Flow) hooks.
 *
 * Backed by Experience BFF:
 * - POST /internal/v1/commerce/cart/validate
 * - POST /internal/v1/commerce/orders
 * - GET  /internal/v1/commerce/orders/{orderId}
 * - POST /internal/v1/commerce/orders/{orderId}/{cancel|validate|price|pay}
 * - GET  /internal/v1/commerce/orders/{orderId}/tracking
 *
 * The controller forwards upstream JSON as-is; responses are treated as unknown JSON.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useWalletPay } from "@/hooks/queries/useMusheWallet";

export type CommerceJson = unknown;

export type CommerceCartValidatePayload = {
  items: Array<{ msikaCoreCode: string; qty: number }>;
  channel?: string;
  facilityRef?: string;
  providerRef?: string;
};

export function useCommerceValidateCart() {
  return useMutation<CommerceJson, unknown, CommerceCartValidatePayload>({
    mutationFn: (payload) => apiClient.post<CommerceJson>("/internal/v1/commerce/cart/validate", payload),
  });
}

export type CommerceCreateOrderPayload = {
  orderType: string;
  patientCpid?: string;
  facilityId?: string;
  facilityRef?: string;
  vendorId?: string;
  vendorRef?: string;
  providerRef?: string;
  idempotencyKey?: string;
  lines: Array<{
    msikaCoreCode: string;
    kind?: string;
    qty: number;
    unitPrice: number;
    fulfillmentMode?: string;
  }>;
};

export function useCommerceCreateOrder() {
  return useMutation<CommerceJson, unknown, CommerceCreateOrderPayload>({
    mutationFn: (payload) => apiClient.post<CommerceJson>("/internal/v1/commerce/orders", payload),
  });
}

export function useCommerceOrder(orderId: string | undefined, opts?: { refetchIntervalMs?: number }) {
  return useQuery<CommerceJson>({
    queryKey: ["commerce", "orders", orderId],
    queryFn: () => apiClient.get<CommerceJson>(`/internal/v1/commerce/orders/${encodeURIComponent(String(orderId))}`),
    enabled: Boolean(orderId),
    refetchInterval: opts?.refetchIntervalMs,
  });
}

export function useCommerceOrderTracking(orderId: string | undefined) {
  return useQuery<CommerceJson>({
    queryKey: ["commerce", "orders", orderId, "tracking"],
    queryFn: () => apiClient.get<CommerceJson>(`/internal/v1/commerce/orders/${encodeURIComponent(String(orderId))}/tracking`),
    enabled: Boolean(orderId),
  });
}

function actionPath(orderId: string, action: "cancel" | "validate" | "price" | "pay") {
  return `/internal/v1/commerce/orders/${encodeURIComponent(orderId)}/${action}`;
}

export function useCommerceOrderAction() {
  return useMutation<CommerceJson, unknown, { orderId: string; action: "cancel" | "validate" | "price" | "pay" }>({
    mutationFn: ({ orderId, action }) => apiClient.post<CommerceJson>(actionPath(orderId, action)),
  });
}

// ── Cart operations (absorbs msika-flow-portal:/cart) ────────────────

export function useCommerceCart() {
  return useQuery<CommerceJson>({
    queryKey: ["commerce-cart"],
    queryFn: () => apiClient.get<CommerceJson>("/internal/v1/commerce/cart?channel=WEB"),
  });
}

export function useValidateCart() {
  return useMutation({
    mutationFn: (payload?: CommerceCartValidatePayload) =>
      apiClient.post<CommerceJson>("/internal/v1/commerce/cart/validate", payload ?? { items: [], channel: "WEB" }),
  });
}

export function useCheckoutCart() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args?: {
      cartId: string;
      orderType?: string;
      facilityId?: string;
      vendorId?: string;
      idempotencyKey?: string;
      paymentMethod?: string;
    }) => {
      if (!args?.cartId) throw new Error("cartId required");
      return apiClient.post<CommerceJson>(`/internal/v1/commerce/cart/${encodeURIComponent(args.cartId)}/checkout`, {
        orderType: args.orderType ?? "OTC_PRODUCT_ORDER",
        facilityId: args.facilityId,
        vendorId: args.vendorId,
        idempotencyKey: args.idempotencyKey,
        paymentMethod: args.paymentMethod,
      });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["commerce-cart"] });
    },
  });
}

// ── Cart item mutations (M7 buyer lane) ──────────────────────────────

/** Tolerant unwrap: msika-flow wraps as ApiResponse{success,data}; some legs nest an extra data. */
export function unwrapCommerceData(payload: unknown): Record<string, unknown> | null {
  let current: unknown = payload;
  for (let depth = 0; depth < 4; depth += 1) {
    if (current == null || typeof current !== "object" || Array.isArray(current)) return null;
    const record = current as Record<string, unknown>;
    if ("cartId" in record || "orderId" in record || "items" in record || "lines" in record || "status" in record) {
      return record;
    }
    current = record.data;
  }
  return null;
}

export function extractCartId(payload: unknown): string | undefined {
  const cart = unwrapCommerceData(payload);
  const id = cart?.cartId ?? cart?.id;
  return typeof id === "string" && id ? id : undefined;
}

export function extractOrderId(payload: unknown): string | undefined {
  const order = unwrapCommerceData(payload);
  const id = order?.orderId ?? order?.id;
  if (typeof id === "string" && id) return id;
  if (typeof id === "number") return String(id);
  return undefined;
}

/**
 * Get-or-create the caller's OPEN cart and return its cartId.
 * Backed by GET /internal/v1/commerce/cart?channel=WEB (msika-flow /v1/carts/open).
 */
export async function ensureOpenCart(): Promise<string> {
  const payload = await apiClient.get<CommerceJson>("/internal/v1/commerce/cart?channel=WEB");
  const cartId = extractCartId(payload);
  if (!cartId) throw new Error("Could not resolve an open cart from the commerce service.");
  return cartId;
}

export type AddCartItemPayload = {
  /** Canonical msika core code (registry lane). */
  msikaCoreCode?: string;
  /** Storefront listing id — msika-flow add-item accepts listingId and resolves price server-side. */
  listingId?: string;
  qty?: number;
  kind?: string;
  fulfillmentMode?: string;
};

export function useAddCartItem() {
  const qc = useQueryClient();
  return useMutation<CommerceJson, unknown, AddCartItemPayload>({
    mutationFn: async (payload) => {
      if (!payload.msikaCoreCode && !payload.listingId) {
        throw new Error("msikaCoreCode or listingId required");
      }
      const cartId = await ensureOpenCart();
      return apiClient.post<CommerceJson>(`/internal/v1/commerce/cart/${encodeURIComponent(cartId)}/items`, {
        ...payload,
        qty: payload.qty ?? 1,
      });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["commerce-cart"] });
    },
  });
}

export function useRemoveCartItem() {
  const qc = useQueryClient();
  return useMutation<CommerceJson, unknown, { cartId: string; msikaCoreCode: string }>({
    mutationFn: ({ cartId, msikaCoreCode }) =>
      apiClient.post<CommerceJson>(
        `/internal/v1/commerce/cart/${encodeURIComponent(cartId)}/items/remove?msikaCoreCode=${encodeURIComponent(msikaCoreCode)}`,
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["commerce-cart"] });
    },
  });
}

// ── Payment confirm seam (single hook — M7 pay page) ─────────────────

export type CommercePaymentConfirmArgs = {
  orderId: string;
  /** Real MusheX intent id returned by POST /orders/{id}/pay (mushexPaymentIntentId). */
  intentId?: string;
  method: string;
  amount?: number;
  currency?: string;
};

/**
 * The ONE place the marketplace payment-confirm contract lives.
 *
 * Verified contract (runtime-proven on the 4-lane rig, 2026-07-12):
 * msika-flow `POST /orders/{id}/pay` creates a REAL MusheX payment intent and returns
 * its id; when the intent settles MusheX emits `mushex.payment.status.changed`, which
 * msika-flow's PaymentEventConsumer folds back into the order (PAYMENT_PENDING→PAID) —
 * that whole loop is live. In preview/sandbox the intent auto-settles at creation
 * (mushex apply-simulation-outcome), so callers polling the order reach PAID without
 * further citizen action. The wallet debit this hook fires
 * (`POST /internal/v1/wallet/pay`, BFF → Mushe wallet, reference = intent id) debits
 * the citizen wallet but does NOT record a payment against the MusheX intent —
 * mushex's real execution leg is `POST /mushex/v1/payment-intents/{id}/attempts`,
 * which no BFF passthrough exposes yet. Wiring wallet-debit→attempt is the one open
 * production seam (documented in the wave report); when it lands, only this hook changes.
 */
export function useCommercePaymentConfirm() {
  const walletPay = useWalletPay();
  return useMutation<CommerceJson, unknown, CommercePaymentConfirmArgs>({
    mutationFn: async ({ orderId, intentId, method, amount, currency }) => {
      if (!amount || amount <= 0) {
        throw new Error("Order amount is not available yet — price the order before paying.");
      }
      return walletPay.mutateAsync({
        method,
        amount,
        currency,
        reference: intentId ?? orderId,
        description: `Marketplace order ${orderId}`,
      });
    },
  });
}

