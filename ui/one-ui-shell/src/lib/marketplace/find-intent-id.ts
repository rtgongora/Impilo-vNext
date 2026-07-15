/** Deep, tolerant scan for the MusheX intent id in the pay response. */
export function findIntentId(payload: unknown, depth = 0): string | undefined {
  if (payload == null || typeof payload !== "object" || depth > 4) return undefined;
  const record = payload as Record<string, unknown>;
  for (const key of ["mushexPaymentIntentId", "mushex_payment_intent_id", "paymentIntentId", "intentId"]) {
    const value = record[key];
    if (typeof value === "string" && value) return value;
  }
  for (const value of Object.values(record)) {
    if (value && typeof value === "object") {
      const found = findIntentId(value, depth + 1);
      if (found) return found;
    }
  }
  return undefined;
}
