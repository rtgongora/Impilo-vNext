/** Session-scoped vendor scope for trusted Experience-operator vendor workflows (not a vendor actor plane). */
const KEY = "exp:commerce_vendor_id";

export function readCommerceVendorId(): string | null {
  if (typeof window === "undefined") return null;
  const v = sessionStorage.getItem(KEY);
  return v && v.trim() ? v.trim() : null;
}

export function writeCommerceVendorId(vendorId: string): void {
  sessionStorage.setItem(KEY, vendorId.trim());
}

export function clearCommerceVendorId(): void {
  sessionStorage.removeItem(KEY);
}
