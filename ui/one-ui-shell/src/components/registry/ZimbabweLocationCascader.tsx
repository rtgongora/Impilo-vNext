"use client";

import { useCallback, useEffect, useState } from "react";
import { listZimbabweProvinces } from "@/lib/registry/zimbabweAdmin";
import { apiClient, type ApiResponse } from "@/lib/api-client";

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

type DistrictRow = { districtCode: string; name: string; provinceCode?: string };
type WardRow = { wardCode: string; name: string };
type LocalityHit = { id: string; name: string; districtCode?: string; wardCode?: string | null };

/**
 * Zimbabwe location: provinces from registry-templates; districts/wards from Tuso (COD-AB import).
 * Locality: Tuso gazetteer search + governed proposal path.
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
  const [districts, setDistricts] = useState<DistrictRow[]>([]);
  const [wards, setWards] = useState<WardRow[]>([]);
  const [localityHits, setLocalityHits] = useState<LocalityHit[]>([]);
  const [geoErr, setGeoErr] = useState<string | null>(null);
  const [proposalBusy, setProposalBusy] = useState(false);
  const [proposalMsg, setProposalMsg] = useState<string | null>(null);

  const loadDistricts = useCallback(async () => {
    if (!provinceCode) {
      setDistricts([]);
      return;
    }
    setGeoErr(null);
    try {
      const res = await apiClient.get<ApiResponse<DistrictRow[]>>(
        `/internal/v1/registry/geo/zw/districts?provinceCode=${encodeURIComponent(provinceCode)}`,
      );
      setDistricts(Array.isArray(res.data) ? res.data : []);
    } catch {
      setDistricts([]);
      setGeoErr("Could not load districts (Tuso / BFF unavailable or empty import).");
    }
  }, [provinceCode]);

  const loadWards = useCallback(async () => {
    if (!districtCode) {
      setWards([]);
      return;
    }
    setGeoErr(null);
    try {
      const res = await apiClient.get<ApiResponse<WardRow[]>>(
        `/internal/v1/registry/geo/zw/wards?districtCode=${encodeURIComponent(districtCode)}`,
      );
      setWards(Array.isArray(res.data) ? res.data : []);
    } catch {
      setWards([]);
      setGeoErr("Could not load wards (Tuso / BFF unavailable or empty import).");
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
      const res = await apiClient.get<ApiResponse<LocalityHit[]>>(
        `/internal/v1/registry/localities/search?districtCode=${encodeURIComponent(districtCode)}&q=${encodeURIComponent(q.trim())}&limit=15`,
      );
      setLocalityHits(Array.isArray(res.data) ? res.data : []);
    } catch {
      setLocalityHits([]);
    }
  }

  async function submitProposal() {
    if (!districtCode || !localityFreeText.trim()) {
      setProposalMsg("Select district and enter a proposed locality name.");
      return;
    }
    setProposalBusy(true);
    setProposalMsg(null);
    try {
      await apiClient.post<ApiResponse<unknown>>("/internal/v1/registry/localities/proposals", {
        districtCode,
        wardCode: wardCode || undefined,
        proposedName: localityFreeText.trim(),
      });
      setProposalMsg("Proposal submitted for registry review.");
    } catch {
      setProposalMsg("Proposal failed (check roles / Tuso availability).");
    } finally {
      setProposalBusy(false);
    }
  }

  if (countryAlpha2 !== "ZW") {
    return (
      <p className="text-xs text-muted-foreground">
        Zimbabwe administrative pickers are shown when country is Zimbabwe (ZW).
      </p>
    );
  }

  const provinces = listZimbabweProvinces();

  return (
    <div className="space-y-3 rounded-lg border border-dashed border-primary/25 bg-primary-soft/40 p-3">
      <p className="text-xs font-medium text-impilo-900">Zimbabwe location (COD-AB–aligned hierarchy)</p>
      {geoErr ? <p className="text-xs text-warning-foreground">{geoErr}</p> : null}
      <div>
        <label className="block text-xs font-medium text-muted-foreground mb-1">Province (Admin 1)</label>
        <select
          disabled={disabled}
          value={provinceCode}
          onChange={(e) => {
            onProvince(e.target.value);
            onDistrict("");
            onWard("");
            onLocalityId("");
          }}
          className="w-full px-3 py-2 border border-border rounded-lg text-sm"
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
        <label className="block text-xs font-medium text-muted-foreground mb-1">District (Admin 2)</label>
        <select
          disabled={disabled || !provinceCode}
          value={districtCode}
          onChange={(e) => {
            onDistrict(e.target.value);
            onWard("");
            onLocalityId("");
          }}
          className="w-full px-3 py-2 border border-border rounded-lg text-sm"
        >
          <option value="">{districts.length ? "Select district…" : "No districts loaded — run COD-AB import"}</option>
          {districts.map((d) => (
            <option key={d.districtCode} value={d.districtCode}>
              {d.name} ({d.districtCode})
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="block text-xs font-medium text-muted-foreground mb-1">Ward (Admin 3)</label>
        <select
          disabled={disabled || !districtCode}
          value={wardCode}
          onChange={(e) => onWard(e.target.value)}
          className="w-full px-3 py-2 border border-border rounded-lg text-sm"
        >
          <option value="">{wards.length ? "Select ward…" : "No wards loaded — run COD-AB import"}</option>
          {wards.map((w) => (
            <option key={w.wardCode} value={w.wardCode}>
              {w.name} ({w.wardCode})
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="block text-xs font-medium text-muted-foreground mb-1">Locality (Tuso gazetteer)</label>
        <div className="flex gap-2">
          <input
            type="text"
            disabled={disabled || !districtCode}
            placeholder="Type 2+ characters to search approved localities"
            className="flex-1 px-3 py-2 border border-border rounded-lg text-sm"
            onChange={(e) => void searchLocalities(e.target.value)}
          />
        </div>
        {localityHits.length > 0 ? (
          <ul className="mt-1 max-h-28 overflow-y-auto rounded border border-border bg-card text-xs">
            {localityHits.map((h) => (
              <li key={h.id}>
                <button
                  type="button"
                  disabled={disabled}
                  className="w-full text-left px-2 py-1 hover:bg-primary-soft"
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
        <p className="mt-1 text-[11px] text-muted-foreground">Selected ID: {localityGazetteerId || "—"}</p>
      </div>
      <div>
        <label className="block text-xs font-medium text-muted-foreground mb-1">Propose new locality (governed)</label>
        <input
          type="text"
          disabled={disabled}
          value={localityFreeText}
          onChange={(e) => onLocalityFreeText(e.target.value)}
          placeholder="If not in gazetteer — propose for terminology review"
          className="w-full px-3 py-2 border border-border rounded-lg text-sm"
        />
        <div className="mt-1 flex flex-wrap items-center gap-2">
          <button
            type="button"
            disabled={disabled || proposalBusy}
            onClick={() => void submitProposal()}
            className="text-xs px-2 py-1 rounded border border-border bg-card hover:bg-background"
          >
            {proposalBusy ? "Submitting…" : "Submit proposal"}
          </button>
          {proposalMsg ? <span className="text-[11px] text-muted-foreground">{proposalMsg}</span> : null}
        </div>
      </div>
    </div>
  );
}
