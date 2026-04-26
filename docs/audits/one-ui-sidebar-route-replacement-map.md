# Sidebar Route → Replacement Access Map

Maps **former global sidebar** entries to **shell-first** discovery paths after drawer/taskbar redesign.

| Old label | Old route | New access path | Command keywords | Launcher group | Local menu | Redirect | Role / permission | Status |
|-----------|-----------|-----------------|------------------|----------------|------------|----------|-------------------|--------|
| Clinical Hub | `/clinical` | Start → Clinical; Command “clinical hub” | clinical, hub, care | Clinical & care | — | no | CLINICAL | keep |
| Clinical References | `/clinical-tools` | Drawer; Command “clinical tools” | tools, references, rules | Clinical & care | — | no | CLINICAL | keep |
| ED / Casualty | `/clinical/emergency` | Command “ed”, “casualty”, spotlight | emergency, ed, casualty | Clinical & care | — | no | QUEUE+ | keep |
| Queue | `/queue` | Start Queue; Command “queue” | queue, triage, waiting | Clinical & care | — | no | QUEUE | keep |
| Scheduling | `/scheduling` | Drawer; Command “roster”, “schedule” | roster, schedule, on-call | Facility ops | — | no | CLINICAL | keep |
| Pharmacy | `/pharmacy` | Start Pharmacy | pharmacy, dispensing | Clinical & care | Pharmacy local nav | no | DISPENSER | keep |
| Inventory | `/inventory` | Start Inventory | inventory, stock, commodities | Operations | ERP local | no | all | keep |
| Enterprise resources | `/enterprise` | Start Enterprise | enterprise, erp | Operations | ERP local | no | all | keep |
| Marketplace | `/marketplace` | Start Marketplace | marketplace, msika | Operations | — | no | COMMERCE | keep |
| Finance | `/finance` | Start Finance | finance, billing, claims | Finance | Finance local | no | FINANCE | keep |
| Laboratory | `/lab` | Start Laboratory | lab, lims, oros, results | Clinical & care | Lab local | no | CLINICAL | keep |
| Professional Profile | `/professional` | Drawer; Command “professional” | profile, professional | Citizen & life | — | no | pro | keep |
| Credentials | `/home/credentials` | Drawer; Home tile | credentials, cpd | Citizen & life | — | no | pro | keep |
| Registry plane | `/registry-admin` | Command “registry plane” | registry admin, governance | Registry | — | no | HIE/ADMIN | keep |
| Registry | `/registry` | Start Registry | registry, vito, varapi, tuso | Registry | — | no | CLINICAL | keep |
| Org administration | `/organization-admin` | Drawer; spotlight | org admin, organization | Operations | — | no | ADMIN/FINANCE | keep |
| Reports | `/reports` | Drawer; Command “reports” | reports, analytics | Intelligence | — | no | pro | keep |
| Administration | `/admin` | Drawer; Command “admin” | admin, governance | System | Admin local | no | ADMIN | keep |
| Operations | `/operations` | Drawer | operations, ops console | System | — | no | ADMIN | keep |
| Developer Portal | `/developer` | Drawer | developer, portal, api | System | — | no | ADMIN | keep |
| Knowledge curation | `/admin/clinical-curation` | Drawer; spotlight | curation, knowledge | System | — | no | ADMIN | keep |
| Sidecar ledger | `/admin/sidecar-retirement` | Drawer | sidecar, ledger | System | — | no | ADMIN | keep |
| Settings | `/settings` | Profile; Command “settings” | settings, preferences | System | — | no | auth | keep |
| Home | `/home` | Start Home; Command “home” | home, dashboard | Citizen & life | — | no | auth | keep |
| Ask | `/ask` | Start Ask; taskbar Nompilo | ask, assistant, ai | Intelligence | — | no | auth | keep |
| Search | `/search` | Command palette; taskbar | search, find | Intelligence | — | no | auth | keep |
| Guidance | `/guidance` | Taskbar Help | help, guidance, sop | Intelligence | — | no | auth | keep |
| Citizen services | `/citizen` | Start Citizen | citizen, health id | Citizen & life | — | no | auth | keep |
| My Wallet | `/wallet` | Spotlight; Command “wallet” | wallet, mushe, payments | Finance | Wallet local | no | auth | keep |
| Wellness | `/wellness` | Drawer; Command “wellness” | wellness, fitness | Citizen & life | Wellness app | no | auth | keep |
| Caregiving | `/caregiving` | Drawer | caregiving, delegate | Citizen & life | — | no | auth | keep |
| Monitoring | `/monitoring` | Drawer | monitoring, devices | Citizen & life | — | no | auth | keep |
| Discover | `/discover` | Drawer | discover, providers | Citizen & life | — | no | auth | keep |
| Claim shared docs | `/share/claim` | Drawer | claim, share | Citizen & life | — | no | auth | keep |
| Notifications | `/home/notifications` | Notification tray | notifications, alerts | Shell | — | no | auth | keep |
| Profile | `/home/profile` | Taskbar Profile | profile, account | Shell | — | no | auth | keep |
| Preferences | `/home/preferences` | Profile → preferences | preferences | Shell | — | no | auth | keep |
| Medications | `/home/medications` | Drawer | medications, meds | Citizen & life | — | no | auth | keep |
| Documents | `/home/documents` | Start My documents | documents, vault | Citizen & life | — | no | auth | keep |
| File manager | `/shell/file-manager` | Taskbar; Command | files, file manager | System | — | no | auth | keep |
| Task manager | `/shell/task-manager` | Taskbar | tasks, windows | System | — | no | auth | keep |
| Support | `/support` | Taskbar System Support | support, ticket, helpdesk | Shell | — | no | auth | keep |
| Secure messaging | `/communication/secure-messaging` | Taskbar Comms | comms, messages, secure | Shell | — | no | auth | keep |
| Facility | `/facility` | Breadcrumb; spotlight | facility, site | Shell | — | no | auth | keep |
| Workspace | `/workspace` | Breadcrumb; Context button | workspace | Shell | — | no | facility | keep |

**Redirects:** none required for this wave — all routes preserved.
