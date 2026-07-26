/**
 * RemindersScreen — Manage medication and appointment reminders.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import { Card, CardHeader, CardBody, Button, Badge, TextField, Select, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { getReminders, createReminder, updateReminder, deleteReminder } from "../../services/remindersService";
import type { Reminder, ReminderType, Recurrence } from "../../types";

const TYPE_COLORS: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  MEDICATION: "default",
  APPOINTMENT: "secondary",
  LAB_CHECK: "destructive",
  FOLLOW_UP: "outline",
};

export function RemindersScreen() {
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Form state
  const [title, setTitle] = useState("");
  const [type, setType] = useState<ReminderType>("MEDICATION");
  const [description, setDescription] = useState("");
  const [dateTime, setDateTime] = useState("");
  const [recurrence, setRecurrence] = useState<Recurrence>("ONCE");

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await getReminders({ size: 50 });
      setReminders(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const resetForm = useCallback(() => {
    setTitle("");
    setType("MEDICATION");
    setDescription("");
    setDateTime("");
    setRecurrence("ONCE");
  }, []);

  const handleCreate = useCallback(async () => {
    setSubmitting(true);
    try {
      await createReminder({
        title,
        type,
        description: description || undefined,
        dateTime,
        recurrence,
      });
      setShowForm(false);
      resetForm();
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [title, type, description, dateTime, recurrence, load, resetForm]);

  const handleToggle = useCallback(async (reminder: Reminder) => {
    try {
      await updateReminder(reminder.id, { enabled: !reminder.enabled });
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [load]);

  const handleDelete = useCallback(async (id: string) => {
    try {
      await deleteReminder(id);
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [load]);

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  return (
    <ScrollView testID="reminders-screen" style={styles.scrollView} showsVerticalScrollIndicator={false}>
      <View style={styles.container}>
        <View style={styles.headerRow}>
          <Text style={styles.heading}>Reminders</Text>
          <Button
            title={showForm ? "Cancel" : "Add Reminder"}
            variant={showForm ? "ghost" : "primary"}
            size="sm"
            onPress={() => {
              setShowForm(!showForm);
              if (showForm) resetForm();
            }}
            testID="toggle-reminder-form"
          />
        </View>

        {showForm ? (
          <Card>
            <CardHeader title="New Reminder" />
            <CardBody>
              <View style={styles.formContainer}>
                <TextField
                  label="Title"
                  value={title}
                  onChange={setTitle}
                  placeholder="Reminder title"
                  testID="reminder-title"
                />
                <Select
                  label="Type"
                  value={type}
                  onChange={(v) => setType(v as ReminderType)}
                  options={[
                    { label: "Medication", value: "MEDICATION" },
                    { label: "Appointment", value: "APPOINTMENT" },
                    { label: "Lab Check", value: "LAB_CHECK" },
                    { label: "Follow-Up", value: "FOLLOW_UP" },
                  ]}
                  testID="reminder-type"
                />
                <TextField
                  label="Description (optional)"
                  value={description}
                  onChange={setDescription}
                  placeholder="Brief description"
                  testID="reminder-description"
                />
                <TextField
                  label="Date & Time"
                  value={dateTime}
                  onChange={setDateTime}
                  placeholder="YYYY-MM-DD HH:MM"
                  testID="reminder-datetime"
                />
                <Select
                  label="Recurrence"
                  value={recurrence}
                  onChange={(v) => setRecurrence(v as Recurrence)}
                  options={[
                    { label: "Once", value: "ONCE" },
                    { label: "Daily", value: "DAILY" },
                    { label: "Weekly", value: "WEEKLY" },
                  ]}
                  testID="reminder-recurrence"
                />
                <Button
                  title={submitting ? "Creating..." : "Create Reminder"}
                  variant="primary"
                  onPress={handleCreate}
                  disabled={submitting || !title || !dateTime}
                  testID="submit-reminder"
                />
              </View>
            </CardBody>
          </Card>
        ) : null}

        {reminders.length === 0 ? (
          <EmptyState
            title="No reminders"
            message="Create your first reminder using the button above"
          />
        ) : (
          reminders.map((reminder) => (
            <Card key={reminder.id}>
              <CardBody>
                <View testID={`reminder-${reminder.id}`} style={styles.reminderRow}>
                  <View style={styles.reminderContent}>
                    <View style={styles.badgeRow}>
                      <Text style={styles.reminderTitle}>{reminder.title}</Text>
                      <Badge variant={TYPE_COLORS[reminder.type] ?? "outline"}>
                        {reminder.type.replace(/_/g, " ")}
                      </Badge>
                    </View>
                    <Text style={styles.dateTimeText}>
                      {new Date(reminder.dateTime).toLocaleString()}
                    </Text>
                    <Text style={styles.recurrenceText}>
                      {`Repeats: ${reminder.recurrence}`}
                    </Text>
                    {reminder.description ? (
                      <Text style={styles.descriptionText}>{reminder.description}</Text>
                    ) : null}
                    <Text style={[styles.statusText, !reminder.enabled && styles.statusDisabled]}>
                      {reminder.enabled ? "Active" : "Paused"}
                    </Text>
                  </View>
                  <View style={styles.actionColumn}>
                    <Pressable
                      testID={`toggle-reminder-${reminder.id}`}
                      onPress={() => handleToggle(reminder)}
                      style={[
                        styles.toggleButton,
                        reminder.enabled ? styles.toggleOn : styles.toggleOff,
                      ]}
                    >
                      <Text style={styles.toggleText}>
                        {reminder.enabled ? "ON" : "OFF"}
                      </Text>
                    </Pressable>
                    <Button
                      title="Delete"
                      variant="ghost"
                      size="sm"
                      onPress={() => handleDelete(reminder.id)}
                      testID={`delete-reminder-${reminder.id}`}
                    />
                  </View>
                </View>
              </CardBody>
            </Card>
          ))
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    gap: 12,
  },
  headerRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  heading: {
    fontSize: 18,
    fontWeight: "600",
  },
  formContainer: {
    gap: 12,
  },
  reminderRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  reminderContent: {
    flex: 1,
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginBottom: 4,
    flexWrap: "wrap",
  },
  reminderTitle: {
    fontWeight: "700",
    fontSize: 15,
  },
  dateTimeText: {
    fontSize: 14,
    color: colors.gray[700],
    marginVertical: 2,
  },
  recurrenceText: {
    fontSize: 13,
    color: colors.gray[500],
    marginVertical: 2,
  },
  descriptionText: {
    fontSize: 13,
    color: colors.gray[400],
    marginVertical: 2,
  },
  statusText: {
    fontSize: 12,
    fontWeight: "600",
    color: "#10B981",
    marginTop: 4,
  },
  statusDisabled: {
    color: colors.gray[400],
  },
  actionColumn: {
    alignItems: "center",
    gap: 8,
  },
  toggleButton: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 12,
    minWidth: 44,
    alignItems: "center",
  },
  toggleOn: {
    backgroundColor: "#10B981",
  },
  toggleOff: {
    backgroundColor: colors.gray[300],
  },
  toggleText: {
    fontSize: 12,
    fontWeight: "700",
    color: "#FFFFFF",
  },
});
