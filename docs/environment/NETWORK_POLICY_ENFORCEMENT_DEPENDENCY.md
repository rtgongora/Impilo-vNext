# D5 — Pod isolation is an infrastructure dependency, not a code gap

**Status:** blocked on a host package. Not closable in application code.
**Measured:** 2026-07-30 on `impilo.mohcc.gov.zw` (235), k3s `v1.35.5+k3s1`, node `user-hvm-domu`.

## The finding

**This cluster does not enforce `NetworkPolicy`.** A default-deny policy covering both
ingress and egress is accepted by the API server, is correctly formed, selects every pod in
its namespace — and changes nothing. Traffic continues to flow in both directions.

This confirms the team's recorded `networkpolicy-not-enforced` note. It also **disproves the
inference that replaced it**: k3s here runs without `--disable-network-policy`, so the
embedded policy controller was expected to be active, and the note was suspected of being
stale or from a different cluster. It is neither. The flag was the wrong thing to look at.

## Root cause

k3s's embedded kube-router network-policy controller programs its match sets through the
**`ipset` userspace binary**. That binary is **not installed on this host**:

```
$ command -v ipset      # → not found
$ lsmod | grep ip_set
ip_set                  loaded
ip_set_hash_ip          NOT loaded
ip_set_hash_net         NOT loaded
```

The `ip_set_hash_*` modules are the ones the controller's sets need, and they are unloaded
because nothing has ever asked for them — consistent with `ipset` never having run.

The controller does not refuse to start and does not fail a health check. It cannot install
rules, so **policy is accepted and silently inert**. That is the property that makes this
dangerous: `kubectl get networkpolicy` lists objects, dashboards report policies present,
and nothing anywhere reports that they do not apply.

## Evidence

A throwaway namespace, two pods, and a positive control before the negative one — so that a
post-policy failure would mean enforcement rather than a broken probe. The namespace was
deleted afterwards; `impilo-full-preview` was not touched.

| Step | Command | Result |
|---|---|---|
| Positive control, no policy | `nc -z -w 3 10.42.0.23 6379` | reachable |
| Positive control, no policy | `redis-cli -h 10.42.0.23 PING` | `PONG` |
| Apply `podSelector: {}`, `policyTypes: [Ingress, Egress]` | `kubectl apply` | created |
| Policy selects the pods | `kubectl describe netpol` | "Selected pods are isolated for ingress connectivity" / "…for egress connectivity" |
| Pod-to-pod, after policy | `nc -z -w 5 10.42.0.23 6379` | **still reachable** |
| Pod-to-pod, after 30s settle | `redis-cli -h 10.42.0.23 PING` | **`PONG`** |
| Pod-to-internet, after policy | `nc -z -w 5 1.1.1.1 443` | **still reachable** |

Egress to the public internet surviving a deny-all egress policy rules out any reading in
which the policy was partially applied.

## Why no manifests were written

There is an unused template at `helm/impilo-service/templates/networkpolicy.yaml`, gated by
`networkPolicy.enabled`, and turning it on is a few lines. It would produce policy objects
on every service, a `networkPolicy.enabled: true` in values, and a security posture document
that could truthfully say pod isolation is configured — on an estate where any pod can still
reach any other pod.

That is worse than the current state, because the current state is at least legible. A
control that is present, inspectable, and inert is one nobody re-tests.

## What actually closes it

On the k3s host, as root:

```bash
apt-get install -y ipset
systemctl restart k3s
```

Then re-run the probe above. The negative control must report **blocked** before any
`NetworkPolicy` in this repo is enabled.

This is a **sudo action on the host**, so it belongs in the sudo checkpoint flow
(`reports/full-boot/sudo-checkpoint.*`), not in a deploy script. It restarts k3s, which
restarts the control plane, so it needs a window — and note the standing **helm repin hold**:
`impilo-full-preview` is at a helm revision older than several `kubectl set image` deploys,
so nothing here should be folded into a `helm upgrade`.

Once enforcement is proven, D5 becomes ordinary application work: enable the existing
template per service, add gateway-only ingress, and give service-to-service calls a workload
identity. None of that is worth writing until the negative control fails.

## Consequence for D2/D3

The decision envelope (D2, shipped) and its downstream verification (D3) assume a caller
must traverse the gateway to reach a service. Without pod isolation, a workload inside the
cluster can call any service directly and skip the PDP entirely.

The envelope is still worth having — verification is what turns `x-decision: ALLOW` from a
header anyone can type into something only the PDP can produce, and that holds regardless of
network reachability. But it is a **defence in depth**, not a perimeter, until this
dependency is closed. Any claim that the PDP gates all access to a service is false on this
cluster today, and D7's cutover runbook must say so rather than implying the gate is the
only path in.

---

## Correction 2026-08-02: `ipset` was necessary but NOT sufficient

This document asserted that the missing `ipset` binary was the root cause. **That was tested and
is disproven.**

`ipset` was installed and k3s restarted:

```
/usr/sbin/ipset                      present (ipset v7.19)
lsmod | grep ip_set   → ip_set 61440 1 xt_set,  xt_set 20480 0    (modules loaded)
systemctl show k3s    → ActiveEnterTimestamp = 2026-08-02 15:17:37 CAT  (restarted)
```

The enforcement probe was then re-run **twice**, with its positive control passing both times:

```
PROBE OK    positive control: pod-to-pod reachable with no policy
RESULT: NetworkPolicy is NOT ENFORCED on this cluster.
```

So the estate still has no pod isolation, and the reason is **not** the one recorded here.

### What is now known

| Fact | Value |
|---|---|
| `ipset` binary | present |
| `ip_set` / `xt_set` kernel modules | loaded |
| k3s launch flags | `server --write-kubeconfig-mode 644` — **no `--disable-network-policy`** |
| k3s config | `kubelet-arg: max-pods=250` only |
| `KUBE-ROUTER` / `KUBE-NWPLCY` iptables chains | **none observed** |
| iptables backend | **`v1.8.10 (nf_tables)`** — not legacy |
| `nft_compat` module | loaded, 3719 references |
| kernel | 6.17.0-29-generic |

### Leading hypothesis, explicitly untested

The host resolves `iptables` to the **nftables** backend via `nft_compat`. kube-router programs
its policy chains through the iptables interface, and a backend mismatch would let rule
programming appear to succeed while the packets are evaluated by a different path. This is a
hypothesis, not a diagnosis — it has **not** been confirmed.

### The one command that would confirm it

Needs root, which this session does not hold:

```bash
sudo journalctl -u k3s --since "-30min" | grep -iE "netpol|kube-router|ipset|network polic"
sudo iptables -S | grep -cE "KUBE-ROUTER|KUBE-NWPLCY"
sudo nft list ruleset | grep -c KUBE
```

Until that is run, the correct status is **BLOCKED, cause unconfirmed** — not "blocked on ipset".
Recording a specific wrong cause is worse than recording an open one, because it stops anyone
looking further. No NetworkPolicy manifests have been written: a control that is accepted and
silently inert is one nobody re-tests.
