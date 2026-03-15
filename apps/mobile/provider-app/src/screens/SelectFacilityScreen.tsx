/**
 * SelectFacilityScreen — Facility selection after authentication.
 *
 * Fetches facilities from indawo-service via BFF and lets the user
 * pick their working facility before entering the app.
 */

import React, { useState, useEffect, useCallback } from "react";
import { apiClient } from "@impilo/mobile-api-client";
import { useAuth } from "@impilo/mobile-auth";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  TextField,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { appStore } from "../stores/appStore";

interface Facility {
  id: string;
  name: string;
  facilityType: string;
  district: string;
  province: string;
}

export function SelectFacilityScreen() {
  const auth = useAuth();
  const [facilities, setFacilities] = useState<Facility[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchFacilities = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = search ? `?search=${encodeURIComponent(search)}` : "";
      const response = await apiClient.get<{
        data: {
          id: string;
          attributes: {
            name: string;
            facility_type: string;
            district: string;
            province: string;
          };
        }[];
      }>(`/internal/v1/facilities${params}`);
      setFacilities(
        response.data.data.map((f) => ({
          id: f.id,
          name: f.attributes.name,
          facilityType: f.attributes.facility_type,
          district: f.attributes.district,
          province: f.attributes.province,
        }))
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load facilities");
    } finally {
      setLoading(false);
    }
  }, [search]);

  useEffect(() => {
    fetchFacilities();
  }, [fetchFacilities]);

  const handleSelectFacility = useCallback(
    (facility: Facility) => {
      appStore.getState().setFacilityContext(facility.id, facility.name);
      auth.setFacility(facility.id, facility.name);
    },
    [auth]
  );

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Select Facility" }),
    React.createElement(
      "div",
      { "data-testid": "select-facility-screen", style: { padding: "16px" } },
      React.createElement(TextField, {
        label: "Search facilities",
        value: search,
        onChange: setSearch,
        placeholder: "Type facility name...",
        testID: "facility-search",
      }),
      React.createElement(
        "div",
        { style: { marginTop: "16px" } },
        loading
          ? React.createElement(LoadingSpinner, { size: "md" })
          : error
            ? React.createElement(ErrorState, {
                title: "Error",
                message: error,
                onRetry: fetchFacilities,
              })
            : facilities.length === 0
              ? React.createElement(EmptyState, {
                  title: "No facilities found",
                  message: "Try a different search term",
                })
              : facilities.map((f) =>
                  React.createElement(
                    Card,
                    { key: f.id },
                    React.createElement(
                      CardBody,
                      null,
                      React.createElement(
                        "div",
                        {
                          style: {
                            display: "flex",
                            justifyContent: "space-between",
                            alignItems: "center",
                          },
                        },
                        React.createElement(
                          "div",
                          null,
                          React.createElement(
                            "strong",
                            null,
                            f.name
                          ),
                          React.createElement(
                            "p",
                            { style: { fontSize: "14px", color: "#6B7280", margin: "4px 0 0" } },
                            `${f.facilityType} · ${f.district}, ${f.province}`
                          )
                        ),
                        React.createElement(Button, {
                          title: "Select",
                          variant: "primary",
                          size: "sm",
                          onPress: () => handleSelectFacility(f),
                          testID: `select-facility-${f.id}`,
                        })
                      )
                    )
                  )
                )
      )
    )
  );
}
