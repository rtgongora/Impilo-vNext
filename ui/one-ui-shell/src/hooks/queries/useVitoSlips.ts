/**
 * VITO Slips Hooks — Health OS §1 (Identity & Trust Services)
 *
 * Provides helper functions to generate URLs for PDF slips
 * that are streamed directly from the Experience BFF.
 */

export interface EmergencyCapsuleParams {
  healthId: string;
  hasAllergies?: boolean;
  hasMajorConditions?: boolean;
  payerIndicator?: string;
}

export interface PickupSlipParams {
  pickupToken: string;
  otp: string;
  delegateName: string;
  facilityName: string;
  expiresAt: string;
}

/**
 * Generates the BFF URL for the Emergency Capsule PDF.
 * This URL can be used in <a href> or <iframe> src.
 */
export function getEmergencyCapsuleUrl(params: EmergencyCapsuleParams): string {
  const searchParams = new URLSearchParams();
  searchParams.set("healthId", params.healthId);
  if (params.hasAllergies !== undefined) {
    searchParams.set("hasAllergies", String(params.hasAllergies));
  }
  if (params.hasMajorConditions !== undefined) {
    searchParams.set("hasMajorConditions", String(params.hasMajorConditions));
  }
  if (params.payerIndicator) {
    searchParams.set("payerIndicator", params.payerIndicator);
  }

  return `/internal/v1/vito/slips/emergency-capsule.pdf?${searchParams.toString()}`;
}

/**
 * Generates the BFF URL for the Pickup Slip PDF.
 * This URL can be used in <a href> or <iframe> src.
 */
export function getPickupSlipUrl(params: PickupSlipParams): string {
  const searchParams = new URLSearchParams();
  searchParams.set("pickupToken", params.pickupToken);
  searchParams.set("otp", params.otp);
  searchParams.set("delegateName", params.delegateName);
  searchParams.set("facilityName", params.facilityName);
  searchParams.set("expiresAt", params.expiresAt);

  return `/internal/v1/vito/slips/pickup.pdf?${searchParams.toString()}`;
}
