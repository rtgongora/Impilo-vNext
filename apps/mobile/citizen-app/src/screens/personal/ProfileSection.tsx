/**
 * ProfileSection — View and edit citizen profile.
 */

import React, { useState, useCallback } from "react";
import {
  Card,
  CardHeader,
  CardBody,
  Button,
  TextField,
  Avatar,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useAppStore, appStore } from "../../stores/appStore";
import { updateProfile } from "../../services/profileService";

export function ProfileSection() {
  const { profile } = useAppStore();
  const [editing, setEditing] = useState(false);
  const [phone, setPhone] = useState(profile?.phone ?? "");
  const [email, setEmail] = useState(profile?.email ?? "");
  const [language, setLanguage] = useState(profile?.preferredLanguage ?? "en");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSave = useCallback(async () => {
    setSaving(true);
    setError(null);
    try {
      const updated = await updateProfile({ phone, email, preferredLanguage: language });
      appStore.getState().setProfile(updated);
      setEditing(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save profile");
    } finally {
      setSaving(false);
    }
  }, [phone, email, language]);

  if (!profile) {
    return React.createElement(LoadingSpinner, { size: "md" });
  }

  return React.createElement(
    "div",
    { "data-testid": "profile-section", style: { display: "flex", flexDirection: "column", gap: "16px" } },

    // Profile card
    React.createElement(
      Card,
      null,
      React.createElement(CardHeader, { title: "Personal Information" }),
      React.createElement(
        CardBody,
        null,
        React.createElement(
          "div",
          { style: { display: "flex", alignItems: "center", gap: "16px", marginBottom: "16px" } },
          React.createElement(Avatar, {
            name: `${profile.givenName} ${profile.familyName}`,
            size: "xl",
            src: profile.avatarUrl,
          }),
          React.createElement(
            "div",
            null,
            React.createElement("h3", { style: { margin: 0, fontSize: "18px" } }, `${profile.givenName} ${profile.familyName}`),
            React.createElement("p", { style: { margin: "4px 0 0", color: "#6B7280", fontSize: "14px" } }, `DOB: ${profile.dateOfBirth}`),
            React.createElement("p", { style: { margin: "2px 0 0", color: "#6B7280", fontSize: "14px" } }, `Sex: ${profile.sex}`),
            profile.nationalId
              ? React.createElement("p", { style: { margin: "2px 0 0", color: "#6B7280", fontSize: "14px" } }, `ID: ${profile.nationalId}`)
              : null
          )
        ),

        error
          ? React.createElement(ErrorState, { title: "Error", message: error, onRetry: () => setError(null) })
          : null,

        editing
          ? React.createElement(
              "div",
              { style: { display: "flex", flexDirection: "column", gap: "12px" } },
              React.createElement(TextField, {
                label: "Phone",
                value: phone,
                onChange: setPhone,
                placeholder: "+263...",
                testID: "profile-phone",
              }),
              React.createElement(TextField, {
                label: "Email",
                value: email,
                onChange: setEmail,
                placeholder: "you@example.com",
                testID: "profile-email",
              }),
              React.createElement(TextField, {
                label: "Preferred Language",
                value: language,
                onChange: setLanguage,
                testID: "profile-language",
              }),
              React.createElement(
                "div",
                { style: { display: "flex", gap: "8px" } },
                React.createElement(Button, {
                  title: saving ? "Saving..." : "Save",
                  variant: "primary",
                  onPress: handleSave,
                  disabled: saving,
                  testID: "profile-save",
                }),
                React.createElement(Button, {
                  title: "Cancel",
                  variant: "ghost",
                  onPress: () => setEditing(false),
                  testID: "profile-cancel",
                })
              )
            )
          : React.createElement(
              "div",
              { style: { display: "flex", flexDirection: "column", gap: "8px" } },
              React.createElement("p", { style: { margin: 0, fontSize: "14px" } }, `Phone: ${profile.phone ?? "Not set"}`),
              React.createElement("p", { style: { margin: 0, fontSize: "14px" } }, `Email: ${profile.email ?? "Not set"}`),
              React.createElement("p", { style: { margin: 0, fontSize: "14px" } }, `Language: ${profile.preferredLanguage}`),
              profile.facilityName
                ? React.createElement("p", { style: { margin: 0, fontSize: "14px" } }, `Primary Facility: ${profile.facilityName}`)
                : null,
              React.createElement(Button, {
                title: "Edit Profile",
                variant: "secondary",
                onPress: () => setEditing(true),
                testID: "profile-edit",
              })
            )
      )
    )
  );
}
