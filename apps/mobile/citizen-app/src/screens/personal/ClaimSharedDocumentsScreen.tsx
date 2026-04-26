import React, { useEffect, useState } from "react";

import { View, Text, StyleSheet, ActivityIndicator, Share } from "react-native";

import { Button, Card, CardBody, TextField, Badge } from "@impilo/mobile-design-system";

import { appStore } from "../../stores/appStore";

import { claimSharedDocuments, enqueueCitizenLongtailPost } from "../../services/citizenLongtailService";

import { ApiError, NetworkError } from "@impilo/mobile-api-client";

import { loadLastShareClaim, saveLastShareClaim, type CachedShareClaim } from "../../services/citizenLongtailLocalCache";

import { formatWhen } from "../../lib/formatWhen";

/**

 * ClaimSharedDocumentsScreen — claim via BFF (`/share/claim`).

 * Maps to web: /share/claim

 */

export function ClaimSharedDocumentsScreen() {

  const [code, setCode] = useState("");

  const [claimed, setClaimed] = useState(false);

  const [queuedOffline, setQueuedOffline] = useState(false);

  const [loading, setLoading] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const [lastClaim, setLastClaim] = useState<CachedShareClaim | null>(null);



  useEffect(() => {

    void (async () => {

      const prev = await loadLastShareClaim();

      if (prev?.code) setLastClaim(prev);

    })();

  }, []);



  const shareClaimMeta = async () => {

    if (!lastClaim?.code) return;

    try {

      await Share.share({

        message: `Claimed share code ${lastClaim.code} on ${formatWhen(lastClaim.claimed_at)}`,

      });

    } catch {

      /* dismissed */

    }

  };



  const claim = async () => {

    setError(null);

    setLoading(true);

    setQueuedOffline(false);

    try {

      if (!appStore.getState().isOnline) {

        await enqueueCitizenLongtailPost(

          "/internal/v1/mobile/citizen/share/claim",

          { code: code.trim() },

          "citizen_longtail_share_claim"

        );

        setQueuedOffline(true);

        setClaimed(false);

        return;

      }

      const result = await claimSharedDocuments(code.trim());

      setClaimed(true);

      const snap = { code: result.code ?? code.trim(), claimed_at: result.claimed_at };

      await saveLastShareClaim(snap);

      setLastClaim(snap);

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

    <View testID="claim-shared-documents-screen" style={styles.container}>

      <Card>

        <CardBody>

          <Text style={styles.title}>Claim Shared Documents</Text>

          <Text style={styles.sub}>Enter a share code provided to you to claim shared documents.</Text>

          {lastClaim && !claimed && !queuedOffline ? (

            <Text style={styles.cacheNote} testID="last-share-claim-note">

              Last successful claim: {lastClaim.code} at {formatWhen(lastClaim.claimed_at)}

            </Text>

          ) : null}

          {queuedOffline ? (

            <Badge variant="warning">Queued for sync</Badge>

          ) : claimed ? (

            <Badge variant="success">Claimed</Badge>

          ) : (

            <Badge variant="outline">Not claimed</Badge>

          )}

        </CardBody>

      </Card>



      <Card>

        <CardBody>

          <Text style={styles.label}>Share code</Text>

          <TextField

            value={code}

            onChange={setCode}

            placeholder="e.g. SHARE-1234"

            testID="claim-code"

          />

          {claimed && lastClaim ? (

            <Text style={styles.meta} testID="claim-confirmed-at">

              Confirmed at {formatWhen(lastClaim.claimed_at)}

            </Text>

          ) : null}

          {error ? <Text style={styles.error}>{error}</Text> : null}

          <Button

            title={queuedOffline ? "Queue again" : "Claim"}

            onPress={() => void claim()}

            disabled={!code.trim() || loading}

            testID="claim-btn"

          />

          {claimed && lastClaim ? (

            <Button title="Share claim details" variant="outline" onPress={() => void shareClaimMeta()} testID="claim-share-native" />

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

  meta: { fontSize: 12, color: "#059669", marginTop: 8 },

  label: { fontSize: 12, fontWeight: "700", color: "#374151", marginBottom: 6 },

  error: { color: "#B91C1C", fontSize: 13, marginTop: 8 },

  spinner: { marginTop: 8 },

});

