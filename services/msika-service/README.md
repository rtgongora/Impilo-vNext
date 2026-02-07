# MSIKA Core — National Products & Services Registry

National registry of health products, services, orderables, chargeables, and capability taxonomies for the Impilo platform.

## Architecture

MSIKA Core provides canonical definitions, codes, metadata, restrictions, and validation hooks consumed by:

| Consumer | Pack Endpoint | Purpose |
|----------|--------------|---------|
| **OROS** | `/v1/packs/orderables` | Lab/imaging/pharmacy/procedure orderables |
| **Pharmacy + Inventory** | `/v1/packs/item-master` | Product/item master, barcode, batch/expiry |
| **Costing/Billing + MUSHEX** | `/v1/packs/chargeables` | Charge codes, tariffs, cost method refs |
| **TUSO** | `/v1/packs/capabilities/facility` | Facility capability taxonomy |
| **VARAPI** | `/v1/packs/capabilities/provider` | Provider privilege taxonomy |
| **Citizen Portal** | `/v1/search` | Read-only browse/search |

### Item Kinds

- `PRODUCT` — health products, commodities, consumables
- `SERVICE` — clinical and non-clinical services
- `ORDERABLE` — order definitions (references product or service)
- `CHARGEABLE` — billing/costing entries (references product or service)
- `CAPABILITY_FACILITY` — facility capability definitions
- `CAPABILITY_PROVIDER` — provider capability/privilege definitions

### Versioned Catalogs

- **NATIONAL** baseline — immutable published snapshots with governance workflow
- **TENANT** overlay — local additions/overrides, cannot weaken national restrictions
- Semantic versioning (MAJOR.MINOR.PATCH)
- Consumer pinning to specific version or "latest published"
- Offline packs with integrity checksums

### Catalog Lifecycle

```
DRAFT → REVIEW → APPROVED → PUBLISHED
```

- `DRAFT`: Items can be added/edited
- `REVIEW`: Submitted for governance review
- `APPROVED`: Ready for publication (step-up auth required to publish)
- `PUBLISHED`: Immutable, consumers can pin to this version

## Local Development

### Prerequisites

```bash
docker compose up -d  # PostgreSQL, Kafka, Redis, Keycloak
```

### Run the service

```bash
cd services/msika-service
mvn spring-boot:run
```

Service starts on **port 8086**.

### Run the web UI

```bash
cd ui/msika-web
npm install
npm run dev
```

UI starts on **port 3012**.

## Sample curl Flows

### 1. Create a catalog

```bash
curl -X POST http://localhost:8086/v1/catalogs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: admin-1" \
  -H "X-Actor-Type: ADMIN" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: ADMIN" \
  -H "X-Device-Fingerprint: curl-test" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "name": "Zimbabwe National Formulary",
    "description": "National baseline product and service catalog",
    "scope": "NATIONAL",
    "version": "1.0.0"
  }'
```

### 2. Add items to the catalog

```bash
CATALOG_ID="<catalogId from step 1>"

# Add a product
curl -X POST "http://localhost:8086/v1/catalogs/${CATALOG_ID}/items" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: admin-1" \
  -H "X-Actor-Type: ADMIN" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: ADMIN" \
  -H "X-Device-Fingerprint: curl-test" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "kind": "PRODUCT",
    "canonicalCode": "PARA-500",
    "displayName": "Paracetamol 500mg Tablets",
    "description": "Analgesic/antipyretic",
    "synonyms": ["Acetaminophen", "Panado"],
    "tags": ["analgesic", "essential-medicine"],
    "restrictions": {
      "prescription_required": { "enabled": false },
      "cold_chain_required": { "enabled": false }
    },
    "ziboBindings": [
      { "system": "http://zibo.mohcc.gov.zw/cs/medications", "code": "PARA-001", "display": "Paracetamol" }
    ],
    "product": {
      "form": "Tablet",
      "strength": "500mg",
      "route": "Oral",
      "uom": "TAB",
      "packSize": 100,
      "barcode": "6001234567890",
      "batchTracked": true,
      "expiryTracked": true,
      "manufacturer": "Varichem Pharmaceuticals"
    }
  }'

# Add an orderable (links to the product)
curl -X POST "http://localhost:8086/v1/catalogs/${CATALOG_ID}/items" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: admin-1" \
  -H "X-Actor-Type: ADMIN" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: ADMIN" \
  -H "X-Device-Fingerprint: curl-test" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "kind": "ORDERABLE",
    "canonicalCode": "ORD-PARA-500",
    "displayName": "Order: Paracetamol 500mg",
    "orderable": {
      "orderType": "PHARMACY",
      "targetKind": "PRODUCT",
      "targetItemId": "<itemId of PARA-500>"
    }
  }'
```

### 3. CSV Import

```bash
curl -X POST "http://localhost:8086/v1/import/csv?catalogId=${CATALOG_ID}" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: admin-1" \
  -H "X-Actor-Type: ADMIN" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: ADMIN" \
  -H "X-Device-Fingerprint: curl-test" \
  -H "Authorization: Bearer <token>" \
  -F "file=@products.csv"
```

CSV format:
```csv
canonical_code,display_name,kind,description,form,strength,route,uom,pack_size,barcode,manufacturer,atc_code,tags
AMOX-500,Amoxicillin 500mg Capsules,PRODUCT,Antibiotic,Capsule,500mg,Oral,CAP,100,6001234567891,Varichem,J01CA04,antibiotic;essential-medicine
```

### 4. Approve a mapping

```bash
curl -X POST "http://localhost:8086/v1/mappings/${MAPPING_ID}/approve" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: admin-1" \
  -H "X-Actor-Type: ADMIN" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: ADMIN" \
  -H "X-Device-Fingerprint: curl-test" \
  -H "Authorization: Bearer <token>"
```

### 5. Publish the catalog

```bash
# Submit for review
curl -X POST "http://localhost:8086/v1/catalogs/${CATALOG_ID}/submit-review" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: admin-1" \
  -H "X-Actor-Type: ADMIN" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: ADMIN" \
  -H "X-Device-Fingerprint: curl-test" \
  -H "Authorization: Bearer <token>"

# Approve
curl -X POST "http://localhost:8086/v1/catalogs/${CATALOG_ID}/approve" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: admin-1" \
  -H "X-Actor-Type: ADMIN" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: ADMIN" \
  -H "X-Device-Fingerprint: curl-test" \
  -H "Authorization: Bearer <token>"

# Publish (requires step-up auth in production)
curl -X POST "http://localhost:8086/v1/catalogs/${CATALOG_ID}/publish" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: admin-1" \
  -H "X-Actor-Type: ADMIN" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: ADMIN" \
  -H "X-Device-Fingerprint: curl-test" \
  -H "Authorization: Bearer <token>"
```

### 6. Retrieve packs

```bash
# Orderables pack (for OROS)
curl "http://localhost:8086/v1/packs/orderables?tenantId=00000000-0000-0000-0000-000000000001" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: oros-service" \
  -H "X-Actor-Type: SERVICE" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: OPERATIONS" \
  -H "X-Device-Fingerprint: oros-service"

# With ETag caching
curl "http://localhost:8086/v1/packs/item-master?tenantId=00000000-0000-0000-0000-000000000001" \
  -H "If-None-Match: \"<etag-from-previous-response>\"" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: pharmacy-service" \
  -H "X-Actor-Type: SERVICE" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: OPERATIONS" \
  -H "X-Device-Fingerprint: pharmacy-service"
# Returns 304 Not Modified if pack unchanged
```

### 7. Rollback to previous version

```bash
curl -X POST "http://localhost:8086/v1/catalogs/${CATALOG_ID}/rollback/1.0.0" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: admin-1" \
  -H "X-Actor-Type: ADMIN" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: ADMIN" \
  -H "X-Device-Fingerprint: curl-test" \
  -H "Authorization: Bearer <token>"
```

### 8. Search

```bash
curl "http://localhost:8086/v1/search?q=paracetamol&kind=PRODUCT" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: citizen-portal" \
  -H "X-Actor-Type: SYSTEM" \
  -H "X-Correlation-Id: $(uuidgen)" \
  -H "X-Purpose-Of-Use: BROWSE" \
  -H "X-Device-Fingerprint: portal-web"
```

## Tenant Overlay Merge Rules

When generating packs, MSIKA applies these overlay rules:

1. **National baseline items load first** — all items from PUBLISHED NATIONAL catalogs
2. **Tenant overlay items merge on top** — tenant items override national items with the same `canonical_code`
3. **Restrictions cannot be weakened** — tenant overlays cannot remove national restrictions unless explicitly allowed by policy
4. **Additive by default** — tenants can add new items not in the national baseline
5. **Version pinning** — consumers specify version or receive latest published

### Merge priority (highest wins):
```
Tenant overlay (specific version) > Tenant overlay (latest) > National baseline (specific version) > National baseline (latest)
```

## Caching Strategy for Packs

Pack endpoints implement HTTP caching:

- **ETag**: SHA-256 checksum of pack contents. Clients send `If-None-Match` to check freshness
- **Cache-Control**: `max-age=1800, must-revalidate` (30 minutes)
- **304 Not Modified**: Returned when ETag matches, saving bandwidth
- **Invalidation**: Pack checksums change when underlying catalogs are republished

### Consumer integration pattern:
```
1. First request: GET /v1/packs/orderables → 200 + ETag header
2. Subsequent: GET /v1/packs/orderables + If-None-Match: "<etag>" → 304 (no body)
3. After catalog publish: GET /v1/packs/orderables + If-None-Match: "<old-etag>" → 200 (new pack)
```

## Kafka Events

| Topic | Event Types | Description |
|-------|------------|-------------|
| `msika.core.catalog.published` | CATALOG_PUBLISHED, CATALOG_APPROVED | Catalog lifecycle events |
| `msika.core.item.changed` | ITEM_CREATED, ITEM_UPDATED, ITEM_DELETED | Item mutation events |
| `msika.core.mapping.approved` | MAPPING_APPROVED | External mapping approved |

All events use the transactional outbox pattern for reliable delivery.

## Database

- **12 tables** with `msika_` prefix in public schema
- Full-text search via PostgreSQL `tsvector` + GIN indexes
- Optimistic locking on catalog items (`lock_version`)
- JSONB for restrictions, ZIBO bindings, metadata
- Change log for full audit trail

## API Documentation

- Swagger UI: http://localhost:8086/swagger-ui.html
- OpenAPI spec: http://localhost:8086/v3/api-docs
- Contract file: `contracts/openapi/msika-core.openapi.yaml`
