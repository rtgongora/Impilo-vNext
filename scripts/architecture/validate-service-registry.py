#!/usr/bin/env python3
import argparse
import collections
import os
import sys
from datetime import date
from pathlib import Path

try:
    import yaml
except Exception:
    print('ERROR: PyYAML is required. Install with: pip install pyyaml')
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[2]
REGISTRY_PATH = ROOT / 'docs/architecture/services-registry.yaml'
REGISTER_MD = ROOT / 'docs/architecture/SERVICE_ARCHITECTURE_REGISTER.md'
ACTIVATION_MATRIX_MD = ROOT / 'docs/architecture/SERVICE_ACTIVATION_MATRIX.md'
CONTRACT_MAP_MD = ROOT / 'docs/architecture/SERVICE_CONTRACT_MAP.md'
SURFACING_MAP_MD = ROOT / 'docs/architecture/SERVICE_SURFACING_MAP.md'
INTEGRATION_MAP_MD = ROOT / 'docs/architecture/SERVICE_INTEGRATION_MAP.md'
DUPLICATION_REGISTER_MD = ROOT / 'docs/architecture/SERVICE_DUPLICATION_AND_CONSOLIDATION_REGISTER.md'
ACTIVATION_BACKLOG_MD = ROOT / 'docs/architecture/SERVICE_ACTIVATION_BACKLOG.md'
REMEDIATION_REPORT_MD = ROOT / 'docs/architecture/SERVICE_REMEDIATION_REPORT.md'
ALIAS_DEPRECATED_CONTRACT_NA = {'product-registry-service', 'wellness-service'}
SKELETON_CONTRACT_EVIDENCE_REQUIRED = {'referral-service', 'analytics-pipeline-service'}

VALID_TYPES = {
    'backend_service',
    'frontend_app',
    'mobile_app',
    'shared_library',
    'adapter',
    'worker',
    'infrastructure',
    'documentation_only',
    'external_dependency',
}
VALID_IMPL = {'live', 'skeleton', 'new', 'deprecated', 'unclear'}
VALID_RINGS = {'ring_0_kernel', 'ring_1_execution', 'ring_2_scale', 'infra', 'ui', 'library', 'external', 'unclear'}
RING_DISPLAY = {
    'ring_0_kernel': 'Ring 0 Kernel',
    'ring_1_execution': 'Ring 1 Execution',
    'ring_2_scale': 'Ring 2 Scale',
    'infra': 'Infrastructure',
    'ui': 'User Interface',
    'library': 'Library',
    'external': 'External',
    'unclear': 'Unclear',
}
VALID_PLANES = {'trust_governance', 'registry_spine', 'clinical_execution', 'finance_resource', 'integration_ops', 'experience', 'enterprise_resource', 'unclear'}
PLANE_DISPLAY = {
    'trust_governance': 'Trust & Governance',
    'registry_spine': 'Registry Spine',
    'clinical_execution': 'Clinical Execution',
    'finance_resource': 'Finance & Resource',
    'integration_ops': 'Integration & Operations',
    'experience': 'Experience',
    'enterprise_resource': 'Enterprise Resource',
    'unclear': 'Unclear',
}
VALID_CONF = {'high', 'medium', 'low'}
VALID_ACTIVATION_STATE = {
    'Activated',
    'Backend-Only',
    'Frontend-Only',
    'Integrated-But-Unsurfaced',
    'Surfaced-But-Shallow',
    'Orphaned',
    'Duplicated',
    'Skeleton',
    'Documented-Only',
    'Deprecated Candidate',
    'Needs Owner Decision',
}
VALID_CONTRACT_ALIGNMENT = {'Aligned', 'Partial', 'Stale', 'Missing', 'Duplicated', 'Unclear', 'Not Applicable'}
VALID_SURFACING_QUALITY = {'Complete', 'Partial', 'Placeholder', 'Missing', 'Not Required', 'Unclear'}
VALID_REMEDIATION_STATUS = {'Fixed', 'Partially Fixed', 'Deferred', 'Needs Owner Decision', 'Blocked', 'Not Applicable'}
VALID_BACKLOG_PRIORITY = {'P0', 'P1', 'P2', 'P3', 'None'}
VALID_CATEGORY_DISPLAY = {
    'Kernel', 'Clinical', 'Data', 'Integration', 'Supply', 'Experience', 'Assurance',
    'Resilience', 'Finance', 'Enterprise', 'Registry', 'Infrastructure', 'User Interface',
    'External', 'Unclear'
}
REQUIRED_FIELDS = [
    'canonical_name', 'display_name', 'module_path', 'type', 'implementation_status', 'ring', 'ring_display',
    'primary_plane', 'primary_plane_display', 'category', 'category_display', 'secondary_planes',
    'secondary_plane_displays', 'system_of_record_for', 'consumes', 'consumed_by', 'frontend_surface',
    'api_surface', 'events_published', 'events_consumed', 'database_schema', 'port', 'deployment_unit',
    'readiness_status', 'boundary_notes', 'forbidden_responsibilities', 'confidence', 'evidence_files',
    'documentation_gap', 'implementation_gap', 'frontend_gap', 'possible_duplicate', 'unresolved_questions',
    'activation_state', 'contract_alignment_status', 'contract_evidence', 'surfacing_quality',
    'integration_status', 'remediation_status', 'remediation_notes', 'owner_decision_required',
    'canonical_service', 'deduplication_group', 'backlog_priority', 'last_validated_on'
]


def parse_args():
    parser = argparse.ArgumentParser(description='Validate canonical service registry policy.')
    parser.add_argument('--mode', choices=['advisory', 'soft', 'hard'], default=os.getenv('SERVICE_REGISTRY_VALIDATION_MODE', 'hard'))
    parser.add_argument(
        '--openapi-legacy-deadline',
        default=os.getenv('OPENAPI_LEGACY_DEADLINE', '2026-10-31'),
        help='Date (YYYY-MM-DD) after which legacy live backend OpenAPI gaps become blocking in hard mode.'
    )
    parser.add_argument(
        '--require-soft-gate-contract-closure',
        action='store_true',
        default=os.getenv('SERVICE_REGISTRY_REQUIRE_SOFT_GATE_CONTRACT_CLOSURE', 'false').lower() == 'true',
        help='When enabled, soft mode also fails on policy errors related to contract-alignment closure.'
    )
    return parser.parse_args()


def has_openapi_evidence(entry):
    api_surface = (entry.get('api_surface') or '').strip()
    if 'openapi' in api_surface:
        return True
    for ev in entry.get('evidence_files', []) or []:
        if 'openapi' in str(ev):
            return True
    return False


def print_report(structural_errors, policy_errors, warnings, mode, summaries=None, enforce_soft_policy=False):
    blocking = []
    if mode == 'soft':
        blocking.extend(structural_errors)
        if enforce_soft_policy:
            blocking.extend(policy_errors)
    elif mode == 'hard':
        blocking.extend(structural_errors)
        blocking.extend(policy_errors)

    if blocking:
        print('VALIDATION FAILED')
        for e in blocking:
            print(f'- ERROR: {e}')
    else:
        print('VALIDATION PASSED')

    if structural_errors and mode == 'advisory':
        print('ADVISORY STRUCTURAL FINDINGS')
        for e in structural_errors:
            print(f'- WARN: {e}')

    if policy_errors and mode != 'hard':
        print('ADVISORY POLICY FINDINGS')
        for e in policy_errors:
            print(f'- WARN: {e}')

    if warnings:
        print('WARNINGS')
        for w in warnings:
            print(f'- WARN: {w}')

    if summaries:
        print('SUMMARY COUNTS')
        for label, counter in summaries:
            print(f'- {label}:')
            for key, value in sorted(counter.items()):
                print(f'  - {key}: {value}')


def main():
    args = parse_args()
    structural_errors = []
    policy_errors = []
    warnings = []

    try:
        legacy_deadline = date.fromisoformat(args.openapi_legacy_deadline)
    except ValueError:
        structural_errors.append(f'Invalid --openapi-legacy-deadline value: {args.openapi_legacy_deadline}')
        print_report(structural_errors, policy_errors, warnings, args.mode)
        return 1 if args.mode in {'soft', 'hard'} else 0

    if not REGISTRY_PATH.exists():
        structural_errors.append('docs/architecture/services-registry.yaml does not exist.')
        print_report(structural_errors, policy_errors, warnings, args.mode)
        return 1 if args.mode in {'soft', 'hard'} else 0

    try:
        data = yaml.safe_load(REGISTRY_PATH.read_text(encoding='utf-8'))
    except Exception as exc:
        structural_errors.append(f'Failed to parse registry YAML: {exc}')
        print_report(structural_errors, policy_errors, warnings, args.mode)
        return 1 if args.mode in {'soft', 'hard'} else 0

    services = data.get('services') if isinstance(data, dict) else None
    if not isinstance(services, list):
        structural_errors.append('Registry root must be a mapping with a services list.')
        print_report(structural_errors, policy_errors, warnings, args.mode)
        return 1 if args.mode in {'soft', 'hard'} else 0

    name_seen = set()
    path_seen = {}
    port_seen = {}
    schema_seen = {}
    missing_contract_alignment = []

    for idx, s in enumerate(services):
        if not isinstance(s, dict):
            structural_errors.append(f'Entry {idx} is not an object.')
            continue

        missing = [f for f in REQUIRED_FIELDS if f not in s]
        if missing:
            structural_errors.append(f"{s.get('canonical_name', f'entry-{idx}')}: missing required fields: {', '.join(missing)}")
            continue

        name = s['canonical_name']
        if name in name_seen:
            structural_errors.append(f'{name}: canonical_name is not unique.')
        name_seen.add(name)

        if s['type'] not in VALID_TYPES:
            structural_errors.append(f'{name}: invalid type {s["type"]}')
        if s['implementation_status'] not in VALID_IMPL:
            structural_errors.append(f'{name}: invalid implementation_status {s["implementation_status"]}')
        if s['ring'] not in VALID_RINGS:
            structural_errors.append(f'{name}: invalid ring {s["ring"]}')
        if s['ring_display'] != RING_DISPLAY.get(s['ring']):
            structural_errors.append(f'{name}: ring_display {s["ring_display"]} does not match ring {s["ring"]}')
        if s['primary_plane'] not in VALID_PLANES:
            structural_errors.append(f'{name}: invalid primary_plane {s["primary_plane"]}')
        if s['primary_plane_display'] != PLANE_DISPLAY.get(s['primary_plane']):
            structural_errors.append(f'{name}: primary_plane_display {s["primary_plane_display"]} does not match primary_plane {s["primary_plane"]}')
        if s['category_display'] not in VALID_CATEGORY_DISPLAY:
            structural_errors.append(f'{name}: invalid category_display {s["category_display"]}')
        if s['confidence'] not in VALID_CONF:
            structural_errors.append(f'{name}: invalid confidence {s["confidence"]}')
        if s['activation_state'] not in VALID_ACTIVATION_STATE:
            structural_errors.append(f'{name}: invalid activation_state {s["activation_state"]}')
        if s['contract_alignment_status'] not in VALID_CONTRACT_ALIGNMENT:
            structural_errors.append(f'{name}: invalid contract_alignment_status {s["contract_alignment_status"]}')
        if s['surfacing_quality'] not in VALID_SURFACING_QUALITY:
            structural_errors.append(f'{name}: invalid surfacing_quality {s["surfacing_quality"]}')
        if s['remediation_status'] not in VALID_REMEDIATION_STATUS:
            structural_errors.append(f'{name}: invalid remediation_status {s["remediation_status"]}')
        if s['backlog_priority'] not in VALID_BACKLOG_PRIORITY:
            structural_errors.append(f'{name}: invalid backlog_priority {s["backlog_priority"]}')
        if not isinstance(s['contract_evidence'], list):
            structural_errors.append(f'{name}: contract_evidence must be a list')
        if not isinstance(s['owner_decision_required'], bool):
            structural_errors.append(f'{name}: owner_decision_required must be boolean')

        if not isinstance(s['secondary_planes'], list):
            structural_errors.append(f'{name}: secondary_planes must be a list')
        else:
            for p in s['secondary_planes']:
                if p not in VALID_PLANES:
                    structural_errors.append(f'{name}: invalid secondary plane {p}')

        mp = s['module_path']
        if mp in path_seen and not (name == path_seen[mp] or s['possible_duplicate']):
            warnings.append(f'{name}: module_path {mp} is shared with {path_seen[mp]} (ensure intentional).')
        else:
            path_seen[mp] = name

        if s['implementation_status'] == 'live' and not s['evidence_files']:
            structural_errors.append(f'{name}: live entry must include evidence_files.')
        if s['type'] in {'backend_service', 'adapter', 'worker'} and s['implementation_status'] == 'live':
            if s['contract_alignment_status'] == 'Aligned' and not s['contract_evidence'] and not has_openapi_evidence(s):
                policy_errors.append(f'{name}: marked Aligned but contract_evidence/OpenAPI evidence is missing.')
            if s['surfacing_quality'] in {'Missing', 'Unclear'} and not s['owner_decision_required']:
                warnings.append(f'{name}: live backend has {s["surfacing_quality"]} surfacing quality without owner decision flag.')
            if s['contract_alignment_status'] == 'Missing':
                missing_contract_alignment.append(name)
        if s['activation_state'] in {'Duplicated', 'Needs Owner Decision'} and not (s['owner_decision_required'] or s['canonical_service']):
            warnings.append(f'{name}: activation_state={s["activation_state"]} should set owner_decision_required or canonical_service.')
        if name in ALIAS_DEPRECATED_CONTRACT_NA and s['contract_alignment_status'] != 'Not Applicable':
            policy_errors.append(f'{name}: alias-deprecated entry must use contract_alignment_status=Not Applicable.')
        if name in SKELETON_CONTRACT_EVIDENCE_REQUIRED:
            if s['contract_alignment_status'] in {'Not Applicable', 'Unclear', 'Missing'}:
                policy_errors.append(
                    f'{name}: skeleton service must carry concrete contract evidence and may not remain Not Applicable/Unclear/Missing.'
                )
            if not s['contract_evidence']:
                policy_errors.append(f'{name}: skeleton service requires contract_evidence entries.')

        # Phased OpenAPI policy
        if s['type'] == 'backend_service' and s['implementation_status'] != 'deprecated':
            has_openapi = has_openapi_evidence(s)
            if not has_openapi:
                if s['implementation_status'] in {'new', 'skeleton'}:
                    policy_errors.append(f'{name}: new/skeleton backend service must include OpenAPI evidence.')
                elif s['implementation_status'] == 'live':
                    if date.today() > legacy_deadline:
                        policy_errors.append(
                            f'{name}: live backend service missing OpenAPI evidence and legacy deadline {legacy_deadline.isoformat()} has passed.'
                        )
                    else:
                        warnings.append(
                            f'{name}: live backend service missing OpenAPI evidence (legacy deadline {legacy_deadline.isoformat()}).'
                        )

        port = s.get('port')
        if port not in (None, '', 0):
            if port in port_seen and not (s['possible_duplicate'] or services[port_seen[port]].get('possible_duplicate')):
                warnings.append(f'{name}: duplicate port {port} also used by {services[port_seen[port]]["canonical_name"]}.')
            else:
                port_seen[port] = idx

        schema = s.get('database_schema')
        if schema:
            if schema in schema_seen and not (s['possible_duplicate'] or services[schema_seen[schema]].get('possible_duplicate')):
                warnings.append(f'{name}: duplicate database_schema {schema} also used by {services[schema_seen[schema]]["canonical_name"]}.')
            else:
                schema_seen[schema] = idx

    # Discovery coverage checks
    service_dirs = sorted([p.name for p in (ROOT / 'services').iterdir() if p.is_dir() and not p.name.startswith('.') and p.name not in {'shared-core', '.m2', '.m2repo', '.mvn'}])
    ui_dirs = sorted([p.name for p in (ROOT / 'ui').iterdir() if p.is_dir() and p.name != 'node_modules' and not p.name.startswith('.')])
    lib_dirs = sorted([p.name for p in (ROOT / 'libs').iterdir() if p.is_dir() and not p.name.startswith('.')])

    registry_names = {s['canonical_name'] for s in services}
    for d in service_dirs + ['shared-core']:
        if d not in registry_names:
            structural_errors.append(f'Missing service coverage: {d} from services/')
    for d in ui_dirs:
        if d not in registry_names:
            structural_errors.append(f'Missing UI coverage: {d} from ui/')
    for d in lib_dirs:
        if d not in registry_names:
            structural_errors.append(f'Missing library coverage: {d} from libs/')

    # Registry libraries → Maven reactor (docs/registry/services-registry.yaml).
    # The check above is the reverse direction (libs/ on disk → architecture registry).
    # medicine-domain was registered but never listed in services/pom.xml modules, so its
    # tests ran in no CI job. Fail when a registry library is absent from the reactor.
    prod_registry = ROOT / 'docs/registry/services-registry.yaml'
    pom_path = ROOT / 'services/pom.xml'
    if prod_registry.exists() and pom_path.exists():
        try:
            prod = yaml.safe_load(prod_registry.read_text(encoding='utf-8')) or {}
            pom_text = pom_path.read_text(encoding='utf-8')
            for lib in prod.get('libraries') or []:
                module = lib.get('maven_module')
                if not module:
                    continue
                # shared-core lives under services/; other libs under ../libs/<module>
                if f'<module>../libs/{module}</module>' not in pom_text and f'<module>{module}</module>' not in pom_text:
                    # Some registry libs are not Maven reactor members by design (mocks, harnesses).
                    path = lib.get('path') or ''
                    if path.startswith('libs/') and (ROOT / path / 'pom.xml').exists():
                        structural_errors.append(
                            f"Registry library '{module}' has a pom at {path} but is not a "
                            f"<module> in services/pom.xml — it will never build or test in the reactor."
                        )
        except Exception as e:
            warnings.append(f'Could not cross-check registry libraries against Maven reactor: {e}')

    # Infra hint checks
    infra_hints = ['docker-compose.yml', 'infra/envoy/envoy.yaml', 'ops/runtime/docker-compose.observability.yml']
    for h in infra_hints:
        if not any((s['type'] == 'infrastructure' and h in s['evidence_files']) for s in services):
            warnings.append(f'No infrastructure entry references {h}.')

    # Unresolved consistency check
    if not REGISTER_MD.exists():
        structural_errors.append('docs/architecture/SERVICE_ARCHITECTURE_REGISTER.md does not exist.')
    else:
        reg_md_text = REGISTER_MD.read_text(encoding='utf-8')
        for s in services:
            if s['ring'] == 'unclear' or s['primary_plane'] == 'unclear' or s['confidence'] == 'low':
                if s['display_name'] not in reg_md_text:
                    policy_errors.append(f'{s["canonical_name"]}: unclear/low-confidence entry must appear in unresolved section of SERVICE_ARCHITECTURE_REGISTER.md')

        forbidden_labels = ['ring_0_kernel', 'ring_1_execution', 'ring_2_scale', 'trust_governance', 'registry_spine', 'clinical_execution', 'finance_resource', 'integration_ops', 'enterprise_resource']
        for token in forbidden_labels:
            if token in reg_md_text:
                warnings.append(f'SERVICE_ARCHITECTURE_REGISTER.md contains machine label {token}; prefer display labels in human-readable sections.')

    required_artifacts = [
        ACTIVATION_MATRIX_MD,
        CONTRACT_MAP_MD,
        SURFACING_MAP_MD,
        INTEGRATION_MAP_MD,
        DUPLICATION_REGISTER_MD,
        ACTIVATION_BACKLOG_MD,
        REMEDIATION_REPORT_MD,
    ]
    for artifact in required_artifacts:
        if not artifact.exists():
            structural_errors.append(f'{artifact.relative_to(ROOT)} does not exist.')

    if DUPLICATION_REGISTER_MD.exists():
        duplication_text = DUPLICATION_REGISTER_MD.read_text(encoding='utf-8')
        for s in services:
            if s['activation_state'] == 'Duplicated' and s['display_name'] not in duplication_text:
                policy_errors.append(
                    f'{s["canonical_name"]}: duplicated entry must be listed in SERVICE_DUPLICATION_AND_CONSOLIDATION_REGISTER.md'
                )

    if ACTIVATION_BACKLOG_MD.exists():
        backlog_text = ACTIVATION_BACKLOG_MD.read_text(encoding='utf-8')
        for s in services:
            if s['backlog_priority'] in {'P0', 'P1', 'P2'} and s['display_name'] not in backlog_text:
                warnings.append(
                    f'{s["canonical_name"]}: backlog priority {s["backlog_priority"]} is not referenced in SERVICE_ACTIVATION_BACKLOG.md'
                )

    if args.require_soft_gate_contract_closure and missing_contract_alignment:
        policy_errors.append(
            'Soft-gate promotion readiness failed; services still in contract_alignment_status=Missing: '
            + ', '.join(sorted(set(missing_contract_alignment)))
        )

    activation_counts = collections.Counter(s['activation_state'] for s in services)
    contract_counts = collections.Counter(s['contract_alignment_status'] for s in services)
    surfacing_counts = collections.Counter(s['surfacing_quality'] for s in services)
    remediation_counts = collections.Counter(s['remediation_status'] for s in services)

    summaries = [
        ('Activation State', activation_counts),
        ('Contract Alignment', contract_counts),
        ('Surfacing Quality', surfacing_counts),
        ('Remediation Status', remediation_counts),
    ]
    print_report(
        structural_errors,
        policy_errors,
        warnings,
        args.mode,
        summaries=summaries,
        enforce_soft_policy=args.require_soft_gate_contract_closure
    )

    if args.mode == 'advisory':
        return 0
    if args.mode == 'soft':
        if args.require_soft_gate_contract_closure:
            return 1 if (structural_errors or policy_errors) else 0
        return 1 if structural_errors else 0
    return 1 if (structural_errors or policy_errors) else 0


if __name__ == '__main__':
    sys.exit(main())
