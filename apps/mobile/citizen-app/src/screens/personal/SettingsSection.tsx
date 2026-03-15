/**
 * SettingsSection — Consent management, notification preferences, and account actions.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Card,
  CardHeader,
  CardBody,
  Button,
  Switch,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useAuth } from "@impilo/mobile-auth";
import { getPreferences, updatePreference, type NotificationPreference } from "@impilo/mobile-messaging";
import { fetchConsents, updateConsent, deleteAccount } from "../../services/profileService";
import type { ConsentPreference } from "../../types";

export function SettingsSection() {
  const auth = useAuth();
  const [consents, setConsents] = useState<ConsentPreference[]>([]);
  const [notifPrefs, setNotifPrefs] = useState<NotificationPreference[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [c, n] = await Promise.all([
        fetchConsents(),
        getPreferences(),
      ]);
      setConsents(c);
      setNotifPrefs(n);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleConsentToggle = useCallback(async (id: string, granted: boolean) => {
    try {
      const updated = await updateConsent(id, granted);
      setConsents((prev) => prev.map((c) => (c.id === id ? updated : c)));
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, []);

  const handleNotifPrefToggle = useCallback(async (category: string, field: "pushEnabled" | "inAppEnabled", value: boolean) => {
    try {
      await updatePreference({ category: category as NotificationPreference["category"], [field]: value });
      setNotifPrefs((prev) =>
        prev.map((p) => (p.category === category ? { ...p, [field]: value } : p))
      );
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, []);

  const handleDeleteAccount = useCallback(async () => {
    try {
      await deleteAccount();
      auth.logout();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [auth]);

  if (isLoading) return React.createElement(LoadingSpinner, { size: "md" });
  if (error) return React.createElement(ErrorState, { title: "Error", message: error.message, onRetry: load });

  return React.createElement(
    "div",
    { "data-testid": "settings-section", style: { display: "flex", flexDirection: "column", gap: "16px" } },

    // Consent preferences
    React.createElement(
      Card,
      null,
      React.createElement(CardHeader, { title: "Privacy & Consent" }),
      React.createElement(
        CardBody,
        null,
        consents.length === 0
          ? React.createElement("p", { style: { color: "#6B7280", fontSize: "14px" } }, "No consent preferences configured")
          : consents.map((consent) =>
              React.createElement(
                "div",
                {
                  key: consent.id,
                  "data-testid": `consent-${consent.id}`,
                  style: { display: "flex", justifyContent: "space-between", alignItems: "center", padding: "8px 0", borderBottom: "1px solid #F3F4F6" },
                },
                React.createElement(
                  "div",
                  null,
                  React.createElement("strong", { style: { fontSize: "14px" } }, consent.category),
                  React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0 0" } }, consent.description)
                ),
                React.createElement(Switch, {
                  checked: consent.granted,
                  onChange: (v: boolean) => handleConsentToggle(consent.id, v),
                  accessibilityLabel: `Toggle ${consent.category} consent`,
                })
              )
            )
      )
    ),

    // Notification preferences
    React.createElement(
      Card,
      null,
      React.createElement(CardHeader, { title: "Notification Preferences" }),
      React.createElement(
        CardBody,
        null,
        notifPrefs.length === 0
          ? React.createElement("p", { style: { color: "#6B7280", fontSize: "14px" } }, "No notification preferences available")
          : notifPrefs.map((pref) =>
              React.createElement(
                "div",
                {
                  key: pref.category,
                  "data-testid": `notif-pref-${pref.category}`,
                  style: { padding: "8px 0", borderBottom: "1px solid #F3F4F6" },
                },
                React.createElement("strong", { style: { fontSize: "14px", display: "block", marginBottom: "8px" } }, pref.category),
                React.createElement(
                  "div",
                  { style: { display: "flex", gap: "24px" } },
                  React.createElement(
                    "label",
                    { style: { display: "flex", alignItems: "center", gap: "8px", fontSize: "13px" } },
                    React.createElement(Switch, {
                      checked: pref.pushEnabled,
                      onChange: (v: boolean) => handleNotifPrefToggle(pref.category, "pushEnabled", v),
                    }),
                    "Push"
                  ),
                  React.createElement(
                    "label",
                    { style: { display: "flex", alignItems: "center", gap: "8px", fontSize: "13px" } },
                    React.createElement(Switch, {
                      checked: pref.inAppEnabled,
                      onChange: (v: boolean) => handleNotifPrefToggle(pref.category, "inAppEnabled", v),
                    }),
                    "In-App"
                  )
                )
              )
            )
      )
    ),

    // Account actions
    React.createElement(
      Card,
      null,
      React.createElement(CardHeader, { title: "Account" }),
      React.createElement(
        CardBody,
        null,
        React.createElement(
          "div",
          { style: { display: "flex", flexDirection: "column", gap: "12px" } },
          React.createElement(Button, {
            title: "Sign Out",
            variant: "secondary",
            onPress: () => auth.logout(),
            testID: "sign-out",
          }),
          showDeleteConfirm
            ? React.createElement(
                "div",
                { style: { padding: "12px", backgroundColor: "#FEE2E2", borderRadius: "8px" } },
                React.createElement("p", { style: { color: "#991B1B", fontSize: "14px", marginBottom: "8px" } },
                  "This will permanently delete your account and all associated data. This action cannot be undone."
                ),
                React.createElement(
                  "div",
                  { style: { display: "flex", gap: "8px" } },
                  React.createElement(Button, {
                    title: "Confirm Delete",
                    variant: "primary",
                    onPress: handleDeleteAccount,
                    testID: "confirm-delete-account",
                  }),
                  React.createElement(Button, {
                    title: "Cancel",
                    variant: "ghost",
                    onPress: () => setShowDeleteConfirm(false),
                  })
                )
              )
            : React.createElement(Button, {
                title: "Delete Account",
                variant: "ghost",
                onPress: () => setShowDeleteConfirm(true),
                testID: "delete-account",
              })
        )
      )
    )
  );
}
