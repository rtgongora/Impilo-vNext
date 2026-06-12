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
- name: IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS
  value: "true"
{{- end }}
{{- end }}

{{- define "impilo.image" -}}
{{- $repo := .repository -}}
{{- $tag := .tag | default "preview" -}}
{{- $registry := "" -}}
{{- if .root -}}
{{- $registry = .root.Values.global.imageRegistry | default "" -}}
{{- end -}}
{{- if $registry -}}
{{ printf "%s/%s:%s" $registry $repo $tag }}
{{- else -}}
{{ $repo }}:{{ $tag }}
{{- end -}}
{{- end }}
