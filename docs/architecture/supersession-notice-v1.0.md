**Version 1.0 - Controlled Notice**

**Status: Effective immediately for architecture interpretation; successor drafts remain pending formal ratification.**

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>DOCUMENT AUTHORITY<br />
</strong>This document is subordinate to the Impilo vNext Hybrid / Federated Target Architecture. Where any conflict exists, the controlling target architecture and its ADRs prevail. This successor draft becomes frozen only after the Target Architecture v1.3.2 is frozen by Product Owner sign-off.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

*Applies to: vNext V3 v1.2 and vNext Technical Companion Spec 1.2.0-canonical.*

# 1. Documents affected

| **Legacy document**                                                                                              | **Previous status**                              | **New status**                                                                                                          |
|------------------------------------------------------------------------------------------------------------------|--------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| Impilo vNext: National Health Operating System - Architectural Definition & Implementation Standard, Version 1.2 | Rewritten canonical / production-grade           | Superseded as a controlling architecture; retained as source lineage for the Product, Capability and Plane Architecture |
| Impilo vNext v1.2 - Technical Companion Spec, Version 1.2.0-canonical                                            | Architecture frozen; implementation must conform | Superseded as a controlling architecture; retained as source lineage for the Technical Standards Catalogue              |

# 2. Immediate interpretation rule

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>DO NOT IMPLEMENT FROM A LEGACY CONFLICT<br />
</strong>Where a legacy document conflicts with the Hybrid / Federated Target Architecture, the target architecture prevails. Where the legacy document contains useful product or technical material not contradicted by the target architecture, the successor Product Architecture or Technical Standards Catalogue determines its retained form.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

# 3. Successor hierarchy

**1.** Hybrid / Federated Target Architecture - controlling architecture.

**2.** Product, Capability and Plane Architecture - successor to vNext V3.

**3.** Technical Standards Catalogue - successor to the Technical Companion.

**4.** Experience Completion Packs and domain specifications.

**5.** Machine-readable and operational conformance artefacts.

# 4. Known legacy statements withdrawn immediately

- “Architecture frozen; implementation must conform” on the legacy Technical Companion.

- Client-supplied X-Tenant-ID and X-Pod-ID as load-bearing authority.

- A single service representing the whole Tshepo trust layer.

- Password grant as the normal user token pattern.

- A private pod requesting its own authority through registration payloads.

- A single centrally canonical Butano record that overrides origin authority.

- Any implication that Ring 0 means centrally deployed or that a “pod” is the legal/authority boundary.

# 5. Ratification and archival action

- Store the two legacy PDFs under an /architecture/archive/legacy-v1.2/ path or equivalent.

- Place this notice beside them and add the notice text to their repository README/index.

- Ratify the Product Architecture and Technical Standards Catalogue after Product Owner freeze of Target Architecture v1.3.2.

- After ratification, update all implementation prompts, ADR references, README files and architecture links to the successor hierarchy.
