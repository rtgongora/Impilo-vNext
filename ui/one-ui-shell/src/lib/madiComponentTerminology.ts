import type { ComponentType } from "./madi";

/** Default ZIBO / SNOMED pins (ZW release train) — superseded by API when available. */
export const MADI_COMPONENT_ZIBO_ARTIFACTS: Record<
  ComponentType,
  { label: string; canonicalUrl: string; code: string; pinnedVersion: string }
> = {
  WHOLE_BLOOD: {
    label: "Whole blood product",
    canonicalUrl: "http://snomed.info/sct/333922001",
    code: "333922001",
    pinnedVersion: "20260401",
  },
  RED_CELLS: {
    label: "Packed red blood cells",
    canonicalUrl: "http://snomed.info/sct/431069004",
    code: "431069004",
    pinnedVersion: "20260401",
  },
  PLATELETS: {
    label: "Platelet product",
    canonicalUrl: "http://snomed.info/sct/346447007",
    code: "346447007",
    pinnedVersion: "20260401",
  },
  PLASMA: {
    label: "Fresh frozen plasma",
    canonicalUrl: "http://snomed.info/sct/346075003",
    code: "346075003",
    pinnedVersion: "20260401",
  },
  CRYOPRECIPITATE: {
    label: "Cryoprecipitate product",
    canonicalUrl: "http://snomed.info/sct/346074004",
    code: "346074004",
    pinnedVersion: "20260401",
  },
};

export const MADI_ZIBO_JURISDICTION_PINS: Record<
  string,
  { releaseTrain: string; components: Partial<Record<ComponentType, { label: string; canonicalUrl: string; code: string; pinnedVersion: string }>> }
> = {
  ZW: {
    releaseTrain: "ZW-MADI-2026.04",
    components: MADI_COMPONENT_ZIBO_ARTIFACTS,
  },
  MW: {
    releaseTrain: "MW-MADI-2026.01",
    components: {
      WHOLE_BLOOD: { ...MADI_COMPONENT_ZIBO_ARTIFACTS.WHOLE_BLOOD, pinnedVersion: "20260115" },
      RED_CELLS: { ...MADI_COMPONENT_ZIBO_ARTIFACTS.RED_CELLS, pinnedVersion: "20260115" },
      PLATELETS: { ...MADI_COMPONENT_ZIBO_ARTIFACTS.PLATELETS, pinnedVersion: "20260115" },
      PLASMA: { ...MADI_COMPONENT_ZIBO_ARTIFACTS.PLASMA, pinnedVersion: "20260115" },
      CRYOPRECIPITATE: { ...MADI_COMPONENT_ZIBO_ARTIFACTS.CRYOPRECIPITATE, pinnedVersion: "20260115" },
    },
  },
};

export function resolveZiboArtifact(componentType: ComponentType, jurisdiction = "ZW") {
  const pack = MADI_ZIBO_JURISDICTION_PINS[jurisdiction] ?? MADI_ZIBO_JURISDICTION_PINS.ZW;
  const artifact = pack.components[componentType] ?? MADI_COMPONENT_ZIBO_ARTIFACTS[componentType];
  return { ...artifact, releaseTrain: pack.releaseTrain, jurisdiction };
}

export function ziboTerminologyHref(componentType: ComponentType, jurisdiction = "ZW"): string {
  const artifact = resolveZiboArtifact(componentType, jurisdiction);
  const base = `/registry/terminology/${encodeURIComponent(artifact.canonicalUrl)}`;
  return `${base}?version=${encodeURIComponent(artifact.pinnedVersion)}`;
}
