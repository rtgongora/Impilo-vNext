"use client";

import { useMemo } from "react";
import countriesDoc from "@registry-templates/terminology/countries.iso3166-1.json";
import {
  buildCountryPickerGroups,
  defaultDeployRegion,
  type DeployRegion,
  type IsoCountryRow,
} from "@/lib/registry/iso3166";

type Props = {
  value: string;
  onChange: (alpha2: string) => void;
  deploy?: DeployRegion;
  className?: string;
};

export function CountryPicker({ value, onChange, deploy, className }: Props) {
  const region = deploy ?? defaultDeployRegion();
  const groups = useMemo(
    () => buildCountryPickerGroups(countriesDoc.countries as IsoCountryRow[], region),
    [region],
  );

  if (region === "ZW") {
    return (
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={className ?? "w-full px-3 py-2 border rounded-md text-sm"}
      >
        <option value="">Country…</option>
        <optgroup label="Zimbabwe (primary)">
          {groups.pinned.map((c) => (
            <option key={c.alpha2} value={c.alpha2}>
              {c.name} ({c.alpha2} / {c.alpha3} / {c.numeric})
            </option>
          ))}
        </optgroup>
        <optgroup label="Common nearby">
          {groups.nearby.map((c) => (
            <option key={c.alpha2} value={c.alpha2}>
              {c.name}
            </option>
          ))}
        </optgroup>
        <optgroup label="All countries (A–Z)">
          {groups.rest.map((c) => (
            <option key={c.alpha2} value={c.alpha2}>
              {c.name}
            </option>
          ))}
        </optgroup>
      </select>
    );
  }

  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={className ?? "w-full px-3 py-2 border rounded-md text-sm"}
    >
      <option value="">Country…</option>
      <optgroup label="Countries (A–Z)">
        {groups.rest.map((c) => (
          <option key={c.alpha2} value={c.alpha2}>
            {c.name}
          </option>
        ))}
      </optgroup>
    </select>
  );
}
