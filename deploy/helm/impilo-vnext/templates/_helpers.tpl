{{- define "impilo.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
impilo.io/environment: {{ .Values.global.environment | quote }}
{{- end }}

{{- define "impilo.globalEnv" -}}
- name: IMPILO_ENV
  value: {{ .root.Values.global.environment | quote }}
- name: IMPILO_GIT_BRANCH
  value: {{ .root.Values.global.gitBranch | default "" | quote }}
- name: IMPILO_GIT_COMMIT
  value: {{ .root.Values.global.gitCommit | default "" | quote }}
- name: IMPILO_BUILD_DATE
  value: {{ .root.Values.global.buildDate | default "" | quote }}
{{- if eq .root.Values.global.environment "full-preview" }}
- name: KEYCLOAK_URL
  value: "http://keycloak:8080"
- name: KEYCLOAK_REALM
  value: "impilo"
{{- end }}
{{- end }}

{{/*
Canonical Tshepo workload identity, matching
docs/security/trust-audit/checkpoint-4/workload-identity-registry.yaml exactly:
  urn:impilo:workload:<environment>:<cluster>:<namespace>:<service-account>:<workload>
The service-account segment is the workload's own name -- one identity per workload.
*/}}
{{- define "impilo.workloadId" -}}
{{- $v := .root.Values.workloadIdentity -}}
{{- printf "urn:impilo:workload:%s:%s:%s:%s:%s" $v.environment $v.cluster .root.Release.Namespace .name .name -}}
{{- end }}

{{- define "impilo.workloadAudience" -}}
{{- printf "%s:%s" .root.Values.workloadIdentity.audiencePrefix .name -}}
{{- end }}

{{/*
Projected, audience-restricted workload token. Short-lived and bound to this workload's
own Tshepo audience, so a token minted for one service cannot be replayed at another.
Mounted outside /var/run/secrets/kubernetes.io so it is never confused with the ambient
API-server token, which stays switched off.
*/}}
{{- define "impilo.workloadTokenVolume" -}}
- name: tshepo-workload-token
  projected:
    sources:
      - serviceAccountToken:
          path: token
          audience: {{ include "impilo.workloadAudience" (dict "root" .root "name" .name) | quote }}
          expirationSeconds: {{ .root.Values.workloadIdentity.tokenExpirationSeconds }}
{{- end }}

{{- define "impilo.workloadTokenMount" -}}
- name: tshepo-workload-token
  mountPath: {{ .root.Values.workloadIdentity.tokenMountPath }}
  readOnly: true
{{- end }}

{{- define "impilo.image" -}}
{{- $repo := .repository -}}
{{- $tag := .tag | default "preview" -}}
{{- $digest := .digest | default "" -}}
{{- $registry := "" -}}
{{- if .root -}}
{{- $registry = .root.Values.global.imageRegistry | default "" -}}
{{- end -}}
{{- if and $digest (hasPrefix "sha256:" $digest) -}}
{{- if $registry -}}
{{ printf "%s/%s@%s" $registry $repo $digest }}
{{- else -}}
{{ printf "%s@%s" $repo $digest }}
{{- end -}}
{{- else if $registry -}}
{{ printf "%s/%s:%s" $registry $repo $tag }}
{{- else -}}
{{ $repo }}:{{ $tag }}
{{- end -}}
{{- end }}
