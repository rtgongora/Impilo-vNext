/**
 * Cart Service — Marketplace shopping cart management.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/marketplace/cart/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Cart } from "../types";

const V1 = "/internal/v1/mobile/citizen/marketplace/cart";

export async function fetchCart(): Promise<Cart> {
  const response = await apiClient.get<{ data: Cart }>(V1);
  return response.data.data;
}

export async function addToCart(itemId: string, quantity: number): Promise<Cart> {
  const response = await apiClient.post<{ data: Cart }>(`${V1}/items`, { itemId, quantity });
  return response.data.data;
}

export async function removeFromCart(itemId: string): Promise<void> {
  await apiClient.delete(`${V1}/items/${encodeURIComponent(itemId)}`);
}

export async function checkout(cartId: string): Promise<void> {
  await apiClient.post(`${V1}/checkout`, { cartId });
}
