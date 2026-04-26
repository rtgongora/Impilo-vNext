import React, { useEffect, useState } from "react";

import { View, Text, StyleSheet, ActivityIndicator, Share } from "react-native";

import { Button, Card, CardBody, TextField, Badge } from "@impilo/mobile-design-system";

import { appStore } from "../../stores/appStore";

import { enqueueCitizenLongtailPost, verifyCredential } from "../../services/citizenLongtailService";

import { ApiError, NetworkError } from "@impilo/mobile-api-client";

import { loadLastCredentialVerify, saveLastCredentialVerify, type CachedCredentialVerify } from "../../services/citizenLongtailLocalCache";

import { formatWhen } from "../../lib/formatWhen";

/**

 * VerifyCredentialScreen — BFF (`/credential/verify`).

 * Maps to web: /verify/credential

 */

export function VerifyCredentialScreen() {

  const [payload, setPayload] = useState("");

  const [verified, setVerified] = useState<null | boolean>(null);

  const [queuedOffline, setQueuedOffline] = useState(false);

  const [loading, setLoading] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const [lastCheck, setLastCheck] = useState<CachedCredentialVerify | null>(null);



  useEffect(() => {

    void (async () => {

      const prev = await loadLastCredentialVerify();

      if (prev) setLastCheck(prev);

    })();

  }, []);



  const shareSummary = async () => {

    if (!lastCheck) return;

    try {

      await Share.share({

        message: `Credential check: ${lastCheck.verified ? "valid" : "not valid"} at ${formatWhen(lastCheck.checked_at)}`,

      });

    } catch {

      /* dismissed */

    }

  };



  const verify = async () => {

    setError(null);

    setLoading(true);

    setQueuedOffline(false);

    setVerified(null);

    try {

      if (!appStore.getState().isOnline) {

        await enqueueCitizenLongtailPost(

          "/internal/v1/mobile/citizen/credential/verify",

          { payload: payload.trim() },

          "citizen_longtail_credential_verify"

        );

        setQueuedOffline(true);

        return;

      }

      const data = await verifyCredential(payload.trim());

      setVerified(data.verified);

      const snap = { verified: data.verified, checked_at: data.checked_at };

      await saveLastCredentialVerify(snap);

      setLastCheck(snap);

    } catch (e) {

      if (e instanceof NetworkError) {

        setError("Network error. Try again when you are online.");

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

    <View testID="verify-credential-screen" style={styles.container}>

      <Card>

        <CardBody>

          <Text style={styles.title}>Verify Credential</Text>

          <Text style={styles.sub}>Paste or scan a credential payload to verify authenticity.</Text>

          {lastCheck && verified === null && !queuedOffline ? (

            <Text style={styles.cacheNote} testID="last-credential-verify-note">

              Last check on device: {lastCheck.verified ? "valid" : "not valid"} ({formatWhen(lastCheck.checked_at)})

            </Text>

          ) : null}

          {queuedOffline ? (

            <Badge variant="warning">Queued for sync</Badge>

          ) : verified === null ? (

            <Badge variant="outline">Not verified</Badge>

          ) : verified ? (

            <Badge variant="success">Verified</Badge>

          ) : (

            <Badge variant="destructive">Invalid</Badge>

          )}

        </CardBody>

      </Card>



      <Card>

        <CardBody>

          <Text style={styles.label}>Credential</Text>

          <TextField

            value={payload}

            onChange={setPayload}

            placeholder="Paste credential here..."

            testID="credential-input"

          />

          {error ? <Text style={styles.error}>{error}</Text> : null}

          <Button

            title={queuedOffline ? "Queue again" : "Verify"}

            onPress={() => void verify()}

            disabled={!payload.trim() || loading}

            testID="verify-btn"

          />

          {lastCheck && verified !== null ? (

            <Button title="Share result summary" variant="outline" onPress={() => void shareSummary()} testID="verify-share-native" />

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

  cacheNote: { fontSize: 12, color: "#4B5563", marginTop: 8 },

  label: { fontSize: 12, fontWeight: "700", color: "#374151", marginBottom: 6 },

  error: { color: "#B91C1C", fontSize: 13, marginTop: 8 },

  spinner: { marginTop: 8 },

});

