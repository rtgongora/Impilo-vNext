"use client";

import { listZimbabweProvinces, districtsSeeded, wardsSeeded } from "@/lib/registry/zimbabweAdmin";

type Props = {
  countryAlpha2: string;
  provinceCode: string;
  districtCode: string;
  wardCode: string;
  localityGazetteerId: string;
  localityFreeText: string;
  onProvince: (code: string) => void;
  onDistrict: (code: string) => void;
  onWard: (code: string) => void;
  onLocalityId: (id: string) => void;
  onLocalityFreeText: (text: string) => void;
  disabled?: boolean;
};

/**
 * Cascading Zimbabwe location capture. Province list is seeded; district/ward CSV imports
 * are pending. Locality is Tuso gazetteer–backed — free text here is only a *proposal*
 * captured offline for terminology review, not a canonical locality code.
 */
export function ZimbabweLocationCascader({
  countryAlpha2,
  provinceCode,
  districtCode,
  wardCode,
  localityGazetteerId,
  localityFreeText,
  onProvince,
  onDistrict,
  onWard,
  onLocalityId,
  onLocalityFreeText,
  disabled,
}: Props) {
  if (countryAlpha2 !== "ZW") {
    return (
      <p className="text-xs text-gray-500">
        Zimbabwe administrative pickers are shown when country is Zimbabwe (ZW).
      </p>
    );
  }

  const provinces = listZimbabweProvinces();
  const dSeeded = districtsSeeded();
  const wSeeded = wardsSeeded();

  return (
    <div className="space-y-3 rounded-lg border border-dashed border-impilo-200 bg-impilo-50/40 p-3">
      <p className="text-xs font-medium text-impilo-900">Zimbabwe location (COD-AB–aligned hierarchy)</p>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Province (Admin 1)</label>
        <select
          disabled={disabled}
          value={provinceCode}
          onChange={(e) => onProvince(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
        >
          <option value="">Select province…</option>
          {provinces.map((p) => (
            <option key={p.code} value={p.code}>
              {p.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">District (Admin 2)</label>
        <select
          disabled={disabled || !dSeeded}
          value={districtCode}
          onChange={(e) => onDistrict(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
        >
          <option value="">{dSeeded ? "Select district…" : "Pending COD-AB / ZIMSTAT seed"}</option>
        </select>
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Ward (Admin 3)</label>
        <select
          disabled={disabled || !wSeeded}
          value={wardCode}
          onChange={(e) => onWard(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
        >
          <option value="">{wSeeded ? "Select ward…" : "Pending COD-AB import (~1961 wards)"}</option>
        </select>
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Locality (Tuso gazetteer ID)</label>
        <input
          type="text"
          disabled={disabled}
          value={localityGazetteerId}
          onChange={(e) => onLocalityId(e.target.value)}
          placeholder="Search Tuso gazetteer (API wiring pending)"
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
        />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Proposed locality name (governed)</label>
        <input
          type="text"
          disabled={disabled}
          value={localityFreeText}
          onChange={(e) => onLocalityFreeText(e.target.value)}
          placeholder="If unknown in gazetteer — propose for terminology review"
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
        />
        <p className="mt-1 text-[11px] text-gray-500">
          Free text is allowed only as a <strong>proposal</strong> with code OTHER / pending Tuso approval per
          registry doctrine.
        </p>
      </div>
    </div>
  );
}
