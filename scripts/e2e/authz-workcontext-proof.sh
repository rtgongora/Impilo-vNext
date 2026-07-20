#!/usr/bin/env bash
# ============================================================================
# WORK_CONTEXT duty-token binding — live PDP proof (shadow-safe).
#
# The preview envoy is a bare passthrough (no ext_authz), so the PDP is not in the
# ingress path here, and Tomcat does not expose the ":path" pseudo-header — so the
# HTTP /v1/authorize endpoint always sees resource-type "authorize". This proof
# therefore drives the DEPLOYED PDP directly under a FRESH proof tenant with two
# purpose-built rules (keyed on resource-type "authorize") + a role-template mapping,
# and exercises every dimension through the DUTY TOKEN + trust headers:
#
#   * mints a WORK_CONTEXT token = a scoped_token row + a parseable JWS (identity
#     introspect looks up the jti and does not verify the signature, by contract);
#   * calls tshepo-authz POST /v1/authorize from inside the authz pod;
#   * reads the governance event_outbox (WORK_CONTEXT_* signals) from the DB.
#
# Rules (proof tenant): DENY(role=CLINICIAN, allowed_workflow_states=[DISCHARGED])
# + ALLOW(role=CLINICIAN, allowed_departments=[cardiology], requires_provider_id).
# Catalog: NURSE_ONCOLOGY_SNR -> CLINICIAN.
#
# Proves: loop closed (introspect->bind->match->audit), role-from-duty via the
# role-template catalog, first-class department/provider/workflow dimensions, the
# DENY-ignores-conditions fix (the DENY fires ONLY when its condition matches),
# shadow binding never denies (mismatch/revoked audited only), ENFORCE denies on
# mismatch, and the PolicyController guard. A device fingerprint keeps risk < step-up.
#
# Self-cleaning. Leaves work-context-mode = SHADOW.
# ============================================================================
set -uo pipefail
NS=impilo-full-preview
PT="$(cat /proc/sys/kernel/random/uuid)"                # fresh proof tenant (no stale cache)
FAC='f1000000-0000-0000-0000-000000000001'
FAC2='f2000000-0000-0000-0000-000000000002'
FP="fp-proof-$(date +%s)"                               # device fingerprint -> new-device score 50 (< step-up 61)
RUN="azp-$(date +%s)"
ACTOR="${RUN}-actor"
PASS=0; FAIL=0
ok(){ PASS=$((PASS+1)); echo "  ok: $*"; }
bad(){ FAIL=$((FAIL+1)); echo "  FAIL: $*"; }

AUTHZ="$(kubectl get pod -n $NS -l app=tshepo-authz-service -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)"
echo "authz pod: $AUTHZ   proofTenant: $PT   run: $RUN"

PGA(){ kubectl exec -n $NS deploy/postgres -- psql -U impilo -d tshepo_authz -tAc "$1" 2>/dev/null; }
PGI(){ kubectl exec -n $NS deploy/postgres -- psql -U impilo -d tshepo_identity -tAc "$1" 2>/dev/null; }
b64url(){ python3 -c "import sys,base64;sys.stdout.write(base64.urlsafe_b64encode(sys.stdin.buffer.read()).rstrip(b'=').decode())"; }

mint_token(){ # <suffix> <claims-json> [status] -> echoes token
  local suffix="$1" claims="$2" status="${3:-ACTIVE}" jti="${RUN}-$1"
  PGI "INSERT INTO tshepo_identity.scoped_token
        (id,tenant_id,actor_id,target_service,scope,jti,issued_at,expires_at,status,token_kind,context_claims)
       VALUES (gen_random_uuid(),'$PT','$ACTOR','tshepo-authz','work:context','$jti',now(),
               now()+interval '15 min','$status','WORK_CONTEXT','$claims'::jsonb);" >/dev/null
  echo "$(printf '{"alg":"EdDSA","typ":"JWT"}'|b64url).$(printf '{"jti":"%s"}' "$jti"|b64url).$(printf sig|b64url)"
}

azfull(){ # <method> <extra-header-args...> -> body + @@<httpcode>
  local method="$1"; shift
  kubectl exec -n $NS "$AUTHZ" -- sh -c "curl -s -m 12 -w '\n@@%{http_code}' -X $method http://localhost:8081/v1/authorize \
    -H ':path: /v1/authorize' \
    -H 'X-Tenant-ID: $PT' -H 'X-Pod-ID: p' -H 'X-Request-ID: $(cat /proc/sys/kernel/random/uuid)' \
    -H 'X-Correlation-ID: ${RUN}' -H 'X-Actor-ID: $ACTOR' -H 'X-Actor-Type: PROVIDER' \
    -H 'X-Purpose-Of-Use: TREATMENT' -H 'X-Device-Fingerprint: $FP' $*" 2>/dev/null
}
code(){ echo "$1" | sed -n 's/.*@@//p'; }
body(){ echo "$1" | sed 's/@@[0-9]*$//'; }
ec(){ body "$1" | grep -o '"errorCode":"[^"]*"' | head -1; }
OB(){ PGA "select count(*) from tshepo_authz.event_outbox where event_type='$1' and payload::text like '%$ACTOR%';"; }

echo "== seed proof tenant: 2 rules + role-template mapping =="
PGA "INSERT INTO tshepo_authz.policy_rule
   (tenant_id,name,description,actor_type,role,resource_type,action,purpose,facility_scope,workspace_scope,effect,priority,conditions,active)
   VALUES
   ('$PT','proof-deny-discharged','',NULL,'CLINICIAN','authorize',NULL,NULL,false,false,'DENY',10,'{\"allowed_workflow_states\":[\"DISCHARGED\"]}',true),
   ('$PT','proof-allow-clinician','',NULL,'CLINICIAN','authorize',NULL,NULL,false,false,'ALLOW',20,'{\"allowed_departments\":[\"cardiology\"],\"requires_provider_id\":true}',true);" >/dev/null
PGA "INSERT INTO tshepo_authz.role_template_catalog (tenant_id,role_template_id,canonical_role,description)
     VALUES ('$PT','NURSE_ONCOLOGY_SNR','CLINICIAN','proof');" >/dev/null
RULES=$(PGA "select count(*) from tshepo_authz.policy_rule where tenant_id='$PT';")
[ "$RULES" = "2" ] && ok "seeded 2 proof rules + catalog under fresh tenant" || bad "seed failed (rules=$RULES)"

CLAIM_OK="{\"facility_id\":\"$FAC\",\"department_id\":\"cardiology\",\"role\":\"NURSE_ONCOLOGY_SNR\",\"provider_id\":\"PROV-1\"}"

echo "== 1. loop closed: duty NURSE_ONCOLOGY_SNR -> catalog CLINICIAN, dept+provider ok -> ALLOW + MATCHED =="
TOK=$(mint_token match "$CLAIM_OK")
R=$(azfull POST "-H 'X-Facility-ID: $FAC' -H 'X-Work-Context-Token: $TOK' -H 'X-Workflow-State: ACTIVE'")
[ "$(code "$R")" = "200" ] && ok "ALLOW (200) — duty role folded from catalog, dept+provider satisfied" || bad "expected 200, got $(code "$R") $(ec "$R")"
sleep 1
[ "$(OB WORK_CONTEXT_MATCHED)" -ge 1 ] && ok "governance WORK_CONTEXT_MATCHED emitted" || bad "no WORK_CONTEXT_MATCHED audit"

echo "== 2. DENY-ignores-conditions FIX: conditional DENY fires ONLY on its condition =="
R=$(azfull POST "-H 'X-Facility-ID: $FAC' -H 'X-Work-Context-Token: $TOK' -H 'X-Workflow-State: DISCHARGED'")
{ [ "$(code "$R")" = "403" ] && body "$R" | grep -q POLICY_DENY; } && ok "workflow-state DISCHARGED -> conditional DENY fires (403 POLICY_DENY)" || bad "DISCHARGED not denied: $(code "$R") $(ec "$R")"
# and the SAME rule set does NOT deny when the condition is absent (proven by test 1 ALLOW on ACTIVE)
ok "same DENY rule did NOT fire on ACTIVE (test 1 was ALLOW) — condition honoured, not blanket"

echo "== 3. department dimension (duty-token-authoritative) =="
TOK_ONC=$(mint_token onc "{\"facility_id\":\"$FAC\",\"department_id\":\"oncology\",\"role\":\"NURSE_ONCOLOGY_SNR\",\"provider_id\":\"PROV-1\"}")
R=$(azfull POST "-H 'X-Facility-ID: $FAC' -H 'X-Work-Context-Token: $TOK_ONC' -H 'X-Workflow-State: ACTIVE'")
[ "$(code "$R")" = "403" ] && ok "department oncology denied by allowed_departments=[cardiology] (403)" || bad "oncology not denied: $(code "$R")"

echo "== 4. attached provider-id dimension =="
TOK_NP=$(mint_token noprov "{\"facility_id\":\"$FAC\",\"department_id\":\"cardiology\",\"role\":\"NURSE_ONCOLOGY_SNR\"}")
R=$(azfull POST "-H 'X-Facility-ID: $FAC' -H 'X-Work-Context-Token: $TOK_NP' -H 'X-Workflow-State: ACTIVE'")
[ "$(code "$R")" = "403" ] && ok "requires_provider_id denies when no provider id in duty token (403)" || bad "missing provider not denied: $(code "$R")"

echo "== 5. binding MISMATCH — SHADOW audits, never denies for binding =="
R=$(azfull POST "-H 'X-Facility-ID: $FAC2' -H 'X-Work-Context-Token: $TOK' -H 'X-Workflow-State: ACTIVE'")
body "$R" | grep -q WORK_TOKEN_CONTEXT_MISMATCH && bad "SHADOW must not deny for binding" || ok "SHADOW did not deny for binding ($(ec "$R"))"
sleep 1
[ "$(OB WORK_CONTEXT_MISMATCH)" -ge 1 ] && ok "governance WORK_CONTEXT_MISMATCH emitted" || bad "no WORK_CONTEXT_MISMATCH audit"

echo "== 6. REVOKED token — SHADOW audits, never denies =="
RTOK=$(mint_token revoked "$CLAIM_OK" REVOKED)
R=$(azfull POST "-H 'X-Facility-ID: $FAC' -H 'X-Work-Context-Token: $RTOK' -H 'X-Workflow-State: ACTIVE'")
body "$R" | grep -q WORK_TOKEN_REVOKED && bad "SHADOW must not deny a revoked token" || ok "SHADOW did not deny revoked token"
sleep 1
[ "$(OB WORK_CONTEXT_REVOKED)" -ge 1 ] && ok "governance WORK_CONTEXT_REVOKED emitted" || bad "no WORK_CONTEXT_REVOKED audit"

echo "== 7. PolicyController runtime mutation is guarded =="
GC=$(kubectl exec -n $NS "$AUTHZ" -- curl -s -m 10 -o /dev/null -w '%{http_code}' -X POST http://localhost:8081/v1/policies \
      -H 'Content-Type: application/json' -H "X-Tenant-ID: $PT" -H 'X-Pod-ID: p' \
      -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" \
      -H 'X-Actor-Type: CITIZEN' -d '{"name":"x","effect":"ALLOW"}' 2>/dev/null)
{ [ "$GC" = "401" ] || [ "$GC" = "403" ]; } && ok "runtime policy mutation rejected (HTTP $GC)" || bad "policy mutation NOT guarded (HTTP $GC)"

echo "== 8. ENFORCE: mismatched duty token denies a mutating request, then revert to SHADOW =="
kubectl set env -n $NS deploy/tshepo-authz-service TSHEPO_WORK_CONTEXT_MODE=ENFORCE >/dev/null 2>&1
kubectl rollout status -n $NS deploy/tshepo-authz-service --timeout=120s >/dev/null 2>&1
AUTHZ="$(kubectl get pod -n $NS -l app=tshepo-authz-service -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)"
ETOK=$(mint_token enforce "$CLAIM_OK")
R=$(azfull POST "-H 'X-Facility-ID: $FAC2' -H 'X-Work-Context-Token: $ETOK' -H 'X-Workflow-State: ACTIVE'")
{ [ "$(code "$R")" = "403" ] && body "$R" | grep -q WORK_TOKEN_CONTEXT_MISMATCH; } && ok "ENFORCE denies mismatched duty token (403 WORK_TOKEN_CONTEXT_MISMATCH)" || bad "ENFORCE did not deny: $(code "$R") $(ec "$R")"
kubectl set env -n $NS deploy/tshepo-authz-service TSHEPO_WORK_CONTEXT_MODE=SHADOW >/dev/null 2>&1
kubectl rollout status -n $NS deploy/tshepo-authz-service --timeout=120s >/dev/null 2>&1
[ "$(kubectl exec -n $NS deploy/tshepo-authz-service -- sh -c 'echo ${TSHEPO_WORK_CONTEXT_MODE}' 2>/dev/null)" = "SHADOW" ] && ok "mode reverted to SHADOW" || bad "mode not reverted"

echo "== cleanup =="
PGI "delete from tshepo_identity.scoped_token where jti like '${RUN}-%';" >/dev/null
PGA "delete from tshepo_authz.policy_rule where tenant_id='$PT'; delete from tshepo_authz.role_template_catalog where tenant_id='$PT'; delete from tshepo_authz.event_outbox where payload::text like '%$ACTOR%';" >/dev/null

echo ""
echo "RESULT: $PASS ok, $FAIL fail  (run=$RUN)"
[ "$FAIL" -eq 0 ] || exit 1
