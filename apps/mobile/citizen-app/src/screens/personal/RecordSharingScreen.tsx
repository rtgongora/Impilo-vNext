import React, { useEffect, useState } from "react";

import { View, Text, StyleSheet, ActivityIndicator, Share } from "react-native";

import { Button, Card, CardBody, TextField, Badge } from "@impilo/mobile-design-system";

import { appStore, useAppStore } from "../../stores/appStore";

import { enqueueCitizenLongtailPost, issueShareCode } from "../../services/citizenLongtailService";

import { ApiError, NetworkError } from "@impilo/mobile-api-client";

import { loadLastShareIssue, saveLastShareIssue, type CachedShareIssue } from "../../services/citizenLongtailLocalCache";

import { formatWhen } from "../../lib/formatWhen";

/**

 * RecordSharingScreen — share code via experience-bff (`/share/issue`).

 * Maps to web: /citizen/record-sharing

 */

export function RecordSharingScreen() {

  const isOnline = useAppStore((s) => s.isOnline);

  const [purpose, setPurpose] = useState("");

  const [shareCode, setShareCode] = useState("");

  const [issued, setIssued] = useState(false);

  const [queuedOffline, setQueuedOffline] = useState(false);

  const [loading, setLoading] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const [lastSaved, setLastSaved] = useState<CachedShareIssue | null>(null);



  useEffect(() => {

    void (async () => {

      const prev = await loadLastShareIssue();

      if (prev?.code) setLastSaved(prev);

    })();

  }, []);



  const shareOut = async () => {

    if (!shareCode.trim()) return;

    try {

      await Share.share({

        message: `Impilo share code: ${shareCode.trim()}${purpose.trim() ? `\nPurpose: ${purpose.trim()}` : ""}`,

      });

    } catch {

      /* user dismissed */

    }

  };



  const issue = async () => {

    setError(null);

    setLoading(true);

    setQueuedOffline(false);

    try {

      if (!appStore.getState().isOnline) {

        await enqueueCitizenLongtailPost(

          "/internal/v1/mobile/citizen/share/issue",

          { purpose: purpose.trim() || null },

          "citizen_longtail_share_issue"

        );

        setQueuedOffline(true);

        setIssued(false);

        setShareCode("");

        return;

      }

      const data = await issueShareCode(purpose.trim() || null);

      setShareCode(data.code);

      setIssued(true);

      await saveLastShareIssue({

        code: data.code,

        issued_at: data.issued_at,

        purpose: (data.purpose ?? purpose.trim()) || null,

      });

      setLastSaved({

        code: data.code,

        issued_at: data.issued_at,

        purpose: (data.purpose ?? purpose.trim()) || null,

      });

    } catch (e) {

      if (e instanceof NetworkError) {

        setError("Network error. Try again or wait until you are back online.");

      } else if (e instanceof ApiError) {

        setError(e.message);

      } else {

        setError("Something went wrong. Please try again.");

      }

    } finally {

      setLoading(false);

    }

  };



  return (

    <View testID="record-sharing-screen" style={styles.container}>

      <Card>

        <CardBody>

          <Text style={styles.title}>Share My Record</Text>

          <Text style={styles.sub}>

            Generate a share code you can give to a provider or caregiver to claim shared documents.

          </Text>

          {!isOnline && (

            <Text style={styles.hint}>You are offline — issuing will queue and send when you reconnect.</Text>

          )}

          {lastSaved && !issued && !queuedOffline ? (

            <Text style={styles.cacheNote} testID="last-share-issue-note">

              Last code on this device: {lastSaved.code} ({formatWhen(lastSaved.issued_at)})

            </Text>

          ) : null}

          {queuedOffline ? (

            <Badge variant="warning">Queued for sync</Badge>

          ) : issued ? (

            <Badge variant="success">Issued</Badge>

          ) : (

            <Badge variant="outline">Not issued</Badge>

          )}

        </CardBody>

      </Card>



      <Card>

        <CardBody>

          <Text style={styles.label}>Purpose (optional)</Text>

          <TextField

            value={purpose}

            onChange={setPurpose}

            placeholder="e.g. Specialist visit"

            testID="share-purpose"

          />

          <Text style={styles.label}>Share code</Text>

          <TextField

            value={shareCode}

            onChange={setShareCode}

            placeholder="Issued code appears here"

            disabled

            testID="share-code"

          />

          {error ? <Text style={styles.error}>{error}</Text> : null}

          <Button

            title={issued ? "Re-issue code" : queuedOffline ? "Queue again" : "Issue share code"}

            onPress={() => void issue()}

            disabled={loading}

            testID="issue-share-code"

          />

          {issued && shareCode.trim() ? (

            <Button title="Share code" variant="outline" onPress={() => void shareOut()} testID="share-code-native-share" />

          ) : null}

          {loading ? <ActivityIndicator style={styles.spinner} /> : null}

        </CardBody>

      </Card>

    </View>

  );

}



const styles = StyleSheet.create({

  container: { gap: 12 },

  title: { fontSize: 16, fontWeight: "800", color: "#111827" },

  sub: { fontSize: 13, color: "#6B7280", marginTop: 6 },

  hint: { fontSize: 12, color: "#B45309", marginTop: 8 },

  cacheNote: { fontSize: 12, color: "#4B5563", marginTop: 8 },

  label: { fontSize: 12, fontWeight: "700", color: "#374151", marginBottom: 6, marginTop: 8 },

  error: { color: "#B91C1C", fontSize: 13, marginTop: 8 },

  spinner: { marginTop: 8 },

});

