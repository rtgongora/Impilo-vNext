# TLS for `impilo.mohcc.gov.zw`

Governed manifests + renewal hook for serving the live app over HTTPS at the
public host `impilo.mohcc.gov.zw`.

## Why this exists

The running k3s/Traefik estate had a hand-applied (un-versioned) TLS pipeline for
`impilo.mohcc.org.zw` — but the *entire* app stack (Keycloak redirect URIs, Helm
`global.publicHost`, mobile `eas.json`, LiveKit, e2e) targets **`.gov.zw`**. These
files bring `.gov.zw` under version control and align the cluster with the app
config.

## Architecture (how a request flows)

```
Client ──TLS:443──▶ Traefik ──plaintext──▶ one-ui-shell:3000  (UI)
                      │                     experience-bff:8160 (/internal,/actuator,/health)
                      └─ TLS terminated here using secret impilo-mohcc-gov-zw-tls
```

TLS terminates at **Traefik**, not Envoy. Envoy (`domains:["*"]`, ext_authz →
TSHEPO) is not in this host's path and needs no changes for a new hostname.

## The ACME issuance pipeline

certbot runs on the **host** (webroot `/var/www/letsencrypt`, ECDSA). The HTTP-01
challenge is routed back to the host from Traefik:

```
Let's Encrypt ──:80──▶ Traefik ──Ingress acme-host-nginx-gov (host .gov.zw,
                                  path /.well-known/acme-challenge/)──▶
                       Service acme-host-nginx (:80→:8089, Endpoints=host:8089)──▶
                       host nginx :8089 (impilo-acme-webroot-8089.conf) serves
                       /var/www/letsencrypt/.well-known/acme-challenge/<token>
```

### External dependencies NOT captured here (pre-existing cluster/host state)

- **Service `acme-host-nginx`** (ns `impilo-full-preview`) with **manual Endpoints**
  pointing at the node IP `:8089`. Reused as-is by `acme-ingress.yaml`.
- **Host nginx** listening on `:8089` (`/etc/nginx/conf.d/impilo-acme-webroot-8089.conf`,
  `server_name _`, `root /var/www/letsencrypt`).
- DNS: `impilo.mohcc.gov.zw` must resolve to the node's public IP.

## Files

| File | Purpose |
|------|---------|
| `acme-ingress.yaml` | Traefik Ingress routing the ACME challenge for `.gov.zw` to `acme-host-nginx`. |
| `ingressroutes.yaml` | Traefik `websecure` IngressRoutes terminating TLS and routing UI/BFF. |
| `../../scripts/tls/sync-mohcc-gov-tls.sh` | certbot `--deploy-hook`: syncs the renewed cert into secret `impilo-mohcc-gov-zw-tls`. |

## Apply / issue (run on the host, as root where noted)

```bash
# 1. ACME routing
kubectl apply -f deploy/tls/mohcc-gov/acme-ingress.yaml

# 2. install the renewal deploy-hook
sudo install -m750 scripts/tls/sync-mohcc-gov-tls.sh /usr/local/bin/sync-mohcc-gov-tls.sh

# 3. dry-run (real external validation, no rate-limit cost)
sudo certbot certonly --webroot -w /var/www/letsencrypt \
  -d impilo.mohcc.gov.zw --key-type ecdsa --dry-run

# 4. issue for real + register the deploy-hook (also runs it now → creates the secret)
sudo certbot certonly --webroot -w /var/www/letsencrypt \
  -d impilo.mohcc.gov.zw --key-type ecdsa \
  --deploy-hook /usr/local/bin/sync-mohcc-gov-tls.sh

# 5. TLS routing
kubectl apply -f deploy/tls/mohcc-gov/ingressroutes.yaml
```

## Verify

```bash
echo | openssl s_client -connect impilo.mohcc.gov.zw:443 -servername impilo.mohcc.gov.zw 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates      # subject CN=impilo.mohcc.gov.zw
curl -sSI https://impilo.mohcc.gov.zw/ | head -1
sudo certbot renew --cert-name impilo.mohcc.gov.zw --dry-run   # exercises the deploy-hook
```

## Renewal

`certbot.timer` (already enabled) renews automatically; the `--deploy-hook`
re-syncs the k8s secret on each renewal. No manual step required.
