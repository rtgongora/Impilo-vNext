import React, { useEffect, useState } from "react";

import { View, Text, StyleSheet, ActivityIndicator, Share } from "react-native";

import { Button, Card, CardBody, TextField, Badge, colors } from "@impilo/mobile-design-system";

import { appStore } from "../../stores/appStore";

import { enqueueCitizenLongtailPost, issueDelegatedPickup } from "../../services/citizenLongtailService";

import { ApiError, NetworkError } from "@impilo/mobile-api-client";

import { loadLastDelegatedPickup, saveLastDelegatedPickup, type CachedDelegatedPickup } from "../../services/citizenLongtailLocalCache";

import { formatWhen } from "../../lib/formatWhen";

/**

 * DelegatedPickupScreen — BFF (`/delegated-pickup/issue`).

 * Maps to web: /citizen/delegated-pickup

 */

export function DelegatedPickupScreen() {

  const [name, setName] = useState("");

  const [phone, setPhone] = useState("");

  const [token, setToken] = useState("");

  const [issued, setIssued] = useState(false);

  const [queuedOffline, setQueuedOffline] = useState(false);

  const [loading, setLoading] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const [lastAuth, setLastAuth] = useState<CachedDelegatedPickup | null>(null);



  useEffect(() => {

    void (async () => {

      const prev = await loadLastDelegatedPickup();

      if (prev?.token) {

        setLastAuth(prev);

        setName(prev.delegate_name);

        setPhone(prev.delegate_phone);

      }

    })();

  }, []);



  const shareToken = async () => {

    if (!token.trim()) return;

    try {

      await Share.share({

        message: `Delegated pickup token: ${token.trim()}\nFor: ${name.trim()} (${phone.trim()})`,

      });

    } catch {

      /* dismissed */

    }

  };



  const issue = async () => {

    setError(null);

    setLoading(true);

    setQueuedOffline(false);

    try {

      if (!appStore.getState().isOnline) {

        await enqueueCitizenLongtailPost(

          "/internal/v1/mobile/citizen/delegated-pickup/issue",

          { delegate_name: name.trim(), delegate_phone: phone.trim() },

          "citizen_longtail_delegated_pickup"

        );

        setQueuedOffline(true);

        setIssued(false);

        setToken("");

        return;

      }

      const data = await issueDelegatedPickup(name.trim(), phone.trim());

      setToken(data.token);

      setIssued(true);

      const snap: CachedDelegatedPickup = {

        token: data.token,

        issued_at: data.issued_at,

        delegate_name: data.delegate_name ?? name.trim(),

        delegate_phone: data.delegate_phone ?? phone.trim(),

      };

      await saveLastDelegatedPickup(snap);

      setLastAuth(snap);

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

    <View testID="delegated-pickup-screen" style={styles.container}>

      <Card>

        <CardBody>

          <Text style={styles.title}>Delegated Pickup</Text>

          <Text style={styles.sub}>

            Authorize someone you trust to pick up medication or documents on your behalf.

          </Text>

          {lastAuth && !issued && !queuedOffline ? (

            <Text style={styles.cacheNote} testID="last-delegated-pickup-note">

              Last token on this device issued {formatWhen(lastAuth.issued_at)} for {lastAuth.delegate_name}.

            </Text>

          ) : null}

          {queuedOffline ? (

            <Badge variant="warning">Queued for sync</Badge>

          ) : issued ? (

            <Badge variant="success">Authorization issued</Badge>

          ) : (

            <Badge variant="outline">No active authorization</Badge>

          )}

        </CardBody>

      </Card>



      <Card>

        <CardBody>

          <Text style={styles.label}>Delegate name</Text>

          <TextField value={name} onChange={setName} placeholder="Full name" testID="delegate-name" />

          <Text style={styles.label}>Delegate phone</Text>

          <TextField value={phone} onChange={setPhone} placeholder="+263..." testID="delegate-phone" />

          {issued && token ? (

            <>

              <Text style={styles.label}>Pickup token</Text>

              <TextField value={token} placeholder="" disabled testID="delegated-pickup-token" />

            </>

          ) : null}

          {error ? <Text style={styles.error}>{error}</Text> : null}

          <Button

            title={issued ? "Issue another" : queuedOffline ? "Queue again" : "Issue authorization"}

            onPress={() => void issue()}

            disabled={!name.trim() || !phone.trim() || loading}

            testID="issue-delegated-pickup"

          />

          {issued && token ? (

            <Button title="Share token" variant="outline" onPress={() => void shareToken()} testID="delegated-pickup-native-share" />

          ) : null}

          {loading ? <ActivityIndicator style={styles.spinner} /> : null}

        </CardBody>

      </Card>

    </View>

  );

}



const styles = StyleSheet.create({

  container: { gap: 12 },

  title: { fontSize: 16, fontWeight: "800", color: colors.gray[900] },

  sub: { fontSize: 13, color: colors.gray[500], marginTop: 6 },

  cacheNote: { fontSize: 12, color: colors.gray[600], marginTop: 8 },

  label: { fontSize: 12, fontWeight: "700", color: colors.gray[700], marginTop: 10, marginBottom: 6 },

  error: { color: "#B91C1C", fontSize: 13, marginTop: 8 },

  spinner: { marginTop: 8 },

});

