/**
 * Marketplace Friction Hook — Health OS §7, §8
 *
 * "Marketplace participation shall be governed according to the risk,
 *  regulatory status, and sensitivity of the product or service."
 *
 * "Friction must be proportionate to risk."
 *
 * Maps product risk classifications to friction levels and checkout requirements.
 */

import type { FrictionLevel } from "@/hooks/useFrictionLevel";

export type MarketplaceRiskClassification =
  | "UNRESTRICTED"    // Wellness goods, fitness accessories
  | "LOW_RISK"        // Basic medical supplies (bandages, thermometers)
  | "MODERATE_RISK"   // Medical devices, diagnostic kits
  | "HIGH_RISK"       // Prescription medicines
  | "REGULATED";      // Controlled substances, blood products

export interface MarketplaceFrictionRequirements {
  frictionLevel: FrictionLevel;
  requiresProvider: boolean;
  requiresFacility: boolean;
  maxQtyPerOrder: number | null;
  checkoutLabel: string;
  checkoutColor: string;
  warningText: string | null;
}

const RISK_TO_FRICTION: Record<MarketplaceRiskClassification, MarketplaceFrictionRequirements> = {
  UNRESTRICTED: {
    frictionLevel: "MINIMAL",
    requiresProvider: false,
    requiresFacility: false,
    maxQtyPerOrder: null,
    checkoutLabel: "Buy Now",
    checkoutColor: "bg-green-600 hover:bg-green-700",
    warningText: null,
  },
  LOW_RISK: {
    frictionLevel: "LOW",
    requiresProvider: false,
    requiresFacility: false,
    maxQtyPerOrder: null,
    checkoutLabel: "Add to Cart",
    checkoutColor: "bg-blue-600 hover:bg-blue-700",
    warningText: null,
  },
  MODERATE_RISK: {
    frictionLevel: "STANDARD",
    requiresProvider: false,
    requiresFacility: true,
    maxQtyPerOrder: 100,
    checkoutLabel: "Order (Facility)",
    checkoutColor: "bg-blue-600 hover:bg-blue-700",
    warningText: "This item requires facility context for ordering.",
  },
  HIGH_RISK: {
    frictionLevel: "ELEVATED",
    requiresProvider: true,
    requiresFacility: true,
    maxQtyPerOrder: 30,
    checkoutLabel: "Prescribe & Order",
    checkoutColor: "bg-amber-600 hover:bg-amber-700",
    warningText: "This item requires an active Provider ID and facility context. Prescription verification will be performed.",
  },
  REGULATED: {
    frictionLevel: "MAXIMUM",
    requiresProvider: true,
    requiresFacility: true,
    maxQtyPerOrder: 10,
    checkoutLabel: "Controlled Order",
    checkoutColor: "bg-red-600 hover:bg-red-700",
    warningText: "This is a controlled substance. Maximum assurance required. This transaction will be audited and requires step-up verification.",
  },
};

export function getMarketplaceFriction(risk: MarketplaceRiskClassification): MarketplaceFrictionRequirements {
  return RISK_TO_FRICTION[risk] ?? RISK_TO_FRICTION.LOW_RISK;
}

/** Derive risk classification from product attributes when explicit classification is absent. */
export function deriveRiskFromRestrictions(restrictions: Record<string, boolean> | null, controlled: boolean): MarketplaceRiskClassification {
  if (controlled) return "REGULATED";
  if (!restrictions) return "UNRESTRICTED";
  if (restrictions.prescription_required) return "HIGH_RISK";
  if (restrictions.provider_only_order || restrictions.age_restricted) return "MODERATE_RISK";
  if (restrictions.cold_chain_required) return "LOW_RISK";
  return "UNRESTRICTED";
}
