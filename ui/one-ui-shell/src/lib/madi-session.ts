/** Session-scoped Madi workspace keys (blood bank id per facility). */

const bloodBankKey = (facilityId: string) => `madi:blood-bank:${facilityId}`;

export function getStoredBloodBankId(facilityId: string | undefined): string {
  if (typeof window === "undefined" || !facilityId) return "";
  return sessionStorage.getItem(bloodBankKey(facilityId)) ?? "";
}

export function setStoredBloodBankId(facilityId: string, bloodBankId: string): void {
  if (typeof window === "undefined") return;
  sessionStorage.setItem(bloodBankKey(facilityId), bloodBankId);
}
