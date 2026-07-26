#!/usr/bin/env bash
# Seed a small set of PUBLISHED marketplace listings on the preview estate so the
# public marketplace lane (and the unified discovery "Medicines & products"
# category) returns real goods instead of an empty list.
#
# Uses the SANCTIONED API flow — create → submit → approve → publish — rather than
# direct SQL, so every listing writes its outbox event and msika_listing_audit row
# and passes the real publish gates. Items come from the V008 starter catalogue
# (real canonical codes); nothing here invents a product.
#
# Idempotent: exits early when published listings already exist.
#
#   bash scripts/seed/22-seed-msika-marketplace-listings.sh
set -euo pipefail

NS="${IMPILO_PREVIEW_NAMESPACE:-impilo-full-preview}"
TENANT="${IMPILO_SEED_TENANT:-00000000-0000-4000-8000-000000000001}"
SELLER_ID="${IMPILO_SEED_SELLER_ID:-OFAM2PROOFVENDOR0000000001}"
SELLER_NAME="${IMPILO_SEED_SELLER_NAME:-OF-A M2 Proof Pharmacy}"
MSIKA="http://msika-service:8086"
# msika pods carry no curl; oros-service does (same pattern as the runtime-proof rigs).
CURL_POD="deploy/oros-service"

psqld() { kubectl -n "$NS" exec deploy/postgres -- psql -U impilo -d msika -tAc "$1"; }

hdr() { # $1 = actor type
  printf -- '-H|Content-Type: application/json|-H|X-Tenant-Id: %s|-H|X-Actor-Id: msika-seed|-H|X-Actor-Type: %s|-H|X-Purpose-Of-Use: MARKETPLACE|-H|X-Correlation-Id: %s' \
    "$TENANT" "$1" "seed-$(date +%s)-$RANDOM"
}

api() { # $1 = actor type, $2 = method, $3 = path, $4 = body (optional)
  local IFS='|'; read -r -a H <<< "$(hdr "$1")"
  if [[ -n "${4:-}" ]]; then
    kubectl -n "$NS" exec "$CURL_POD" -- curl -sS "${H[@]}" -X "$2" "${MSIKA}$3" -d "$4"
  else
    kubectl -n "$NS" exec "$CURL_POD" -- curl -sS "${H[@]}" -X "$2" "${MSIKA}$3"
  fi
}

json_field() { python3 -c "import sys,json;d=json.load(sys.stdin);print((d.get('data') or {}).get('$1',''))" 2>/dev/null || true; }

existing="$(psqld "SELECT count(*) FROM msika_listings WHERE status='PUBLISHED';" | tr -d '[:space:]')"
if [[ "${existing:-0}" -gt 0 ]]; then
  echo "[seed] msika marketplace listings: SKIP (${existing} already published)"
  exit 0
fi

echo "[seed] creating seller storefront ${SELLER_ID}"
SF="$(api MARKETPLACE_SELLER POST /v1/storefronts \
  "{\"sellerType\":\"VENDOR\",\"sellerId\":\"${SELLER_ID}\",\"displayName\":\"${SELLER_NAME}\"}" | json_field storefrontId)"
if [[ -z "$SF" ]]; then
  echo "[seed] FAIL could not create storefront" >&2
  exit 1
fi
api MARKETPLACE_OPERATOR POST "/v1/storefronts/${SF}/verify" \
  '{"verificationStatus":"VERIFIED","notes":"preview seed"}' >/dev/null
echo "[seed] storefront ${SF} verified"

# item_id | title | summary | price | fulfilment  (all real V008 starter-catalogue items)
LISTINGS=(
  "STARTER0000000000000000058|Digital blood pressure machine, upper arm|Automatic upper-arm monitor for home and clinic blood-pressure checks.|48.00|PICKUP"
  "STARTER0000000000000000057|Fingertip pulse oximeter|Measures blood-oxygen saturation and pulse rate at the fingertip.|22.50|PICKUP"
  "STARTER0000000000000000055|Compressor nebuliser machine|Converts liquid medicine into a fine mist for inhalation.|65.00|PICKUP"
  "STARTER0000000000000000053|Nasal oxygen cannula, adult (pack of 50)|Adult nasal cannula for low-flow oxygen delivery.|34.00|DELIVERY"
  "STARTER0000000000000000045|Manual resuscitator (bag-valve-mask), adult|Self-inflating adult resuscitation bag with mask and reservoir.|85.00|PICKUP"
  "STARTER0000000000000000050|Oxygen cylinder 10L with regulator and flowmeter|J-size oxygen cylinder supplied with regulator and flowmeter.|120.00|DELIVERY"
)

published=0
for row in "${LISTINGS[@]}"; do
  IFS='|' read -r item title summary price fulfilment <<< "$row"
  body="$(python3 - "$TENANT" "$SF" "$item" "$SELLER_ID" "$title" "$summary" "$price" "$fulfilment" <<'PY'
import json,sys
t,sf,item,seller,title,summary,price,ful = sys.argv[1:9]
print(json.dumps({"tenantId":t,"storefrontId":sf,"catalogItemId":item,"sellerType":"VENDOR",
                  "sellerId":seller,"title":title,"summary":summary,
                  "priceAmount":float(price),"priceCurrency":"ZWG","fulfilmentMode":ful}))
PY
)"
  lst="$(api MARKETPLACE_SELLER POST /v1/listings "$body" | json_field listingId)"
  if [[ -z "$lst" ]]; then
    echo "[seed] WARN could not create listing for ${item}" >&2
    continue
  fi
  api MARKETPLACE_SELLER   POST "/v1/listings/${lst}/submit"  >/dev/null
  api MARKETPLACE_OPERATOR POST "/v1/listings/${lst}/approve" '{"notes":"preview seed"}' >/dev/null
  api MARKETPLACE_OPERATOR POST "/v1/listings/${lst}/publish" >/dev/null
  published=$((published + 1))
  echo "[seed]   published ${title}"
done

final="$(psqld "SELECT count(*) FROM msika_listings WHERE status='PUBLISHED';" | tr -d '[:space:]')"
echo "[seed] msika marketplace listings: ${published} seeded, ${final} published total"
[[ "${final:-0}" -gt 0 ]] || { echo "[seed] FAIL nothing published" >&2; exit 1; }
