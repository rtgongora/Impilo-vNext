import provincesSeed from "@registry-templates/terminology/zimbabwe-provinces.seed.json";

export type ZimbabweProvince = (typeof provincesSeed)["provinces"][number];

export function listZimbabweProvinces(): ZimbabweProvince[] {
  return [...provincesSeed.provinces];
}
