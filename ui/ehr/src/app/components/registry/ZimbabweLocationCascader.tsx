"use client";

import { useCallback, useEffect, useState } from "react";
import { listZimbabweProvinces } from "@/lib/registry/zimbabweAdmin";
import { fetchJson } from "@/lib/apiClient";

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

type DistrictRow = { districtCode: string; name: string };
type WardRow = { wardCode: string; name: string };
type LocalityHit = { id: string; name: string };

function dataArray<T>(res: { data?: unknown }): T[] {
  const d = res.data;
  return Array.isArray(d) ? (d as T[]) : [];
}

export function ZimbabweLocationCascader(props: Props) {
  const {
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
  } = props;

  const [districts, setDistricts] = useState<DistrictRow[]>([]);
  const [wards, setWards] = useState<WardRow[]>([]);
  const [localityHits, setLocalityHits] = useState<LocalityHit[]>([]);
  const [geoErr, setGeoErr] = useState<string | null>(null);

  const loadDistricts = useCallback(async () => {
    if (!provinceCode) {
      setDistricts([]);
      return;
    }
    setGeoErr(null);
    try {
      const res = await fetchJson<{ data: unknown }>(
        `/internal/v1/registry/geo/zw/districts?provinceCode=${encodeURIComponent(provinceCode)}`,
      );
      setDistricts(dataArray<DistrictRow>(res));
    } catch {
      setDistricts([]);
      setGeoErr("Districts unavailable (import Tuso reference data).");
    }
  }, [provinceCode]);

  const loadWards = useCallback(async () => {
    if (!districtCode) {
      setWards([]);
      return;
    }
    try {
      const res = await fetchJson<{ data: unknown }>(
        `/internal/v1/registry/geo/zw/wards?districtCode=${encodeURIComponent(districtCode)}`,
      );
      setWards(dataArray<WardRow>(res));
    } catch {
      setWards([]);
      setGeoErr("Wards unavailable (import Tuso reference data).");
    }
  }, [districtCode]);

  useEffect(() => {
    void loadDistricts();
  }, [loadDistricts]);

  useEffect(() => {
    void loadWards();
  }, [loadWards]);

  async function searchLocalities(q: string) {
    if (!districtCode || q.trim().length < 2) {
      setLocalityHits([]);
      return;
    }
    try {
      const res = await fetchJson<{ data: unknown }>(
        `/internal/v1/registry/localities/search?districtCode=${encodeURIComponent(districtCode)}&q=${encodeURIComponent(q.trim())}&limit=15`,
      );
      setLocalityHits(dataArray<LocalityHit>(res));
    } catch {
      setLocalityHits([]);
    }
  }

  if (countryAlpha2 !== "ZW") {
    return <p className="text-xs text-gray-500">ZW pickers appear when country is Zimbabwe.</p>;
  }

  const provinces = listZimbabweProvinces();

  return (
    <div className="space-y-2 rounded border border-dashed border-impilo-primary/30 bg-white p-2 text-sm">
      {geoErr ? <p className="text-xs text-amber-800">{geoErr}</p> : null}
      <div>
        <label className="block text-xs text-gray-600">Province</label>
        <select
          disabled={disabled}
          value={provinceCode}
          onChange={(e) => {
            onProvince(e.target.value);
            onDistrict("");
            onWard("");
            onLocalityId("");
          }}
          className="w-full border rounded px-2 py-1"
        >
          <option value="">—</option>
          {provinces.map((p) => (
            <option key={p.code} value={p.code}>
              {p.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="block text-xs text-gray-600">District</label>
        <select
          disabled={disabled || !provinceCode}
          value={districtCode}
          onChange={(e) => {
            onDistrict(e.target.value);
            onWard("");
            onLocalityId("");
          }}
          className="w-full border rounded px-2 py-1"
        >
          <option value="">—</option>
          {districts.map((d) => (
            <option key={d.districtCode} value={d.districtCode}>
              {d.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="block text-xs text-gray-600">Ward</label>
        <select
          disabled={disabled || !districtCode}
          value={wardCode}
          onChange={(e) => onWard(e.target.value)}
          className="w-full border rounded px-2 py-1"
        >
          <option value="">—</option>
          {wards.map((w) => (
            <option key={w.wardCode} value={w.wardCode}>
              {w.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="block text-xs text-gray-600">Locality search</label>
        <input
          className="w-full border rounded px-2 py-1 text-sm"
          disabled={disabled || !districtCode}
          placeholder="2+ chars"
          onChange={(e) => void searchLocalities(e.target.value)}
        />
        {localityHits.length > 0 ? (
          <ul className="mt-1 max-h-24 overflow-y-auto text-xs border rounded">
            {localityHits.map((h) => (
              <li key={h.id}>
                <button
                  type="button"
                  className="w-full text-left px-2 py-0.5 hover:bg-slate-100"
                  onClick={() => {
                    onLocalityId(h.id);
                    onLocalityFreeText(h.name);
                  }}
                >
                  {h.name}
                </button>
              </li>
            ))}
          </ul>
        ) : null}
        <p className="text-[10px] text-gray-500">ID: {localityGazetteerId || "—"}</p>
      </div>
      <div>
        <label className="block text-xs text-gray-600">Locality proposal text</label>
        <input
          className="w-full border rounded px-2 py-1 text-sm"
          disabled={disabled}
          value={localityFreeText}
          onChange={(e) => onLocalityFreeText(e.target.value)}
        />
      </div>
    </div>
  );
}
