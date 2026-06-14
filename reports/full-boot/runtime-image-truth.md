# Runtime Image Truth

> Deployment truth is the running estate, not the deployment story.

- Namespace: `impilo-full-preview` | Registry: `127.0.0.1:5000` | Phase: `post-rollout`
- Runtime services checked: **91**
- Stale non-exempt services: **89**

| service | aligned | reason | expected_digest | registry | pod_imageID | deploy_ref |
|---|---|---|---|---|---|---|
| ai-model-registry-service | NO | stale_application_service |  | sha256:f6cca9c0053d | sha256:2517988e9b2f | 127.0.0.1:5000/impilo/ai-model-registry-service:preview |
| analytics-pipeline-service | NO | stale_application_service |  | sha256:2169d5dd2645 | sha256:2eede28a5d1d | 127.0.0.1:5000/impilo/analytics-pipeline-service:preview |
| asset-registry-service | NO | stale_application_service |  | sha256:679ee8f152d4 | sha256:997dbf79fd72 | 127.0.0.1:5000/impilo/asset-registry-service:preview |
| audit-ledger-service | NO | stale_application_service |  | sha256:e552060eee25 | sha256:9b325eea0c6d | 127.0.0.1:5000/impilo/audit-ledger-service:preview |
| booking-service | NO | stale_application_service |  | sha256:2730c77de1f8 | sha256:52406527ab53 | 127.0.0.1:5000/impilo/booking-service:preview |
| butano-fhir | NO | stale_application_service |  | sha256:50e657754dcd | sha256:092d981ac2da | 127.0.0.1:5000/impilo/butano-fhir:preview |
| butano-service | NO | stale_application_service |  | sha256:0d5bba330205 | sha256:e09f4a30501e | 127.0.0.1:5000/impilo/butano-service:preview |
| campaigns-service | NO | stale_application_service |  | sha256:7f0fa9ad9087 | sha256:6dff5eb56ae6 | 127.0.0.1:5000/impilo/campaigns-service:preview |
| card-print-agent | NO | stale_application_service |  | sha256:58e4d9975708 | sha256:1484b2f3b2b5 | 127.0.0.1:5000/impilo/card-print-agent:preview |
| channels-service | NO | stale_application_service |  | sha256:f0f8ae50a7c4 | sha256:54b7f70b23dc | 127.0.0.1:5000/impilo/channels-service:preview |
| clinical-knowledge-platform-service | NO | stale_application_service |  | sha256:4bde67c1348c | sha256:82e807808847 | 127.0.0.1:5000/impilo/clinical-knowledge-platform-service:preview |
| community-service | NO | stale_application_service |  | sha256:699c831d58d1 | sha256:674325f0d2eb | 127.0.0.1:5000/impilo/community-service:preview |
| connector-fhir-adapter | NO | stale_application_service |  | sha256:c69a38759544 | sha256:36e6a79b0118 | 127.0.0.1:5000/impilo/connector-fhir-adapter:preview |
| costing-engine-service | NO | stale_application_service |  | sha256:670458cc8fe0 | sha256:3b93e3c8a369 | 127.0.0.1:5000/impilo/costing-engine-service:preview |
| coverage-service | NO | stale_application_service |  | sha256:740fb7d40130 | sha256:76c976d3db78 | 127.0.0.1:5000/impilo/coverage-service:preview |
| credential-verification-service | NO | stale_application_service |  | sha256:a8eff248c2c9 | sha256:c87ef868d169 | 127.0.0.1:5000/impilo/credential-verification-service:preview |
| data-access-governance-service | NO | stale_application_service |  | sha256:c928e878c614 | sha256:90c448b57ba2 | 127.0.0.1:5000/impilo/data-access-governance-service:preview |
| data-governance-service | NO | stale_application_service |  | sha256:0e9853480b69 | sha256:1765ffce6ba1 | 127.0.0.1:5000/impilo/data-governance-service:preview |
| data-ingestion-service | NO | stale_application_service |  | sha256:712180f60bf7 | sha256:6d600daefe8d | 127.0.0.1:5000/impilo/data-ingestion-service:preview |
| data-pipeline-service | NO | stale_application_service |  | sha256:996bf47bc8ae | sha256:39fa40c14bda | 127.0.0.1:5000/impilo/data-pipeline-service:preview |
| data-warehouse-service | NO | stale_application_service |  | sha256:c4f4fe31707c | sha256:2a6ee1df472e | 127.0.0.1:5000/impilo/data-warehouse-service:preview |
| developer-portal-service | NO | stale_application_service |  | sha256:50bf337e4821 | sha256:d81cc37c6d20 | 127.0.0.1:5000/impilo/developer-portal-service:preview |
| dispatch-service | NO | stale_application_service |  | sha256:1e88cd3c6123 | sha256:05465a277dd6 | 127.0.0.1:5000/impilo/dispatch-service:preview |
| document-service | NO | stale_application_service |  | sha256:9f674a4a29ee | sha256:d5ad702afea0 | 127.0.0.1:5000/impilo/document-service:preview |
| experience-bff | YES | aligned |  | sha256:11bd1e2d7cc8 | sha256:11bd1e2d7cc8 | 127.0.0.1:5000/impilo/experience-bff@sha256:11bd1e2d7cc84ccde136557c51ba488d76f5f31a9b1b36c8eca0caa1e032b730 |
| fhir-gateway-service | NO | stale_application_service |  | sha256:0b6e724bfb7b | sha256:8d26b4c133cf | 127.0.0.1:5000/impilo/fhir-gateway-service:preview |
| forms-service | NO | stale_application_service |  | sha256:aa38d7d36b9f | sha256:25d4ef26fb6e | 127.0.0.1:5000/impilo/forms-service:preview |
| general-ledger-service | NO | stale_application_service |  | sha256:2d348ab47f1c | sha256:2e26396b34e0 | 127.0.0.1:5000/impilo/general-ledger-service:preview |
| guidance-service | NO | stale_application_service |  | sha256:92237e9dbfd4 | sha256:0fad7ae3b84c | 127.0.0.1:5000/impilo/guidance-service:preview |
| hr-payroll-service | NO | stale_application_service |  | sha256:db07ae9b095a | sha256:07cbf24fbaf4 | 127.0.0.1:5000/impilo/hr-payroll-service:preview |
| identity-assurance-service | NO | stale_application_service |  | sha256:77ae2c361230 | sha256:782f3c53c7c0 | 127.0.0.1:5000/impilo/identity-assurance-service:preview |
| indawo-service | NO | stale_application_service |  | sha256:c3703dad5e30 | sha256:8ffe3123d68a | 127.0.0.1:5000/impilo/indawo-service:preview |
| inpatient-service | NO | stale_application_service |  | sha256:2cb34e935911 | sha256:05780737fe1b | 127.0.0.1:5000/impilo/inpatient-service:preview |
| integration-hub | NO | stale_application_service |  | sha256:5081e51ab0ff | sha256:1fffa06786c1 | 127.0.0.1:5000/impilo/integration-hub:preview |
| inventory-elmis-adapter | NO | stale_application_service |  | sha256:fbc135a8d7ab | sha256:58d02d529ac2 | 127.0.0.1:5000/impilo/inventory-elmis-adapter:preview |
| inventory-service | NO | stale_application_service |  | sha256:fac7c1510b3b | sha256:32297bcb9b17 | 127.0.0.1:5000/impilo/inventory-service:preview |
| iot-ingestion-service | NO | stale_application_service |  | sha256:acda3dee3c72 | sha256:055fbc19b3c3 | 127.0.0.1:5000/impilo/iot-ingestion-service:preview |
| jobs-service | NO | stale_application_service |  | sha256:c7704987f521 | sha256:891970595837 | 127.0.0.1:5000/impilo/jobs-service:preview |
| landela-adapter-service | NO | stale_application_service |  | sha256:c20daffbdf05 | sha256:5f17ace651cf | 127.0.0.1:5000/impilo/landela-adapter-service:preview |
| learning-service | NO | stale_application_service |  | sha256:41d055530dd4 | sha256:7684663c6561 | 127.0.0.1:5000/impilo/learning-service:preview |
| live-service | NO | stale_application_service |  | sha256:a50c370d39d9 | sha256:5eecec58a03c | 127.0.0.1:5000/impilo/live-service:preview |
| llm-orchestration-service | NO | stale_application_service |  | sha256:1694bbb3a108 | sha256:85fd92868076 | 127.0.0.1:5000/impilo/llm-orchestration-service:preview |
| madi-service | NO | stale_application_service |  | sha256:edfe3bb5b9a3 | sha256:3bbe870c6c0a | 127.0.0.1:5000/impilo/madi-service:preview |
| msika-apps-service | NO | stale_application_service |  | sha256:b0dca77f0d6c | sha256:4aef7a2c9520 | 127.0.0.1:5000/impilo/msika-apps-service:preview |
| msika-flow-service | NO | stale_application_service |  | sha256:340a02e219df | sha256:298bae7d3300 | 127.0.0.1:5000/impilo/msika-flow-service:preview |
| msika-service | NO | stale_application_service |  | sha256:4e26155c551d | sha256:93510a53216c | 127.0.0.1:5000/impilo/msika-service:preview |
| mushe-wallet-service | NO | stale_application_service |  | sha256:93cab47fdf7e | sha256:5f539a5c6f07 | 127.0.0.1:5000/impilo/mushe-wallet-service:preview |
| mushex-service | NO | stale_application_service |  | sha256:9dfed7d483f6 | sha256:08ba76caaa4b | 127.0.0.1:5000/impilo/mushex-service:preview |
| mvumo-service | NO | stale_application_service |  | sha256:11579e5b1dd9 | sha256:7e9fd112f5f9 | 127.0.0.1:5000/impilo/mvumo-service:preview |
| national-data-repository-service | NO | stale_application_service |  | sha256:d8e926882177 | sha256:844252786d0a | 127.0.0.1:5000/impilo/national-data-repository-service:preview |
| ndila-service | NO | stale_application_service |  | sha256:bd90cc001fb8 | sha256:9a0a2bcc583f | 127.0.0.1:5000/impilo/ndila-service:preview |
| ndr-service | NO | stale_application_service |  | sha256:673ddd785c68 | sha256:1ce366680d8e | 127.0.0.1:5000/impilo/ndr-service:preview |
| nhume-service | NO | stale_application_service |  | sha256:98ccd13ca983 | sha256:0923b098fcda | 127.0.0.1:5000/impilo/nhume-service:preview |
| notification-service | NO | stale_application_service |  | sha256:fe8750d3bdf1 | sha256:3469a9399042 | 127.0.0.1:5000/impilo/notification-service:preview |
| observability-service | NO | stale_application_service |  | sha256:880d293ce291 | sha256:0c3e8560e478 | 127.0.0.1:5000/impilo/observability-service:preview |
| offline-edge-service | NO | stale_application_service |  | sha256:f1bef37d3d74 | sha256:d216faeb1580 | 127.0.0.1:5000/impilo/offline-edge-service:preview |
| offline-sync-service | NO | stale_application_service |  | sha256:8c4422c64ff7 | sha256:01939a258730 | 127.0.0.1:5000/impilo/offline-sync-service:preview |
| one-ui-shell | YES | aligned |  | sha256:550ae66b5450 | sha256:550ae66b5450 | 127.0.0.1:5000/impilo/one-ui-shell@sha256:550ae66b5450604cf9e3c27d2a68d730cd821e4ec3e4e79cbd79d74d0783499c |
| oros-service | NO | stale_application_service |  | sha256:93018544ec8c | sha256:5d57ac743014 | 127.0.0.1:5000/impilo/oros-service:preview |
| pacs-adapter-service | NO | stale_application_service |  | sha256:00dd26dc569a | sha256:cbcbd6e6ee7f | 127.0.0.1:5000/impilo/pacs-adapter-service:preview |
| pct-service | NO | stale_application_service |  | sha256:bc669bd12593 | sha256:43324eeb7e45 | 127.0.0.1:5000/impilo/pct-service:preview |
| pharmacy-elmis-adapter | NO | stale_application_service |  | sha256:010960d4b032 | sha256:1bcf0386f79c | 127.0.0.1:5000/impilo/pharmacy-elmis-adapter:preview |
| pharmacy-service | NO | stale_application_service |  | sha256:05f0e95a1af3 | sha256:2d669b818eb2 | 127.0.0.1:5000/impilo/pharmacy-service:preview |
| procurement-service | NO | stale_application_service |  | sha256:ac0366888890 | sha256:6d29c48e8fd6 | 127.0.0.1:5000/impilo/procurement-service:preview |
| product-registry-service | NO | stale_application_service |  | sha256:597209bf603e | sha256:9f5b3c11a5ae | 127.0.0.1:5000/impilo/product-registry-service:preview |
| referral-service | NO | stale_application_service |  | sha256:76a3730cfe82 | sha256:295145af3bda | 127.0.0.1:5000/impilo/referral-service:preview |
| reporting-service | NO | stale_application_service |  | sha256:eb5679bac3ef | sha256:235374205228 | 127.0.0.1:5000/impilo/reporting-service:preview |
| rtc-gateway-service | NO | stale_application_service |  | sha256:d291cb9f1bd4 | sha256:6c2c2aa2a990 | 127.0.0.1:5000/impilo/rtc-gateway-service:preview |
| rules-service | NO | stale_application_service |  | sha256:1c0fa15a7368 | sha256:070bae438978 | 127.0.0.1:5000/impilo/rules-service:preview |
| scheduling-service | NO | stale_application_service |  | sha256:c56160ac0b2e | sha256:37abf1365655 | 127.0.0.1:5000/impilo/scheduling-service:preview |
| schema-registry-service | NO | stale_application_service |  | sha256:19f7b51dd533 | sha256:a4c868db73af | 127.0.0.1:5000/impilo/schema-registry-service:preview |
| search-service | NO | stale_application_service |  | sha256:1d7d25f4a6a0 | sha256:323d9bf5f733 | 127.0.0.1:5000/impilo/search-service:preview |
| security-hardening-service | NO | stale_application_service |  | sha256:50e08f45ec06 | sha256:1c0d8a09e62c | 127.0.0.1:5000/impilo/security-hardening-service:preview |
| share-slip-service | NO | stale_application_service |  | sha256:4f8193ddf936 | sha256:749d0c290768 | 127.0.0.1:5000/impilo/share-slip-service:preview |
| simba-service | NO | stale_application_service |  | sha256:d20448b1beee | sha256:2f8f501cbf3f | 127.0.0.1:5000/impilo/simba-service:preview |
| support-service | NO | stale_application_service |  | sha256:a1fda4cb85a4 | sha256:5befeebdced0 | 127.0.0.1:5000/impilo/support-service:preview |
| surveillance-service | NO | stale_application_service |  | sha256:e5be43f3b0b6 | sha256:10ffc8780a18 | 127.0.0.1:5000/impilo/surveillance-service:preview |
| tshepo-audit-service | NO | stale_application_service |  | sha256:9915f2e37982 | sha256:63e43270347d | 127.0.0.1:5000/impilo/tshepo-audit-service:preview |
| tshepo-authz-service | NO | stale_application_service |  | sha256:d6fa09c41155 | sha256:84009097b0cb | 127.0.0.1:5000/impilo/tshepo-authz-service:preview |
| tshepo-consent-service | NO | stale_application_service |  | sha256:c8b057c70dab | sha256:8d29a2468a69 | 127.0.0.1:5000/impilo/tshepo-consent-service:preview |
| tshepo-identity-service | NO | stale_application_service |  | sha256:00b67ce64138 | sha256:1508cc1f19c3 | 127.0.0.1:5000/impilo/tshepo-identity-service:preview |
| tshepo-keys-service | NO | stale_application_service |  | sha256:52e4699b2da8 | sha256:bebd2117be32 | 127.0.0.1:5000/impilo/tshepo-keys-service:preview |
| tshepo-offline-service | NO | stale_application_service |  | sha256:626dc1a5654c | sha256:542d554fc763 | 127.0.0.1:5000/impilo/tshepo-offline-service:preview |
| tuso-service | NO | stale_application_service |  | sha256:3dd63d044f74 | sha256:8f87fddc40f3 | 127.0.0.1:5000/impilo/tuso-service:preview |
| ubomi-service | NO | stale_application_service |  | sha256:cb40faccbabe | sha256:5d6fdeec10fb | 127.0.0.1:5000/impilo/ubomi-service:preview |
| varapi-service | NO | stale_application_service |  | sha256:f8e8cdc37d85 | sha256:a6462f5a4260 | 127.0.0.1:5000/impilo/varapi-service:preview |
| vito-service | NO | stale_application_service |  | sha256:223b60e80c32 | sha256:96b29f7ca758 | 127.0.0.1:5000/impilo/vito-service:preview |
| wellness-service | NO | stale_application_service |  | sha256:1d388f918154 | sha256:8ba7a3994df8 | 127.0.0.1:5000/impilo/wellness-service:preview |
| workflow-service | NO | stale_application_service |  | sha256:2668d22c14b9 | sha256:ae614bcb9649 | 127.0.0.1:5000/impilo/workflow-service:preview |
| workforce-governance-service | NO | stale_application_service |  | sha256:9d5865492433 | sha256:ab5497609243 | 127.0.0.1:5000/impilo/workforce-governance-service:preview |
| zibo-service | NO | stale_application_service |  | sha256:b95f509cc5e9 | sha256:22c3fee16116 | 127.0.0.1:5000/impilo/zibo-service:preview |
