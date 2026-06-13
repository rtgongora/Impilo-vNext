"use client";

/**
 * NdilaLocationPicker — composite picker used everywhere a service needs to
 * capture or update a geospatial point:
 *   - Tuso facility coordinates
 *   - Indawo site location
 *   - Nhume/Dispatch delivery origin / destination
 *   - Public Health field-team check-in
 *   - Citizen "set my address" flow
 *
 * Composes NdilaAddressSearchBox + NdilaCoordinateInput so consumers don't
 * have to wire up provider selection themselves.
 */

import { useState } from "react";
import { NdilaAddressSearchBox } from "./NdilaAddressSearchBox";
import { NdilaCoordinateInput } from "./NdilaCoordinateInput";
import type { NdilaCoordinate, NdilaGeocodeResult } from "@/lib/ndila/ndila-client";

interface Props {
  value?: NdilaCoordinate | null;
  onChange?: (next: NdilaCoordinate | null, meta?: { source: "ADDRESS_SEARCH" | "MANUAL" | "DEVICE_GPS"; result?: NdilaGeocodeResult; accuracyMeters?: number }) => void;
  country?: string;
  purposeOfUse?: string;
  allowAddressSearch?: boolean;
  allowManualCoordinates?: boolean;
  allowCurrentDeviceLocation?: boolean;
  verificationRequired?: boolean;
}

export function NdilaLocationPicker({
  value, onChange, country = "ZWE", purposeOfUse,
  allowAddressSearch = true,
  allowManualCoordinates = true,
  allowCurrentDeviceLocation = true,
  verificationRequired = false,
}: Props) {
  const [selected, setSelected] = useState<NdilaCoordinate | null>(value ?? null);

  const propagate = (next: NdilaCoordinate | null, meta: Parameters<NonNullable<Props["onChange"]>>[1]) => {
    setSelected(next);
    onChange?.(next, meta);
  };

  return (
    <div className="space-y-3">
      {allowAddressSearch && (
        <NdilaAddressSearchBox
          country={country}
          purposeOfUse={purposeOfUse}
          onSelect={(r) => propagate({ latitude: r.latitude, longitude: r.longitude }, { source: "ADDRESS_SEARCH", result: r })}
        />
      )}
      {allowManualCoordinates && (
        <NdilaCoordinateInput
          value={selected}
          country={country}
          allowCurrentDeviceLocation={allowCurrentDeviceLocation}
          onChange={(next, accuracy) =>
            propagate(next, { source: next && accuracy ? "DEVICE_GPS" : "MANUAL", accuracyMeters: accuracy })
          }
        />
      )}
      {verificationRequired && selected && (
        <p className="text-[10px] text-warning-foreground">
          Coordinate captured. Field verification still required before this location is marked verified.
        </p>
      )}
    </div>
  );
}
