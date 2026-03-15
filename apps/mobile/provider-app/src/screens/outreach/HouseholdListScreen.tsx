/**
 * HouseholdListScreen — Household register with GPS-tagged entries.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  TextField,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { getHouseholds, registerHousehold, recordCommunityVisit } from "../../services/householdService";
import { useAppStore } from "../../stores/appStore";
import { useOfflineStore } from "@impilo/mobile-offline";
import type { Household } from "../../types";

export function HouseholdListScreen() {
  const { facilityId, isOnline } = useAppStore();
  const [households, setHouseholds] = useState<Household[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showRegister, setShowRegister] = useState(false);
  const [registerForm, setRegisterForm] = useState({
    headOfHousehold: "",
    address: "",
  });
  const [saving, setSaving] = useState(false);

  // Offline store for households
  const { data: offlineHouseholds } = useOfflineStore<Household>("households");

  const loadHouseholds = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await getHouseholds(facilityId, 0, 100);
      setHouseholds(result.households);
    } catch (err) {
      // Fall back to offline data
      if (offlineHouseholds.length > 0) {
        setHouseholds(offlineHouseholds);
      } else {
        setError(err instanceof Error ? err.message : "Failed to load households");
      }
    } finally {
      setLoading(false);
    }
  }, [facilityId, offlineHouseholds]);

  useEffect(() => {
    loadHouseholds();
  }, [loadHouseholds]);

  const handleRegister = useCallback(async () => {
    if (!facilityId || !registerForm.headOfHousehold || !registerForm.address) return;
    setSaving(true);
    setError(null);
    try {
      const household = await registerHousehold({
        headOfHousehold: registerForm.headOfHousehold,
        address: registerForm.address,
        gpsLatitude: 0,
        gpsLongitude: 0,
        facilityId,
        members: [],
      });
      setHouseholds((prev) => [household, ...prev]);
      setShowRegister(false);
      setRegisterForm({ headOfHousehold: "", address: "" });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setSaving(false);
    }
  }, [facilityId, registerForm]);

  const handleStartVisit = useCallback(async (household: Household) => {
    try {
      await recordCommunityVisit({
        householdId: household.id,
        visitType: "GENERAL",
        gpsLatitude: household.gpsLatitude,
        gpsLongitude: household.gpsLongitude,
      });
      loadHouseholds();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to start visit");
    }
  }, [loadHouseholds]);

  const filtered = households.filter(
    (h) =>
      !search ||
      h.headOfHousehold.toLowerCase().includes(search.toLowerCase()) ||
      h.address.toLowerCase().includes(search.toLowerCase())
  );

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Households" }),
    React.createElement(
      "div",
      { "data-testid": "household-list-screen", style: { padding: "16px" } },

      React.createElement(
        "div",
        { style: { display: "flex", gap: "8px", marginBottom: "16px" } },
        React.createElement(TextField, {
          label: "Search",
          value: search,
          onChange: setSearch,
          placeholder: "Search households...",
          testID: "household-search",
        }),
        React.createElement(Button, {
          title: "Register New",
          variant: "primary",
          onPress: () => setShowRegister(true),
          testID: "register-household-btn",
        })
      ),

      showRegister
        ? React.createElement(
            Card,
            null,
            React.createElement(CardBody, null,
              React.createElement(TextField, {
                label: "Head of Household",
                value: registerForm.headOfHousehold,
                onChange: (v: string) => setRegisterForm((f) => ({ ...f, headOfHousehold: v })),
                testID: "hh-head",
              }),
              React.createElement(TextField, {
                label: "Address",
                value: registerForm.address,
                onChange: (v: string) => setRegisterForm((f) => ({ ...f, address: v })),
                testID: "hh-address",
              }),
              React.createElement("div", { style: { display: "flex", gap: "8px", marginTop: "12px" } },
                React.createElement(Button, { title: "Save", onPress: handleRegister, loading: saving, testID: "save-household-btn" }),
                React.createElement(Button, { title: "Cancel", variant: "ghost", onPress: () => setShowRegister(false) })
              )
            )
          )
        : null,

      loading
        ? React.createElement(LoadingSpinner, { size: "md" })
        : error
          ? React.createElement(ErrorState, { title: "Error", message: error, onRetry: loadHouseholds })
          : filtered.length === 0
            ? React.createElement(EmptyState, { title: "No households", message: "Register a new household to begin" })
            : filtered.map((hh) =>
                React.createElement(
                  Card,
                  { key: hh.id },
                  React.createElement(CardBody, null,
                    React.createElement("div", { "data-testid": `household-${hh.id}`, style: { display: "flex", justifyContent: "space-between" } },
                      React.createElement("div", null,
                        React.createElement("strong", null, hh.headOfHousehold),
                        React.createElement("p", { style: { fontSize: "14px", color: "#6B7280" } }, hh.address),
                        React.createElement("div", { style: { display: "flex", gap: "4px", marginTop: "4px" } },
                          React.createElement(Badge, { variant: "outline", children: `${hh.members.length} members` }),
                          React.createElement(Badge, {
                            variant: hh.riskCategory === "HIGH" ? "destructive" : hh.riskCategory === "MEDIUM" ? "secondary" : "outline",
                            children: hh.riskCategory,
                          }),
                          hh.lastVisitDate
                            ? React.createElement(Badge, { variant: "outline", children: `Last: ${new Date(hh.lastVisitDate).toLocaleDateString()}` })
                            : null
                        )
                      ),
                      React.createElement(Button, {
                        title: "Visit",
                        variant: "primary",
                        size: "sm",
                        onPress: () => handleStartVisit(hh),
                        testID: `visit-hh-${hh.id}`,
                      })
                    )
                  )
                )
              )
    )
  );
}
