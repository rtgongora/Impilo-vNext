import provincesSeed from "@registry-templates/terminology/zimbabwe-provinces.seed.json";

export type ZimbabweProvince = (typeof provincesSeed)["provinces"][number];

export function listZimbabweProvinces(): ZimbabweProvince[] {
  return [...provincesSeed.provinces];
}

/**
 * Districts and wards are intentionally not bundled: import COD-AB / ZIMSTAT seeds.
 * UI should call Tuso reference-data or admin import APIs when available.
 */
export function districtsSeeded(): boolean {
  return false;
}

export function wardsSeeded(): boolean {
  return false;
}
