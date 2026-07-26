/**
 * FacilityDisclosure — honest regulatory-source disclosure for a facility.
 *
 * WHY THIS EXISTS: the HPA import placed 5,509 facilities into tuso.facility at
 * `regulatoryStatus = IMPORTED_PENDING_CONFIGURATION`. That means the Health
 * Professions Authority has legally registered the institution and NOBODY has
 * confirmed what services it provides, whether it is open, or whether the
 * contact details are current. Web already carries this disclosure
 * (ui/one-ui-shell/src/components/public/find-care/FacilityHpaDisclosure.tsx);
 * mobile did not, and rendered address and phone as plain facts.
 *
 * That gap is worse on mobile than on web, because mobile is the surface someone
 * uses while standing in the street deciding whether to walk in. A citizen must
 * never be sent to a building on the strength of a record nobody has checked.
 *
 * Copy is deliberately kept in step with the web component so the platform tells
 * one story: a regulatory listing is not proof that a facility is open.
 */

import React from "react";
import { View, Text, StyleSheet } from "react-native";

/** tuso regulatory_status values that mean "listed, but unconfirmed". */
export const UNCONFIRMED_REGULATORY_STATUS = "IMPORTED_PENDING_CONFIGURATION";

/**
 * Location precision tiers. The coordinate programme lands a three-precision
 * model; this component accepts all three now so the screens do not need to be
 * reopened when it arrives.
 *   address-precise → a real coordinate; distance may be shown
 *   approximate     → suburb-level only; NEVER show a precise km figure
 *   absent          → no coordinate at all (the current state of the 5,509)
 */
export type LocationPrecision = "address-precise" | "approximate" | "absent";

export function isUnconfirmedFacility(regulatoryStatus?: string | null): boolean {
  return (regulatoryStatus ?? "").toUpperCase() === UNCONFIRMED_REGULATORY_STATUS;
}

/**
 * Compact marker for list rows — must be distinguishable BEFORE tapping, so a
 * citizen scanning a directory can tell confirmed from unconfirmed at a glance.
 */
export function FacilityUnconfirmedTag({ regulatoryStatus }: { regulatoryStatus?: string | null }) {
  if (!isUnconfirmedFacility(regulatoryStatus)) return null;
  return (
    <View style={styles.tag} testID="facility-unconfirmed-tag">
      <Text style={styles.tagText}>Services not confirmed</Text>
    </View>
  );
}

/**
 * Full disclosure block for the detail screen.
 * Renders nothing for facilities whose status is confirmed — this is an
 * exception surface, not decoration.
 */
export function FacilityDisclosure({
  regulatoryStatus,
  locationPrecision = "absent",
  hasPhone = false,
}: {
  regulatoryStatus?: string | null;
  locationPrecision?: LocationPrecision;
  hasPhone?: boolean;
}) {
  if (!isUnconfirmedFacility(regulatoryStatus)) return null;

  return (
    <View style={styles.card} testID="facility-hpa-disclosure">
      <Text style={styles.heading}>About this listing</Text>

      <Text style={styles.body}>
        Registered with the Health Professions Authority — services not yet confirmed.
        A regulatory listing does not confirm that this facility is currently open or operational.
      </Text>

      <View style={styles.list}>
        <Text style={styles.item}>• Operating hours have not yet been confirmed.</Text>
        {locationPrecision === "absent" && (
          <Text style={styles.item} testID="disclosure-location-absent">
            • Map location awaiting confirmation.
          </Text>
        )}
        {locationPrecision === "approximate" && (
          <Text style={styles.item} testID="disclosure-location-approximate">
            • Location is approximate — suburb level only, so distance is not shown.
          </Text>
        )}
        {hasPhone && (
          <Text style={styles.item} testID="disclosure-phone-unconfirmed">
            • Contact details awaiting confirmation — call before travelling.
          </Text>
        )}
      </View>
    </View>
  );
}

/**
 * Renders a fact that has NOT been confirmed. Used for address/phone on
 * unconfirmed facilities so they never read as verified truth. Falls back to
 * plain rendering for confirmed facilities.
 */
export function UnconfirmedField({
  label,
  value,
  unconfirmed,
  testID,
}: {
  label: string;
  value?: string | null;
  unconfirmed: boolean;
  testID?: string;
}) {
  if (!value) return null;
  return (
    <View style={styles.fieldRow} testID={testID}>
      <Text style={styles.field}>
        {label}: {value}
      </Text>
      {unconfirmed && <Text style={styles.fieldNote}>Not confirmed</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderWidth: 1,
    borderColor: "#FDE68A",
    backgroundColor: "#FFFBEB",
    borderRadius: 12,
    padding: 14,
    gap: 8,
  },
  heading: { fontSize: 15, fontWeight: "700", color: "#92400E" },
  body: { fontSize: 13, color: "#78350F", lineHeight: 19 },
  list: { gap: 4, marginTop: 2 },
  item: { fontSize: 13, color: "#92400E", lineHeight: 19 },
  tag: {
    alignSelf: "flex-start",
    backgroundColor: "#FEF3C7",
    borderColor: "#FDE68A",
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  tagText: { fontSize: 11, fontWeight: "600", color: "#92400E" },
  fieldRow: { flexDirection: "row", alignItems: "center", flexWrap: "wrap", gap: 6 },
  field: { fontSize: 14, color: "#1F2937" },
  fieldNote: {
    fontSize: 11,
    fontWeight: "600",
    color: "#92400E",
    backgroundColor: "#FEF3C7",
    paddingHorizontal: 6,
    paddingVertical: 1,
    borderRadius: 4,
    overflow: "hidden",
  },
});
