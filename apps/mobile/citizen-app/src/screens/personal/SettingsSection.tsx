/**
 * SettingsSection — Consent management, notification preferences, and account actions.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet } from "react-native";
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

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  return (
    <View testID="settings-section" style={styles.container}>
      {/* Consent preferences */}
      <Card>
        <CardHeader title="Privacy & Consent" />
        <CardBody>
          {consents.length === 0 ? (
            <Text style={styles.emptyText}>No consent preferences configured</Text>
          ) : (
            consents.map((consent) => (
              <View
                key={consent.id}
                testID={`consent-${consent.id}`}
                style={styles.consentRow}
              >
                <View>
                  <Text style={styles.consentCategory}>{consent.category}</Text>
                  <Text style={styles.consentDescription}>{consent.description}</Text>
                </View>
                <Switch
                  checked={consent.granted}
                  onChange={(v: boolean) => handleConsentToggle(consent.id, v)}
                  accessibilityLabel={`Toggle ${consent.category} consent`}
                />
              </View>
            ))
          )}
        </CardBody>
      </Card>

      {/* Notification preferences */}
      <Card>
        <CardHeader title="Notification Preferences" />
        <CardBody>
          {notifPrefs.length === 0 ? (
            <Text style={styles.emptyText}>No notification preferences available</Text>
          ) : (
            notifPrefs.map((pref) => (
              <View
                key={pref.category}
                testID={`notif-pref-${pref.category}`}
                style={styles.notifPrefRow}
              >
                <Text style={styles.notifPrefCategory}>{pref.category}</Text>
                <View style={styles.notifSwitchRow}>
                  <View style={styles.notifSwitchItem}>
                    <Switch
                      checked={pref.pushEnabled}
                      onChange={(v: boolean) => handleNotifPrefToggle(pref.category, "pushEnabled", v)}
                    />
                    <Text style={styles.notifSwitchLabel}>Push</Text>
                  </View>
                  <View style={styles.notifSwitchItem}>
                    <Switch
                      checked={pref.inAppEnabled}
                      onChange={(v: boolean) => handleNotifPrefToggle(pref.category, "inAppEnabled", v)}
                    />
                    <Text style={styles.notifSwitchLabel}>In-App</Text>
                  </View>
                </View>
              </View>
            ))
          )}
        </CardBody>
      </Card>

      {/* Account actions */}
      <Card>
        <CardHeader title="Account" />
        <CardBody>
          <View style={styles.accountContainer}>
            <Button
              title="Sign Out"
              variant="secondary"
              onPress={() => auth.logout()}
              testID="sign-out"
            />
            {showDeleteConfirm ? (
              <View style={styles.deleteConfirmContainer}>
                <Text style={styles.deleteWarningText}>
                  This will permanently delete your account and all associated data. This action cannot be undone.
                </Text>
                <View style={styles.deleteButtonRow}>
                  <Button
                    title="Confirm Delete"
                    variant="primary"
                    onPress={handleDeleteAccount}
                    testID="confirm-delete-account"
                  />
                  <Button
                    title="Cancel"
                    variant="ghost"
                    onPress={() => setShowDeleteConfirm(false)}
                  />
                </View>
              </View>
            ) : (
              <Button
                title="Delete Account"
                variant="ghost"
                onPress={() => setShowDeleteConfirm(true)}
                testID="delete-account"
              />
            )}
          </View>
        </CardBody>
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  emptyText: {
    color: "#6B7280",
    fontSize: 14,
  },
  consentRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#F3F4F6",
  },
  consentCategory: {
    fontSize: 14,
    fontWeight: "700",
  },
  consentDescription: {
    fontSize: 13,
    color: "#6B7280",
    marginTop: 2,
  },
  notifPrefRow: {
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#F3F4F6",
  },
  notifPrefCategory: {
    fontSize: 14,
    fontWeight: "700",
    marginBottom: 8,
  },
  notifSwitchRow: {
    flexDirection: "row",
    gap: 24,
  },
  notifSwitchItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  notifSwitchLabel: {
    fontSize: 13,
  },
  accountContainer: {
    gap: 12,
  },
  deleteConfirmContainer: {
    padding: 12,
    backgroundColor: "#FEE2E2",
    borderRadius: 8,
  },
  deleteWarningText: {
    color: "#991B1B",
    fontSize: 14,
    marginBottom: 8,
  },
  deleteButtonRow: {
    flexDirection: "row",
    gap: 8,
  },
});
