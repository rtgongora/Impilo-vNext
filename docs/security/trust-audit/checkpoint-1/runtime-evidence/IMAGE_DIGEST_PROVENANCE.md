# Image digest → source commit provenance — Checkpoint 1 closure

**Observation window (UTC):** `2026-08-01T09:42:27Z`
**Namespace:** `impilo-full-preview`
**Branch documenting this capture:** `claude/tshepo-trust-cp1-truth-audit`

## Method

- Workload images read from live Deployment/StatefulSet/CronJob specs.
- Provenance taken from OCI labels via `docker inspect` when the local registry digest is present on the node:
  - `org.opencontainers.image.revision` → source commit
  - `zw.gov.mohcc.impilo.source.tree` → tree SHA
  - `zw.gov.mohcc.impilo.source.branch` → build branch
- Images without readable labels are classified **UNKNOWN** (not inferred from the documenting branch).
- Vendor/third-party images are **VENDOR_EXTERNAL** (no Impilo source commit expected).

## Summary

| Class | Count |
|---|---|
| MAPPED | 5 |
| UNKNOWN | 101 |
| VENDOR_EXTERNAL | 12 |
| Distinct workload images | 118 |

## Trust-relevant mapped digests (explicit)

| Workload | Image digest | Source commit | Build branch | Tree |
|---|---|---|---|---|
| Deployment/butano-fhir | `sha256:a0d05ae4f9665372424148b6c8f86030a2c894e9e7c77a925dc6528c966e9f4f` | `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| Deployment/butano-service | `sha256:37d9a856b9ebd64eb414d92b601dbc74871ca39fa9f85b2bcd3ea29e29ac7ad2` | `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| Deployment/experience-bff | `sha256:1948d8d355b5a3456ed0bbdf1feb195143ff4f45b348b8dcb85b1d41b3ea763b` | `486b3a4ff93e6e4b2cfb9eb8ea1aa7503649b565` | `codex/mfa-production` | `ce6e984e07cf83e48bd7d503ba07481394a96654` |
| Deployment/fhir-gateway-service | `sha256:8081d24cf865dcff61e2b83efa89718c75bef897f7ad07e56d211aab196cf711` | `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| Deployment/keycloak,Job/keycloak-bootstrap-admin-mfa,Job/keycloak-create-reconciler-mfa,Job/keycloak-grant-event-reader-mfa,Job/keycloak-remove-bootstrap-admin-mfa | `sha256:70f0af3d5a9352c1d62cf6ea059430faaa10ed772bb63bea690c99cd2a4836bc` | `304152be61a790c2e92f40f36b1db2b4e6ff11c6` | `codex/mfa-production` | `52e64b981d3ffd45cf4a0dee6d795d17c77ec12a` |
| Deployment/mvumo-service | `sha256:ade83fa189b43b7824293655c16cf3a25c0587bfe5ae6356cc13fcab7b1868b1` | `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| Deployment/one-ui-shell | `sha256:d264a0c1ebbf11fe675d893f90473de02bed5fb8dc64100746622eddab0f2b2e` | `304152be61a790c2e92f40f36b1db2b4e6ff11c6` | `codex/mfa-production` | `52e64b981d3ffd45cf4a0dee6d795d17c77ec12a` |
| Deployment/pct-service | `sha256:364c104c80ee19448a4082a91a4b1ca380fb3faf5807b73034d9318f098a2775` | `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| Deployment/tshepo-audit-service | `sha256:bba09c3926e5106fa72358a2d731bef2f91a154b9c92f87499d83a7327b38bd5` | `3f42627f7745e87e748f648166b2509b3fc98580` | `codex/mfa-production` | `1b94c7f6b121bcee123535562cc4575c0c620ab6` |
| Deployment/tshepo-authz-service | `sha256:4da33b6f60ae10e647261fc908b2687c3dfb9c08ef1a4110f2c4b8a41a058e82` | `07b8674a89eeea696a0a48e1cc406ca46a3bd775` | `codex/mfa-production` | `ee337f945a91b5ee9e662b88744d8a0c4863ff3f` |
| Deployment/tshepo-consent-service | `sha256:42add39afe9af9065d160c8dbd6072025bab9a7f995708fc3f478e9bdd0229db` | `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| Deployment/tshepo-identity-service | `sha256:8277bb5e8f8b5a759f96e0ea9068d86041baf133cfe12b49b73075fa1956ba47` | `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| Deployment/tshepo-keys-service | `sha256:5974067cd6e9f2775f01b00ef13e0205cc82e1f5af4b1dc2a757d4fb9265fba7` | `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| Deployment/tshepo-offline-service | `sha256:285377523fd1cc828312adb79f8385a9193f37fb6c926f4c58078f1b46a33610` | `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| Deployment/varapi-service | `sha256:216b86409a0a8444449afc83761ccf35208c1025305597b6b2174691d69c6583` | `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| Deployment/vito-service | `sha256:2909c790f1f8f09194a4d1423bf1312f490a0da8c3d13c7214fddbdc3893ba4b` | `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| Job/keycloak-h2-snapshot-mfa-20260801-0313 | `UNKNOWN` | `N/A (vendor image)` | `N/A` | `N/A` |
| Deployment/envoy | `UNKNOWN` | `N/A (vendor image)` | `N/A` | `N/A` |
| Job/keycloak-h2-export-mfa-20260801-0313 | `sha256:82c5b7a110456dbd42b86ea572e728878549954cc8bd03cd65410d75328095d2` | `N/A (vendor image)` | `N/A` | `N/A` |
| Job/keycloak-pg25-import-mfa-20260801-0313 | `sha256:82c5b7a110456dbd42b86ea572e728878549954cc8bd03cd65410d75328095d2` | `N/A (vendor image)` | `N/A` | `N/A` |

## Full inventory

| Provenance | Image (as deployed) | Digest | Source commit | Owners (sample) |
|---|---|---|---|---|
| UNKNOWN | `127.0.0.1:5000/impilo/abis-service@sha256:b70f81f782d1c3caf86fe3f3c3121a3841fcb982e4838...` | `sha256:b70f81f782d1c3caf86fe3f3c3121a3841fcb982e4838101f94209a4cc1d7844` | `UNKNOWN` | Deployment/abis-service |
| UNKNOWN | `127.0.0.1:5000/impilo/ai-model-registry-service@sha256:78692566c4af25a9af656b7cc598f006...` | `sha256:78692566c4af25a9af656b7cc598f006e808fc069ad71d9fe3b8c2ad8613062d` | `UNKNOWN` | Deployment/ai-model-registry-service |
| UNKNOWN | `127.0.0.1:5000/impilo/analytics-pipeline-service@sha256:5922e54c1891607fc32e3c5451c8dcb...` | `sha256:5922e54c1891607fc32e3c5451c8dcbb01b5be8fa1809e475e6398f3cc37c688` | `UNKNOWN` | Deployment/analytics-pipeline-service |
| UNKNOWN | `127.0.0.1:5000/impilo/asset-registry-service@sha256:d9743322a41ebd3fa7f2957b72800824eeb...` | `sha256:d9743322a41ebd3fa7f2957b72800824eeba3a5b7f0359b9ddbbd4ed2bf430e6` | `UNKNOWN` | Deployment/asset-registry-service |
| UNKNOWN | `127.0.0.1:5000/impilo/audit-ledger-service@sha256:87806a69083dd2ea2247129fa3121d77f7b38...` | `sha256:87806a69083dd2ea2247129fa3121d77f7b3879da218dd9d35038349d06c2555` | `UNKNOWN` | Deployment/audit-ledger-service |
| UNKNOWN | `127.0.0.1:5000/impilo/booking-service@sha256:1e72f57ebd690a05a6ead1e7d804b446d85daa8b1d...` | `sha256:1e72f57ebd690a05a6ead1e7d804b446d85daa8b1d0e6abd47df1fc1e25392fd` | `UNKNOWN` | Deployment/booking-service |
| UNKNOWN | `127.0.0.1:5000/impilo/butano-fhir@sha256:a0d05ae4f9665372424148b6c8f86030a2c894e9e7c77a...` | `sha256:a0d05ae4f9665372424148b6c8f86030a2c894e9e7c77a925dc6528c966e9f4f` | `UNKNOWN` | Deployment/butano-fhir |
| UNKNOWN | `127.0.0.1:5000/impilo/butano-service@sha256:37d9a856b9ebd64eb414d92b601dbc74871ca39fa9f...` | `sha256:37d9a856b9ebd64eb414d92b601dbc74871ca39fa9f85b2bcd3ea29e29ac7ad2` | `UNKNOWN` | Deployment/butano-service |
| UNKNOWN | `127.0.0.1:5000/impilo/campaigns-service@sha256:17bcd29748a7e235aca83a88201aaea1570dd9fb...` | `sha256:17bcd29748a7e235aca83a88201aaea1570dd9fbf84443e314c5a50b951233e7` | `UNKNOWN` | Deployment/campaigns-service |
| UNKNOWN | `127.0.0.1:5000/impilo/card-print-agent@sha256:f6e2ba2643ff0336ccea0670a6789a3a51095c36d...` | `sha256:f6e2ba2643ff0336ccea0670a6789a3a51095c36d59643d719bce9f1881a7c76` | `UNKNOWN` | Deployment/card-print-agent |
| UNKNOWN | `127.0.0.1:5000/impilo/channels-service@sha256:ccdd0ffbff4ee587a31c2d05aaaa19337aa5393b1...` | `sha256:ccdd0ffbff4ee587a31c2d05aaaa19337aa5393b1157cb802c8df24fc24df8fc` | `UNKNOWN` | Deployment/channels-service |
| UNKNOWN | `127.0.0.1:5000/impilo/clinical-knowledge-platform-service@sha256:fa4e56e22650ecf5a00783...` | `sha256:fa4e56e22650ecf5a0078303ad14a843201a7a1848f3ada4466118f4644f5592` | `UNKNOWN` | Deployment/clinical-knowledge-platform-service |
| UNKNOWN | `127.0.0.1:5000/impilo/community-service@sha256:ea195af7c5ba26efebf6a79f33b0125cb413b21b...` | `sha256:ea195af7c5ba26efebf6a79f33b0125cb413b21bd07474bd7cf2d7488dbb34b4` | `UNKNOWN` | Deployment/community-service |
| UNKNOWN | `127.0.0.1:5000/impilo/connector-fhir-adapter@sha256:a93778b9017a141b737b45530a944168cec...` | `sha256:a93778b9017a141b737b45530a944168cec5cc48c5820783535619cc1e7883ca` | `UNKNOWN` | Deployment/connector-fhir-adapter |
| UNKNOWN | `127.0.0.1:5000/impilo/costing-engine-service@sha256:a92b4d26be810a91257851d5f4eb3f26a6f...` | `sha256:a92b4d26be810a91257851d5f4eb3f26a6f7fbf849d4e86673b443fc127b114d` | `UNKNOWN` | Deployment/costing-engine-service |
| UNKNOWN | `127.0.0.1:5000/impilo/coverage-service@sha256:9477166c88962d82dbbada89ae6da8a1ebfe6dde4...` | `sha256:9477166c88962d82dbbada89ae6da8a1ebfe6dde451a5a27c139311a589d055c` | `UNKNOWN` | Deployment/coverage-service |
| UNKNOWN | `127.0.0.1:5000/impilo/credential-verification-service@sha256:09ebb4222f69df2a0ad8bbf673...` | `sha256:09ebb4222f69df2a0ad8bbf6733e7eb5aabfa8d7aeb28573e4d4a51050780cfa` | `UNKNOWN` | Deployment/credential-verification-service |
| UNKNOWN | `127.0.0.1:5000/impilo/daidzai-service@sha256:e31553d698e341a19e7ae9cdcb7cea82d306549dac...` | `sha256:e31553d698e341a19e7ae9cdcb7cea82d306549dac3d6f2accc3de3d176e54c1` | `UNKNOWN` | Deployment/daidzai-service |
| UNKNOWN | `127.0.0.1:5000/impilo/data-access-governance-service@sha256:6b6678ae8ab897837e71047b2ed...` | `sha256:6b6678ae8ab897837e71047b2ed715657b0326a86c8bf5c377d4f28861fb6c2b` | `UNKNOWN` | Deployment/data-access-governance-service |
| UNKNOWN | `127.0.0.1:5000/impilo/data-governance-service@sha256:631059f5240dd3217ab86e89f9440d114c...` | `sha256:631059f5240dd3217ab86e89f9440d114c8616aeb75e65a2fd6c0f0ddd5698a0` | `UNKNOWN` | Deployment/data-governance-service |
| UNKNOWN | `127.0.0.1:5000/impilo/data-ingestion-service@sha256:118b033474fb045847c0c742b84fe73689f...` | `sha256:118b033474fb045847c0c742b84fe73689f503f0eb4dbbfdbc02695b246cc413` | `UNKNOWN` | Deployment/data-ingestion-service |
| UNKNOWN | `127.0.0.1:5000/impilo/data-pipeline-service@sha256:b59aa713ffe96e5827da0c07a4e8d707d177...` | `sha256:b59aa713ffe96e5827da0c07a4e8d707d177de087ad78049f8f64a5a764319e2` | `UNKNOWN` | Deployment/data-pipeline-service |
| UNKNOWN | `127.0.0.1:5000/impilo/data-warehouse-service@sha256:c5c6896094d0aee1b015db7d034b6c63752...` | `sha256:c5c6896094d0aee1b015db7d034b6c6375237ef3cb65171ffd5fe9d16fdee72e` | `UNKNOWN` | Deployment/data-warehouse-service |
| UNKNOWN | `127.0.0.1:5000/impilo/developer-portal-service@sha256:68d4c93c354fc215f02bb446ce653c989...` | `sha256:68d4c93c354fc215f02bb446ce653c989c258c59d75e41855a38cbc863149aa0` | `UNKNOWN` | Deployment/developer-portal-service |
| UNKNOWN | `127.0.0.1:5000/impilo/dispatch-service@sha256:42b8f98c37ad5f21f85bbf63149238acd1c33241c...` | `sha256:42b8f98c37ad5f21f85bbf63149238acd1c33241c29bed05bb282861a81a4a0e` | `UNKNOWN` | Deployment/dispatch-service |
| UNKNOWN | `127.0.0.1:5000/impilo/document-service@sha256:c21f04fcb28d228d7d340792165e5ae3da0b27062...` | `sha256:c21f04fcb28d228d7d340792165e5ae3da0b27062396629d327a3c9f32cb6976` | `UNKNOWN` | Deployment/document-service |
| MAPPED | `127.0.0.1:5000/impilo/experience-bff@sha256:1948d8d355b5a3456ed0bbdf1feb195143ff4f45b34...` | `sha256:1948d8d355b5a3456ed0bbdf1feb195143ff4f45b348b8dcb85b1d41b3ea763b` | `486b3a4ff93e6e4b2cfb9eb8ea1aa7503649b565` | Deployment/experience-bff |
| UNKNOWN | `127.0.0.1:5000/impilo/fhir-gateway-service@sha256:8081d24cf865dcff61e2b83efa89718c75bef...` | `sha256:8081d24cf865dcff61e2b83efa89718c75bef897f7ad07e56d211aab196cf711` | `UNKNOWN` | Deployment/fhir-gateway-service |
| UNKNOWN | `127.0.0.1:5000/impilo/forms-service@sha256:f20041d6620233ed7d95e23846e7f6bc3a37071fad3e...` | `sha256:f20041d6620233ed7d95e23846e7f6bc3a37071fad3e2ad37b12e6b4cf3cbbe7` | `UNKNOWN` | Deployment/forms-service |
| UNKNOWN | `127.0.0.1:5000/impilo/general-ledger-service@sha256:312963d3eba2257de5fd4c5e3a4c7c5c950...` | `sha256:312963d3eba2257de5fd4c5e3a4c7c5c95067b37f2bb7e07af1b28fe6de62ed6` | `UNKNOWN` | Deployment/general-ledger-service |
| UNKNOWN | `127.0.0.1:5000/impilo/guidance-service@sha256:5077164f86e9acb71eee144d3886a35cd83ac9166...` | `sha256:5077164f86e9acb71eee144d3886a35cd83ac91660f6477c62859cd9d7d45d7e` | `UNKNOWN` | Deployment/guidance-service |
| UNKNOWN | `127.0.0.1:5000/impilo/hr-payroll-service@sha256:0b7afc361c44ca3c741feedf0914785b2dad38b...` | `sha256:0b7afc361c44ca3c741feedf0914785b2dad38b5f56380d977f87f4ebedf6680` | `UNKNOWN` | Deployment/hr-payroll-service |
| UNKNOWN | `127.0.0.1:5000/impilo/identity-assurance-service@sha256:dabbeb2743a7e74b27f1805c78ed151...` | `sha256:dabbeb2743a7e74b27f1805c78ed1514f7d97112d3250a005f42096109ede3b8` | `UNKNOWN` | Deployment/identity-assurance-service |
| UNKNOWN | `127.0.0.1:5000/impilo/indawo-service@sha256:9c5a992447d1d4a64d0d26c57954817d0ecf8a73d59...` | `sha256:9c5a992447d1d4a64d0d26c57954817d0ecf8a73d59d27eba3464e9a1eb02584` | `UNKNOWN` | Deployment/indawo-service |
| UNKNOWN | `127.0.0.1:5000/impilo/inpatient-service@sha256:473b87e7074d6146a2cf19c2670987dcf231b4bb...` | `sha256:473b87e7074d6146a2cf19c2670987dcf231b4bbbfb7009ebd7101e07a28f82c` | `UNKNOWN` | Deployment/inpatient-service |
| UNKNOWN | `127.0.0.1:5000/impilo/integration-hub@sha256:cd1fb0d96e900168c739fca6abe6201b7bacfe0609...` | `sha256:cd1fb0d96e900168c739fca6abe6201b7bacfe0609ef23ba252e3b94b6806e69` | `UNKNOWN` | Deployment/integration-hub |
| UNKNOWN | `127.0.0.1:5000/impilo/inventory-elmis-adapter@sha256:ca604bf962789bab98fb231f86ad5469c6...` | `sha256:ca604bf962789bab98fb231f86ad5469c6fe19444a9c006721d75c980f7e6a5c` | `UNKNOWN` | Deployment/inventory-elmis-adapter |
| UNKNOWN | `127.0.0.1:5000/impilo/inventory-service@sha256:ec05ca0da427283b3d30fc42a44076cf4bc13b73...` | `sha256:ec05ca0da427283b3d30fc42a44076cf4bc13b7394a2902fc6f496ce3a6ae12b` | `UNKNOWN` | Deployment/inventory-service |
| UNKNOWN | `127.0.0.1:5000/impilo/iot-ingestion-service@sha256:8034eb76b45a82cffe0f96eb61f3daace04a...` | `sha256:8034eb76b45a82cffe0f96eb61f3daace04af0048092b8f858bef3ab9a0be300` | `UNKNOWN` | Deployment/iot-ingestion-service |
| UNKNOWN | `127.0.0.1:5000/impilo/jobs-service@sha256:e69a859d79ae75ac010ab73bdd58490fc37159ba6eda7...` | `sha256:e69a859d79ae75ac010ab73bdd58490fc37159ba6eda7bd3768696b747fce0ed` | `UNKNOWN` | Deployment/jobs-service |
| MAPPED | `127.0.0.1:5000/impilo/keycloak@sha256:70f0af3d5a9352c1d62cf6ea059430faaa10ed772bb63bea6...` | `sha256:70f0af3d5a9352c1d62cf6ea059430faaa10ed772bb63bea690c99cd2a4836bc` | `304152be61a790c2e92f40f36b1db2b4e6ff11c6` | Deployment/keycloak, Job/keycloak-bootstrap-admin-mfa, Job/keycloak-create-reconciler-mfa (+2) |
| UNKNOWN | `127.0.0.1:5000/impilo/khuluma-service@sha256:397135a232c9543f1ccb2b2d9f4e816104b3598423...` | `sha256:397135a232c9543f1ccb2b2d9f4e816104b3598423506099f2730edad0727780` | `UNKNOWN` | Deployment/khuluma-service |
| UNKNOWN | `127.0.0.1:5000/impilo/landela-adapter-service@sha256:ebe285ea0a58253c4ce1f1f1558feb837d...` | `sha256:ebe285ea0a58253c4ce1f1f1558feb837dc764a05714be94be7d72f739baab82` | `UNKNOWN` | Deployment/landela-adapter-service |
| UNKNOWN | `127.0.0.1:5000/impilo/learning-service@sha256:7cb3d1171a9168d9b6beebde4e21287f7a7c370c5...` | `sha256:7cb3d1171a9168d9b6beebde4e21287f7a7c370c55cea40727c07d75313956d7` | `UNKNOWN` | Deployment/learning-service |
| UNKNOWN | `127.0.0.1:5000/impilo/live-service@sha256:8c3072f7929244bc179eb7a3f77767d92c5db517595bb...` | `sha256:8c3072f7929244bc179eb7a3f77767d92c5db517595bb7fecd8c70b232830d58` | `UNKNOWN` | Deployment/live-service |
| UNKNOWN | `127.0.0.1:5000/impilo/llm-orchestration-service@sha256:c46371f5735c26164fe53b325e16ae8d...` | `sha256:c46371f5735c26164fe53b325e16ae8ddce49c63aa568dbc60f1598f7eb97743` | `UNKNOWN` | Deployment/llm-orchestration-service |
| UNKNOWN | `127.0.0.1:5000/impilo/madi-service@sha256:4c1627115a6106fe07ac8784f69083aa7ec705f25c0cf...` | `sha256:4c1627115a6106fe07ac8784f69083aa7ec705f25c0cffba8751967a4db37603` | `UNKNOWN` | Deployment/madi-service |
| UNKNOWN | `127.0.0.1:5000/impilo/matcher-engine:preview` | `sha256:9be47f412531b3aee9154bb94b1ecbdd0c8e04d291e79cb779f807a0abef6f56` | `UNKNOWN` | Deployment/matcher-engine |
| UNKNOWN | `127.0.0.1:5000/impilo/msika-apps-service@sha256:954ad75b010beef8240dd02f3a9255c1ad2c9b7...` | `sha256:954ad75b010beef8240dd02f3a9255c1ad2c9b779d39d8c91789d09518a4d42c` | `UNKNOWN` | Deployment/msika-apps-service |
| UNKNOWN | `127.0.0.1:5000/impilo/msika-flow-service@sha256:be80b9f9ed1f8f34b9b03d9a927a2e1054452d0...` | `sha256:be80b9f9ed1f8f34b9b03d9a927a2e1054452d0f342b5c686df1efdba98bd372` | `UNKNOWN` | Deployment/msika-flow-service |
| UNKNOWN | `127.0.0.1:5000/impilo/msika-service@sha256:dfc721ecc423356e780f59361a13f2eba2d35b78c841...` | `sha256:dfc721ecc423356e780f59361a13f2eba2d35b78c8410ce24f3a561d07743d7f` | `UNKNOWN` | Deployment/msika-service |
| UNKNOWN | `127.0.0.1:5000/impilo/mushe-wallet-service@sha256:cffb855fe54b602c03524a5092fdfccc4256b...` | `sha256:cffb855fe54b602c03524a5092fdfccc4256b6a29d2064f11d8dbca0f64703f7` | `UNKNOWN` | Deployment/mushe-wallet-service |
| UNKNOWN | `127.0.0.1:5000/impilo/mushex-service@sha256:00d24394f0ed173020fb0e9688cbf2c68699aa4c839...` | `sha256:00d24394f0ed173020fb0e9688cbf2c68699aa4c8395c83400a0a347ba618903` | `UNKNOWN` | Deployment/mushex-service |
| UNKNOWN | `127.0.0.1:5000/impilo/mvumo-service@sha256:ade83fa189b43b7824293655c16cf3a25c0587bfe5ae...` | `sha256:ade83fa189b43b7824293655c16cf3a25c0587bfe5ae6356cc13fcab7b1868b1` | `UNKNOWN` | Deployment/mvumo-service |
| UNKNOWN | `127.0.0.1:5000/impilo/national-data-repository-service@sha256:6b4a2136179743ada791a5be2...` | `sha256:6b4a2136179743ada791a5be200e8a417166e236bf55b088bc9f068d4987cafc` | `UNKNOWN` | Deployment/national-data-repository-service |
| UNKNOWN | `127.0.0.1:5000/impilo/ndila-service@sha256:998eff10ffc05642ec2c03309be0d0af25d9a81bbd25...` | `sha256:998eff10ffc05642ec2c03309be0d0af25d9a81bbd2548805feeda295b4f6919` | `UNKNOWN` | Deployment/ndila-service |
| UNKNOWN | `127.0.0.1:5000/impilo/ndr-service@sha256:4222a8d713a8a5ca49e44f0e804cd822bc362d47906e11...` | `sha256:4222a8d713a8a5ca49e44f0e804cd822bc362d47906e119921668fa99cdcf27e` | `UNKNOWN` | Deployment/ndr-service |
| UNKNOWN | `127.0.0.1:5000/impilo/nhume-service@sha256:beb4113faa935dbc8574a216bb96706949b6e53c2b4d...` | `sha256:beb4113faa935dbc8574a216bb96706949b6e53c2b4d598ed71f99e2d6813bdb` | `UNKNOWN` | Deployment/nhume-service |
| UNKNOWN | `127.0.0.1:5000/impilo/notification-service@sha256:02f39539ccb4fba475aa51a30961f21f1584a...` | `sha256:02f39539ccb4fba475aa51a30961f21f1584a5c680e04c4f4424e69c90244848` | `UNKNOWN` | Deployment/notification-service |
| UNKNOWN | `127.0.0.1:5000/impilo/observability-service@sha256:0a24076647e4d5b0c30f5b6be462330ccf95...` | `sha256:0a24076647e4d5b0c30f5b6be462330ccf95dd465d509586fcbac71b211e966d` | `UNKNOWN` | Deployment/observability-service |
| UNKNOWN | `127.0.0.1:5000/impilo/offline-edge-service@sha256:294ee9b9ab65d981f79a1ce04a6e403b82f1c...` | `sha256:294ee9b9ab65d981f79a1ce04a6e403b82f1c31b8a6352e319ffc805da5e2521` | `UNKNOWN` | Deployment/offline-edge-service |
| UNKNOWN | `127.0.0.1:5000/impilo/offline-sync-service@sha256:40f18ecacb291c86d186bfc85527c1ec88482...` | `sha256:40f18ecacb291c86d186bfc85527c1ec8848297fb46ec716d987a05ec58bc56a` | `UNKNOWN` | Deployment/offline-sync-service |
| MAPPED | `127.0.0.1:5000/impilo/one-ui-shell@sha256:d264a0c1ebbf11fe675d893f90473de02bed5fb8dc641...` | `sha256:d264a0c1ebbf11fe675d893f90473de02bed5fb8dc64100746622eddab0f2b2e` | `304152be61a790c2e92f40f36b1db2b4e6ff11c6` | Deployment/one-ui-shell |
| UNKNOWN | `127.0.0.1:5000/impilo/organization-registry-service@sha256:c821e41e5d961a75d0f1392148f7...` | `sha256:c821e41e5d961a75d0f1392148f7df92e5c4cb4f1776ebf496977af05450dcad` | `UNKNOWN` | Deployment/organization-registry-service |
| UNKNOWN | `127.0.0.1:5000/impilo/oros-service@sha256:71eaf434f9634d935b7c0b667d7fce99a6edf69c2a870...` | `sha256:71eaf434f9634d935b7c0b667d7fce99a6edf69c2a8707b5d4e1951c15c3ad1c` | `UNKNOWN` | Deployment/oros-service |
| UNKNOWN | `127.0.0.1:5000/impilo/pacs-adapter-service@sha256:b612e8dee4de47449cbf948cc08d9e5b8dc98...` | `sha256:b612e8dee4de47449cbf948cc08d9e5b8dc9892938e5e6937917e43a408383d8` | `UNKNOWN` | Deployment/pacs-adapter-service |
| UNKNOWN | `127.0.0.1:5000/impilo/participation-service@sha256:eb12b6a933bfe6d4ae92d1c55e0f208a580e...` | `sha256:eb12b6a933bfe6d4ae92d1c55e0f208a580e1485d791dc08a36476c25320129d` | `UNKNOWN` | Deployment/participation-service |
| UNKNOWN | `127.0.0.1:5000/impilo/patient-safety-service@sha256:1958481665802f496a6b3ec01a37f19a1d0...` | `sha256:1958481665802f496a6b3ec01a37f19a1d0efcba6423a292455df6ee29833af4` | `UNKNOWN` | Deployment/patient-safety-service |
| UNKNOWN | `127.0.0.1:5000/impilo/pct-service@sha256:364c104c80ee19448a4082a91a4b1ca380fb3faf5807b7...` | `sha256:364c104c80ee19448a4082a91a4b1ca380fb3faf5807b73034d9318f098a2775` | `UNKNOWN` | Deployment/pct-service |
| UNKNOWN | `127.0.0.1:5000/impilo/pharmacy-elmis-adapter@sha256:8a008d6c1807e872ffdb35d69a28dd69a61...` | `sha256:8a008d6c1807e872ffdb35d69a28dd69a6124a8de8721780017b27418ad56007` | `UNKNOWN` | Deployment/pharmacy-elmis-adapter |
| UNKNOWN | `127.0.0.1:5000/impilo/pharmacy-service@sha256:9206eea3de5302aab530761c7a9f7d488f3ebdf7e...` | `sha256:9206eea3de5302aab530761c7a9f7d488f3ebdf7e123bc88fd5b275006ac3c4a` | `UNKNOWN` | Deployment/pharmacy-service |
| UNKNOWN | `127.0.0.1:5000/impilo/procurement-service@sha256:33b6f63c90177bd6ee019e415f0ecfc4d5e6c4...` | `sha256:33b6f63c90177bd6ee019e415f0ecfc4d5e6c4bf8d2dc8b414f90e7e48aeec19` | `UNKNOWN` | Deployment/procurement-service |
| UNKNOWN | `127.0.0.1:5000/impilo/product-registry-service@sha256:8759cda4abb1eb2d43e35cf8db55fe36f...` | `sha256:8759cda4abb1eb2d43e35cf8db55fe36f7c17132d1c43a3956856fb8ff1e4c72` | `UNKNOWN` | Deployment/product-registry-service |
| UNKNOWN | `127.0.0.1:5000/impilo/public-website@sha256:de1235a219801a1e37569ff6963d0d7c2c2bebde00b...` | `sha256:de1235a219801a1e37569ff6963d0d7c2c2bebde00b888edd670eee46921458e` | `UNKNOWN` | Deployment/public-website |
| UNKNOWN | `127.0.0.1:5000/impilo/referral-service@sha256:d3866f284cc002c986b46715512d33e5708266dc5...` | `sha256:d3866f284cc002c986b46715512d33e5708266dc5bca8dd5610bcc12d3371d7e` | `UNKNOWN` | Deployment/referral-service |
| UNKNOWN | `127.0.0.1:5000/impilo/reporting-service@sha256:000d695d570fc5ff617e6d1c411f527639ae1b92...` | `sha256:000d695d570fc5ff617e6d1c411f527639ae1b92c9067bb97eb7ab1b15c425b1` | `UNKNOWN` | Deployment/reporting-service |
| UNKNOWN | `127.0.0.1:5000/impilo/rito-quality-safety-service@sha256:704ec0bd657485e4109543d96ce941...` | `sha256:704ec0bd657485e4109543d96ce94184c74f7bc41d8a695662dc6a20dd4e41dc` | `UNKNOWN` | Deployment/rito-quality-safety-service |
| UNKNOWN | `127.0.0.1:5000/impilo/rtc-gateway-service@sha256:a8037e48d62b3a5d48a6fe57c1db2af764b024...` | `sha256:a8037e48d62b3a5d48a6fe57c1db2af764b02450da6dae1cf72ec66d9e8e307f` | `UNKNOWN` | Deployment/rtc-gateway-service |
| UNKNOWN | `127.0.0.1:5000/impilo/rules-service@sha256:4347054f437afa32a70c0048a205adbf0b96b52df5cd...` | `sha256:4347054f437afa32a70c0048a205adbf0b96b52df5cd844dd1dfc9b2d9cd74ef` | `UNKNOWN` | Deployment/rules-service |
| UNKNOWN | `127.0.0.1:5000/impilo/scheduling-service@sha256:04b6e89ab8779bd375c016e0e167ea430d0749c...` | `sha256:04b6e89ab8779bd375c016e0e167ea430d0749cacaff272a4ad5bdcef03008f2` | `UNKNOWN` | Deployment/scheduling-service |
| UNKNOWN | `127.0.0.1:5000/impilo/schema-registry-service@sha256:fea0933bfb4ccd5c5da8e882e5b0ac07db...` | `sha256:fea0933bfb4ccd5c5da8e882e5b0ac07dbc151ce08114248612fde4638a3fc91` | `UNKNOWN` | Deployment/schema-registry-service |
| UNKNOWN | `127.0.0.1:5000/impilo/search-service@sha256:4d36474e3d24d159bac97ed2fa07d4766943149f08c...` | `sha256:4d36474e3d24d159bac97ed2fa07d4766943149f08c511363958012ad543131a` | `UNKNOWN` | Deployment/search-service |
| UNKNOWN | `127.0.0.1:5000/impilo/security-hardening-service@sha256:482a84f24951081d87a76d362c4258e...` | `sha256:482a84f24951081d87a76d362c4258e3e7384583b67cb4c1659667fd3714aaf4` | `UNKNOWN` | Deployment/security-hardening-service |
| UNKNOWN | `127.0.0.1:5000/impilo/share-slip-service@sha256:c2427005dcdeaa608d5888c77618b47572ccbf3...` | `sha256:c2427005dcdeaa608d5888c77618b47572ccbf3ec889d3bb31261facee1a8d6a` | `UNKNOWN` | Deployment/share-slip-service |
| UNKNOWN | `127.0.0.1:5000/impilo/simba-service@sha256:cd82b50d003242d90cb5b8307300fa8ceb767f5beb61...` | `sha256:cd82b50d003242d90cb5b8307300fa8ceb767f5beb619457073ae7b600c3b843` | `UNKNOWN` | Deployment/simba-service |
| UNKNOWN | `127.0.0.1:5000/impilo/support-service@sha256:2ed21c83c58c0ecb6956892265cb1c00c42b0342bc...` | `sha256:2ed21c83c58c0ecb6956892265cb1c00c42b0342bc06fc7e2a3366401d8a5a2b` | `UNKNOWN` | Deployment/support-service |
| UNKNOWN | `127.0.0.1:5000/impilo/surveillance-service@sha256:423d0f2746cf6e76f675583cc8cdf596fc5f9...` | `sha256:423d0f2746cf6e76f675583cc8cdf596fc5f95612f55c7967a1a529249f5654d` | `UNKNOWN` | Deployment/surveillance-service |
| UNKNOWN | `127.0.0.1:5000/impilo/telemonitoring-service@sha256:8442e18480eaf91eae273a6c025d112f8fc...` | `sha256:8442e18480eaf91eae273a6c025d112f8fc4384ff106bb5ea557fe58dcb89edb` | `UNKNOWN` | Deployment/telemonitoring-service |
| MAPPED | `127.0.0.1:5000/impilo/tshepo-audit-service@sha256:bba09c3926e5106fa72358a2d731bef2f91a1...` | `sha256:bba09c3926e5106fa72358a2d731bef2f91a154b9c92f87499d83a7327b38bd5` | `3f42627f7745e87e748f648166b2509b3fc98580` | Deployment/tshepo-audit-service |
| MAPPED | `127.0.0.1:5000/impilo/tshepo-authz-service@sha256:4da33b6f60ae10e647261fc908b2687c3dfb9...` | `sha256:4da33b6f60ae10e647261fc908b2687c3dfb9c08ef1a4110f2c4b8a41a058e82` | `07b8674a89eeea696a0a48e1cc406ca46a3bd775` | Deployment/tshepo-authz-service |
| UNKNOWN | `127.0.0.1:5000/impilo/tshepo-consent-service@sha256:42add39afe9af9065d160c8dbd6072025ba...` | `sha256:42add39afe9af9065d160c8dbd6072025bab9a7f995708fc3f478e9bdd0229db` | `UNKNOWN` | Deployment/tshepo-consent-service |
| UNKNOWN | `127.0.0.1:5000/impilo/tshepo-identity-service@sha256:8277bb5e8f8b5a759f96e0ea9068d86041...` | `sha256:8277bb5e8f8b5a759f96e0ea9068d86041baf133cfe12b49b73075fa1956ba47` | `UNKNOWN` | Deployment/tshepo-identity-service |
| UNKNOWN | `127.0.0.1:5000/impilo/tshepo-keys-service@sha256:5974067cd6e9f2775f01b00ef13e0205cc82e1...` | `sha256:5974067cd6e9f2775f01b00ef13e0205cc82e1f5af4b1dc2a757d4fb9265fba7` | `UNKNOWN` | Deployment/tshepo-keys-service |
| UNKNOWN | `127.0.0.1:5000/impilo/tshepo-offline-service@sha256:285377523fd1cc828312adb79f8385a9193...` | `sha256:285377523fd1cc828312adb79f8385a9193f37fb6c926f4c58078f1b46a33610` | `UNKNOWN` | Deployment/tshepo-offline-service |
| UNKNOWN | `127.0.0.1:5000/impilo/tuso-service@sha256:f5669dcb3d22a0b6e45b9d11e1df9d8231ad88c063b8b...` | `sha256:f5669dcb3d22a0b6e45b9d11e1df9d8231ad88c063b8bbbe0a56856224357b50` | `UNKNOWN` | Deployment/tuso-service |
| UNKNOWN | `127.0.0.1:5000/impilo/ubomi-service@sha256:ace694d39b9895ace0509a1e57bfe79e77b2db5a5606...` | `sha256:ace694d39b9895ace0509a1e57bfe79e77b2db5a5606ca5f48b557455fae99ac` | `UNKNOWN` | Deployment/ubomi-service |
| UNKNOWN | `127.0.0.1:5000/impilo/varapi-service@sha256:216b86409a0a8444449afc83761ccf35208c1025305...` | `sha256:216b86409a0a8444449afc83761ccf35208c1025305597b6b2174691d69c6583` | `UNKNOWN` | Deployment/varapi-service |
| UNKNOWN | `127.0.0.1:5000/impilo/vashandi-workforce-service@sha256:18e79d0f84a202c714601d50d42943b...` | `sha256:18e79d0f84a202c714601d50d42943bd1d589db9f8f1c75bbcab64d5bf3d21d8` | `UNKNOWN` | Deployment/vashandi-workforce-service |
| UNKNOWN | `127.0.0.1:5000/impilo/vito-service@sha256:2909c790f1f8f09194a4d1423bf1312f490a0da8c3d13...` | `sha256:2909c790f1f8f09194a4d1423bf1312f490a0da8c3d13c7214fddbdc3893ba4b` | `UNKNOWN` | Deployment/vito-service |
| UNKNOWN | `127.0.0.1:5000/impilo/wellness-service@sha256:e510f1c73132047d96dce1d37a398d6b6695d8320...` | `sha256:e510f1c73132047d96dce1d37a398d6b6695d832052b82d43709ccfef30a5d58` | `UNKNOWN` | Deployment/wellness-service |
| UNKNOWN | `127.0.0.1:5000/impilo/workflow-service@sha256:d0d4c4519275594539d07addf2a754868375468d8...` | `sha256:d0d4c4519275594539d07addf2a754868375468d884ea3d510bcef9730eb7dae` | `UNKNOWN` | Deployment/workflow-service |
| UNKNOWN | `127.0.0.1:5000/impilo/workforce-governance-service@sha256:283c2ef8cbcb6d59e8314d6e17ff4...` | `sha256:283c2ef8cbcb6d59e8314d6e17ff4568915b25f47ac8e8631e6a089e3844a0c1` | `UNKNOWN` | Deployment/workforce-governance-service |
| UNKNOWN | `127.0.0.1:5000/impilo/zibo-service@sha256:ef58e0db5b12b49a7bb2150feaaaaabb6b93d4ef79fc6...` | `sha256:ef58e0db5b12b49a7bb2150feaaaaabb6b93d4ef79fc61c9adc3f095ae72eccf` | `UNKNOWN` | Deployment/zibo-service |
| UNKNOWN | `apache/kafka:3.7.1` | `UNKNOWN` | `UNKNOWN` | Deployment/kafka |
| VENDOR_EXTERNAL | `busybox:1.36` | `UNKNOWN` | `N/A (vendor image)` | Job/keycloak-h2-snapshot-mfa-20260801-0313 |
| VENDOR_EXTERNAL | `envoyproxy/envoy:v1.31-latest` | `UNKNOWN` | `N/A (vendor image)` | Deployment/envoy |
| VENDOR_EXTERNAL | `ghcr.io/maplibre/martin:v0.15.0` | `sha256:9bf94e0c214563bf34ffc35e7e3cdb1520b090f9516d86fcf01eff4ffe369c45` | `N/A (vendor image)` | Deployment/ndila-martin |
| UNKNOWN | `hapiproject/hapi:v7.4.0` | `UNKNOWN` | `UNKNOWN` | Deployment/hapi-fhir |
| UNKNOWN | `minio/minio:latest` | `UNKNOWN` | `UNKNOWN` | Deployment/minio |
| VENDOR_EXTERNAL | `mirror.gcr.io/curlimages/curl:8.11.1` | `sha256:c1fe1679c34d9784c1b0d1e5f62ac0a79fca01fb6377cdd33e90473c6f9f9a69` | `N/A (vendor image)` | CronJob/estate-health-watch, Job/estate-health-watch-29756370, Job/estate-health-watch-29756850 (+1) |
| VENDOR_EXTERNAL | `mirror.gcr.io/jodogne/orthanc-plugins:1.12.4` | `sha256:673c55d1844067a955fdafc92657c179d64f275fea738755c5667fc9a7d57da7` | `N/A (vendor image)` | Deployment/orthanc |
| VENDOR_EXTERNAL | `mirror.gcr.io/livekit/egress:v1.13.0` | `sha256:980ff439431df2c773573721ab6da19e15bdc1f049ab7cb80e87470bf174c12f` | `N/A (vendor image)` | Deployment/livekit-egress |
| VENDOR_EXTERNAL | `mirror.gcr.io/livekit/livekit-server:v1.13.3` | `sha256:483b8b7b5b0654f91f1e8bdc7b46fcd37fd9911612ecf627f97e3185a89825bd` | `N/A (vendor image)` | Deployment/livekit |
| VENDOR_EXTERNAL | `mirror.gcr.io/minio/mc:RELEASE.2025-08-13T08-35-41Z` | `UNKNOWN` | `N/A (vendor image)` | Job/livekit-egress-bucket-init |
| VENDOR_EXTERNAL | `postgres:16-alpine` | `UNKNOWN` | `N/A (vendor image)` | Deployment/postgres, CronJob/postgres-backup, Job/postgres-backup-29756130 (+2) |
| VENDOR_EXTERNAL | `quay.io/keycloak/keycloak:25.0` | `sha256:82c5b7a110456dbd42b86ea572e728878549954cc8bd03cd65410d75328095d2` | `N/A (vendor image)` | Job/keycloak-h2-export-mfa-20260801-0313 |
| VENDOR_EXTERNAL | `quay.io/keycloak/keycloak@sha256:82c5b7a110456dbd42b86ea572e728878549954cc8bd03cd65410d...` | `sha256:82c5b7a110456dbd42b86ea572e728878549954cc8bd03cd65410d75328095d2` | `N/A (vendor image)` | Job/keycloak-pg25-import-mfa-20260801-0313 |
| VENDOR_EXTERNAL | `redis:7-alpine` | `UNKNOWN` | `N/A (vendor image)` | Deployment/redis |

## Interpretation

- Preview images that carry labels were built from branch `codex/mfa-production`, **not** from `claude/tshepo-trust-cp1-truth-audit`. Do not claim this documenting branch is what preview runs.
- Majority of Impilo service images are bare `sha256:` IDs or unlabeled local digests → **UNKNOWN** provenance until labels are restored in the image build pipeline.
- Mutable tag exception: `matcher-engine:preview` (tag-pinned, not digest-pinned).
