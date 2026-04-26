# One UI Shell — Global Sidebar Dependency Audit

**Branch / scope:** `ui/one-ui-shell` — `ExperienceSidebar.tsx` + `src/lib/routes.ts` (route registry).  
**Method:** Static analysis of `ZONES`, `getSidebarSpotlight`, and footer context links (2026-04).  
**Related:** `docs/product/one-ui-shell-navigation-doctrine.md`

## Legend

| Column | Meaning |
|--------|---------|
| **Scope** | `global` = permanent shell sidebar; `local` = in-module (not audited here unless surfaced in same component) |
| **Data** | `static` = links only; `api` = page may call BFF; `mixed` |
| **Criticality** | `critical` / `common` / `occasional` / `admin` / `dev` |

---

## A. Primary sidebar zones (`ZONES` in `ExperienceSidebar.tsx`)

### Zone: Work (`id: work`)

| Label | Route | Component | Roles | Domain | Scope | Duplicated elsewhere | Route status | Data | Criticality | Replacement path(s) |
|-------|-------|------------|-------|--------|-------|------------------------|--------------|------|---------------|----------------------|
| Clinical Hub | `/clinical` | `ExperienceSidebar` | CLINICIAN,NURSE,FACILITY_ADMIN,SYSTEM_ADMIN,DEVELOPER | Clinical Care | global | Start app `clinical`, Command “clinical” | working | api | critical | Start, Command, Home clinical tiles |
| Clinical References | `/clinical-tools` | same | CLINICAL roles | Clinical / tools | global | Command (add) | working | mixed | common | Command, drawer |
| ED / Casualty | `/clinical/emergency` | same | queue roles | Emergency | global | Spotlight on clinical paths | working | api | critical | Command “ed”, Spotlight |
| Queue | `/queue` | same | queue roles | Queue | global | Start `queue` | working | api | critical | Start, Command, Home |
| Scheduling | `/scheduling` | same | CLINICAL roles | Scheduling | global | Command (add) | working | mixed | common | Command, drawer |
| Pharmacy | `/pharmacy` | same | PHARMACIST,FACILITY_ADMIN,SYSTEM_ADMIN,DEVELOPER | Pharmacy | global | Start app `pharmacy` | working | api | common | Start, Command |
| Inventory | `/inventory` | same | all authenticated | Inventory | global | Start `inventory` | working | api | common | Start, Command |
| Enterprise resources | `/enterprise` | same | all | ERP | global | Start `enterprise` | working | mixed | common | Start, Command |
| Marketplace | `/marketplace` | same | all | Commerce | global | Start `marketplace` | working | mixed | common | Start, Command |
| Finance | `/finance` | same | finance roles | Finance | global | Start `finance` | working | api | common | Start, Command |
| Laboratory | `/lab` | same | CLINICAL roles | OROS / lab | global | Start `lab` | working | mixed | common | Start, Command |

### Zone: My Professional (`id: professional`)

| Label | Route | Roles | Domain | Scope | Duplicated | Criticality | Replacement |
|-------|-------|-------|--------|-------|------------|-------------|-------------|
| Professional Profile | `/professional` | all pro | Profile | global | Home | common | Start (add app), drawer |
| Credentials | `/home/credentials` | all pro | CPD | global | Home | common | Drawer, Home |
| Registry plane | `/registry-admin` | SYSTEM_ADMIN,HIE_ADMIN | Registries | global | Admin entry | admin | Command “registry plane”, drawer |
| Registry | `/registry` | all pro | Registries | global | Start `registry` | common | Start, Command |
| Org administration | `/organization-admin` | admin, finance, dev | Admin | global | Spotlight | admin | Command, drawer |
| Reports | `/reports` | all pro | Analytics | global | — | common | Command, drawer |
| Administration | `/admin` | admin | Admin | global | — | admin | Command, drawer |
| Operations | `/operations` | admin | Ops | global | — | admin | Command, drawer |
| Developer Portal | `/developer` | admin | Dev | global | — | dev | Command, drawer |
| Knowledge curation | `/admin/clinical-curation` | admin | Governance | global | Spotlight | admin | Command, drawer |
| Sidecar ledger | `/admin/sidecar-retirement` | admin | Dev/Ops | global | — | dev | Command, drawer |
| Settings | `/settings` | all pro | Settings | global | Profile footer | common | Profile menu, Command |

### Zone: My Life (`id: life`)

| Label | Route | Roles | Domain | Scope | Criticality | Replacement |
|-------|-------|-------|--------|-------|-------------|-------------|
| Home | `/home` | auth | Home | global | critical | Start, Command, taskbar |
| Ask | `/ask` | auth | Intelligence | global | common | Start app `ask`, taskbar |
| Search | `/search` | auth | Search | global | common | Command palette, taskbar |
| Guidance | `/guidance` | auth | Help | global | common | Taskbar Help, Command |
| Citizen services | `/citizen` | auth | Registration / citizen | global | common | Start `citizen`, Home |
| My Wallet | `/wallet` | auth | MusheX | global | common | Spotlight on wallet, Command |
| Wellness | `/wellness` | auth | Wellness | global | common | Home, Command |
| Caregiving | `/caregiving` | auth | Caregiving | global | occasional | Drawer, Home |
| Monitoring | `/monitoring` | auth | Remote monitoring | global | occasional | Drawer |
| Discover | `/discover` | auth | Discovery | global | occasional | Drawer |
| Claim shared docs | `/share/claim` | auth | Documents | global | occasional | Drawer, spotlight |
| Notifications | `/home/notifications` | auth | Notifications | global | common | Taskbar tray, Command |
| Profile | `/home/profile` | auth | Profile | global | critical | Taskbar Profile, Command |
| Preferences | `/home/preferences` | auth | Settings | global | common | Profile path |
| Medications | `/home/medications` | auth | Citizen meds | global | common | Drawer, Home |
| Documents | `/home/documents` | auth | Documents | global | common | Start `my_documents` |
| File manager | `/shell/file-manager` | auth | Shell | global | occasional | Taskbar, Command |
| Task manager | `/shell/task-manager` | auth | Shell | global | occasional | Taskbar |
| Support | `/support` | auth | Support | global | common | Taskbar System Support |

---

## B. Context spotlight quick actions (`getSidebarSpotlight`)

These are **additional** links rendered above the zone list (contextual, not duplicate of zone items).

| Context trigger (pathname prefix) | Spotlight title | Actions (label → href) |
|------------------------------------|-----------------|-------------------------|
| `/registry-admin` | Registry governance plane | Intake hub `/registry/intake`, Providers `/registry/providers`, Facilities `/registry/facilities` |
| `/wallet` | Mushe Wallet | Dashboard `/wallet`, Send `/wallet/send`, Cards `/wallet/cards` |
| `/organization-admin` | Organization administration | Admin `/admin`, Reports `/reports` |
| `/ehr`, `/queue`, `/telemedicine`, `/clinical` | Clinical coordination | ED `/clinical/emergency`, Queue `/queue`, Telemedicine `/telemedicine` |
| `/finance` | Finance operations | Billing `/finance/billing`, Payments `/finance/payments` |
| `/enterprise`, `/erp` | Enterprise resource plane | Dashboard `/enterprise`, Inventory `/inventory`, Procurement `/erp/procurement`, Finance `/finance` |
| `/citizen`, `/share/claim`, `/verify/credential` | Citizen self-service | Verify `/verify/credential`, Health ID QR, ID recovery, Claim docs |
| `/registry`, `/admin`, `/reports` | Professional oversight | Registry intake, Knowledge curation, Registry plane, Org admin |
| default | One Experience Layer | Home `/home`, Facility `/facility` |

**Replacement:** each spotlight action gets **Command palette keywords** (see route replacement map) and appears in **drawer** when user is on matching routes (spotlight preserved inside drawer).

---

## C. Footer / embedded context (sidebar bottom)

| UI block | Purpose | Replacement |
|----------|---------|-------------|
| Active context (facility, workspace, shift) | Orientation | **ModuleBreadcrumb** + **Context** taskbar button + Home context card |
| Citizen Profile / Settings links | Account | **Profile** taskbar + Home |
| Provider role switch chips | Role switch | **Context** flow + future dedicated modal |

---

## D. Cross-package notes (this audit cycle)

| Package | Finding |
|---------|---------|
| `ui/shared-ui` | No global sidebar component found. |
| `ui/experience`, `ui/ehr`, … other web UIs | Host **One UI Shell** for unified navigation; `routes.ts` duplicates `sidebar` metadata for parity — not a second sidebar implementation. |
| `apps/mobile` | **Parity follow-up:** align mobile tab / drawer model with shell doctrine (separate backlog). |

---

## E. Mock / fake / placeholder risk (shell-adjacent)

| Area | Risk | Mitigation |
|------|------|------------|
| Home dashboard tiles | Legacy demo numbers | Doctrine: empty states; gate demo data behind `NODE_ENV` / feature flags (incremental) |
| Notification tray | Fake counts | Only render server-driven or “no notifications” |

---

## Sign-off gate

**Do not remove the navigation drawer content until:**

1. This audit is reviewed against production role matrix.  
2. `docs/audits/one-ui-sidebar-route-replacement-map.md` is kept in sync when routes change.  
3. User acceptance tests cover **Start**, **Search**, and **drawer** for each **critical** row above.
