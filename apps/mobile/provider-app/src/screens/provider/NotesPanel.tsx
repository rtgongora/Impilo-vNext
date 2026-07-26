/**
 * NotesPanel — Clinical notes within an encounter.
 */

import React, { useState, useCallback } from "react";
import { View, Text, TextInput, StyleSheet } from "react-native";
import { Card, CardBody, Button, ErrorState, colors } from "@impilo/mobile-design-system";
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

  return (
    <View testID="notes-panel">
      <Card>
        <CardBody>
          <TextInput
            value={notes}
            onChangeText={setNotes}
            placeholder="Enter clinical notes..."
            testID="notes-textarea"
            multiline
            textAlignVertical="top"
            style={styles.textArea}
          />
          <View style={styles.actionRow}>
            <Button
              title="Save Notes"
              onPress={handleSave}
              loading={saving}
              testID="save-notes-btn"
            />
            {saved ? <Text style={styles.savedText}>Saved</Text> : null}
          </View>
          {error ? <ErrorState title="Error" message={error} /> : null}
        </CardBody>
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  textArea: {
    width: "100%",
    minHeight: 200,
    padding: 12,
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    fontSize: 14,
  },
  actionRow: {
    marginTop: 12,
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
  },
  savedText: {
    color: "#009739",
    fontSize: 14,
  },
});
