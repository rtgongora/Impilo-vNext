/**
 * NotesPanel — Clinical notes within an encounter.
 */

import React, { useState, useCallback } from "react";
import { Card, CardBody, Button, TextField, ErrorState } from "@impilo/mobile-design-system";
import { addEncounterNotes } from "../../services/encounterService";
import { useEncounterStore } from "../../stores/encounterStore";

interface NotesPanelProps {
  encounterId: string;
}

export function NotesPanel({ encounterId }: NotesPanelProps) {
  const { activeEncounter } = useEncounterStore();
  const [notes, setNotes] = useState(activeEncounter?.notes ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const handleSave = useCallback(async () => {
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      await addEncounterNotes(encounterId, notes);
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save notes");
    } finally {
      setSaving(false);
    }
  }, [encounterId, notes]);

  return React.createElement(
    "div",
    { "data-testid": "notes-panel" },
    React.createElement(
      Card,
      null,
      React.createElement(
        CardBody,
        null,
        React.createElement("textarea", {
          value: notes,
          onChange: (e: React.ChangeEvent<HTMLTextAreaElement>) => setNotes(e.target.value),
          placeholder: "Enter clinical notes...",
          "data-testid": "notes-textarea",
          style: {
            width: "100%",
            minHeight: "200px",
            padding: "12px",
            border: "1px solid #D1D5DB",
            borderRadius: "8px",
            fontSize: "14px",
            fontFamily: "inherit",
            resize: "vertical",
          },
        }),
        React.createElement(
          "div",
          { style: { marginTop: "12px", display: "flex", gap: "8px", alignItems: "center" } },
          React.createElement(Button, {
            title: "Save Notes",
            onPress: handleSave,
            loading: saving,
            testID: "save-notes-btn",
          }),
          saved ? React.createElement("span", { style: { color: "#059669", fontSize: "14px" } }, "Saved") : null
        ),
        error ? React.createElement(ErrorState, { title: "Error", message: error }) : null
      )
    )
  );
}
