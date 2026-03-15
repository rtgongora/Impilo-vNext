# Provider App Mode Matrix

The Provider App operates in four distinct modes, each scoped to a specific healthcare workflow. Mode availability is determined by the authenticated user's Keycloak role assignments.

## Mode Overview

| Mode | Required Roles | Available Tabs | Key Features | Offline Capable |
|---|---|---|---|---|
| Provider | `PROVIDER` | Worklist, Patients, Activity, Alerts | Full clinical workflow: encounters, vitals, diagnosis, prescriptions, labs, referrals | Limited |
| Outreach | `OUTREACH` | Dashboard, Households, Screenings, Schedule | Community health: household visits, screenings, immunizations, GPS tagging, follow-ups | Full |
| Supervisor | `SUPERVISOR` | Dashboard, Team, Stock, Escalations | Facility management: KPIs, team oversight, stock control, dispatch, escalation handling | Limited |
| Offline Edge | Any role | Status, Queue, Conflicts, Emergency | Sync management: queue inspection, conflict resolution, edge snapshots, break-glass access | Native |

## Mode Details

### Provider Mode

**Required role:** `PROVIDER`

Provider mode is the primary clinical interface for facility-based healthcare workers. It presents a worklist-driven workflow where patients are triaged, consulted, and managed through structured encounters.

**Available tabs:**

| Tab | Purpose |
|---|---|
| Worklist | Prioritized queue of patients awaiting consultation |
| Patients | Patient search, demographics, and clinical history |
| Activity | Chronological feed of clinical actions performed |
| Alerts | Clinical alerts, critical lab results, and overdue tasks |

**Offline behavior:** Limited. Patient demographics and recent encounter history are cached locally. New encounters can be initiated offline and queued for sync, but lab orders and referrals require connectivity.

### Outreach Mode

**Required role:** `OUTREACH`

Outreach mode supports community health workers performing fieldwork. It is designed for full offline operation, with GPS-tagged visits and batch synchronization.

**Available tabs:**

| Tab | Purpose |
|---|---|
| Dashboard | Daily visit targets, completion metrics, area coverage |
| Households | Registered households and member management |
| Screenings | Community health screening forms and results |
| Schedule | Visit schedule with route optimization |

**Offline behavior:** Full. All outreach workflows are designed to operate without connectivity. Data is stored locally using CRDT structures and synchronized when the device reconnects. Edge snapshots provide pre-loaded reference data for assigned catchment areas.

### Supervisor Mode

**Required role:** `SUPERVISOR`

Supervisor mode provides facility and team management capabilities. It surfaces operational KPIs, staff performance data, and inventory status.

**Available tabs:**

| Tab | Purpose |
|---|---|
| Dashboard | Facility KPIs, visit volumes, wait times, outcome metrics |
| Team | Staff roster, attendance, task assignment, performance |
| Stock | Pharmaceutical and consumable inventory levels and adjustments |
| Escalations | Unresolved issues, support tickets, exception workflows |

**Offline behavior:** Limited. Dashboard metrics are cached for offline viewing. Stock adjustments can be queued offline. Team management and escalation handling require connectivity.

### Offline Edge Mode

**Required role:** Any authenticated role

Offline Edge mode is a system management interface available to all users. It provides visibility into the device's synchronization state and access to emergency functions.

**Available tabs:**

| Tab | Purpose |
|---|---|
| Status | Network connectivity, sync health, last sync timestamp |
| Queue | Pending sync operations with status and retry controls |
| Conflicts | Field-level conflict review and resolution interface |
| Emergency | Break-glass access activation with audit trail |

**Offline behavior:** Native. This mode exists specifically to manage offline state and is fully functional without connectivity.

## Mode Switching

Mode switching is handled by the `ModeSwitcher` component. The component reads the authenticated user's role assignments from the Keycloak token and filters the available modes accordingly.

**Switching rules:**

- A user may have access to multiple modes if they hold multiple roles (e.g., a supervisor with `PROVIDER` and `SUPERVISOR` roles can switch between both).
- Offline Edge mode is always available regardless of role assignment.
- The default mode on login is determined by the user's primary role.
- Switching modes does not require re-authentication; the existing session and trust context are preserved.
- Mode switches are logged for audit purposes.

## Authentication Requirements

| Requirement | Details |
|---|---|
| Auth protocol | Keycloak Authorization Code with PKCE |
| Token storage | Secure on-device storage (platform keychain) |
| Token refresh | Automatic silent refresh before expiry |
| Role source | Keycloak realm roles embedded in the access token |
| Trust headers | 14 headers injected by `@impilo/mobile-trust` on every request |
| Session scope | Single facility context per session; facility switch requires re-selection |
| Offline tokens | Long-lived refresh tokens for extended offline operation |
