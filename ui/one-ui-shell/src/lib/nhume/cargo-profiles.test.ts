import { describe, expect, it } from "vitest";
import { CARGO_PROFILES, applyCargoProfile, getCargoProfile, readDeliveryLinks } from "./cargo-profiles";

describe("nhume cargo profiles", () => {
  it("covers every movement category the health system needs", () => {
    const ids = CARGO_PROFILES.map((p) => p.id);
    for (const id of ["PATIENT", "SPECIMEN", "BLOOD", "VACCINE", "MEDICINE", "COMMODITY", "EQUIPMENT", "TEAM", "DOCUMENT", "GENERAL"]) {
      expect(ids).toContain(id);
    }
    // Non-patient profiles must NOT be patient-linked (the "not a patient-only form" rule).
    expect(getCargoProfile("SPECIMEN")!.patientLinked).toBe(false);
    expect(getCargoProfile("COMMODITY")!.patientLinked).toBe(false);
    expect(getCargoProfile("PATIENT")!.patientLinked).toBe(true);
  });

  it("specimen mission sets specimen + custody + biohazard and links the lab order", () => {
    const c = applyCargoProfile("SPECIMEN", {
      specimenType: "SPUTUM",
      numberOfSpecimens: 3,
      biohazard: true,
      coldChain: true,
      orosOrderRef: "ORD-123",
    });
    expect(c.deliveryType).toBe("LAB_SAMPLE_PICKUP");
    expect(c.flags.specimen).toBe(true);
    expect(c.flags.chain_of_custody_required).toBe(true); // default + biohazard
    expect(c.flags.biohazard).toBe(true);
    expect(c.flags.cold_chain_required).toBe(true);
    expect(c.clinicalContextRef).toBe("ORD-123");
    expect((c.metadata.cargo as Record<string, unknown>).specimenType).toBe("SPUTUM");
    expect((c.metadata.links as Record<string, string>).orosOrderRef).toBe("ORD-123");
  });

  it("blood mission is cold-chain + custody by default and links the MADI order", () => {
    const c = applyCargoProfile("BLOOD", { bloodGroup: "O_NEG", units: 2, madiOrderRef: "MADI-9" });
    expect(c.deliveryType).toBe("BLOOD_PRODUCT");
    expect(c.flags.cold_chain_required).toBe(true);
    expect(c.flags.chain_of_custody_required).toBe(true);
    expect(c.clinicalContextRef).toBe("MADI-9");
    expect((c.metadata.links as Record<string, string>).madiOrderRef).toBe("MADI-9");
    expect((c.metadata.cargo as Record<string, unknown>).units).toBe(2);
    expect((c.metadata.cargo as Record<string, unknown>).identityVerification).toBe("OTP");
  });

  it("commodity mission links a Dura requisition without faking stock", () => {
    const c = applyCargoProfile("COMMODITY", { commodityCategory: "ARVs", stockoutRisk: true, duraRequisitionRef: "REQ-77" });
    expect(c.deliveryType).toBe("PROGRAMME_COMMODITY");
    expect((c.metadata.links as Record<string, string>).duraRequisitionRef).toBe("REQ-77");
    expect(c.clinicalContextRef).toBeUndefined(); // commodities are not clinical-linked
  });

  it("equipment mission carries fragile + return-required", () => {
    const c = applyCargoProfile("EQUIPMENT", { equipmentType: "oxygen concentrator", returnRequired: true });
    expect(c.flags.fragile).toBe(true);
    expect(c.flags.return_required).toBe(true);
  });

  it("readDeliveryLinks turns persisted metadata into deep links", () => {
    const links = readDeliveryLinks({ links: { orosOrderRef: "ORD-1", madiOrderRef: "MADI-2" } });
    expect(links).toHaveLength(2);
    const oros = links.find((l) => l.system === "OROS")!;
    expect(oros.href).toContain("/diagnostics/orders?order=ORD-1");
    expect(readDeliveryLinks({})).toEqual([]);
    expect(readDeliveryLinks(undefined)).toEqual([]);
  });
});
