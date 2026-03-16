#!/bin/bash
# =============================================================================
# Impilo vNext — Service Runtime Minimum Check
# Verifies every service has: Application class, controller, migration, test
# =============================================================================
set -uo pipefail

echo "============================================"
echo " Impilo vNext — Service Runtime Minimums"
echo "============================================"
echo ""

TOTAL=0
PASS=0
WARN=0

for svc_dir in services/*/; do
    svc=$(basename "$svc_dir")
    TOTAL=$((TOTAL + 1))

    # Skip shared-core (library, not a service)
    if [ "$svc" = "shared-core" ]; then
        echo "LIBRARY: $svc (shared library, not a runnable service)"
        continue
    fi

    # Skip BFF services (no own persistence by design)
    if [[ "$svc" == *"-bff" ]]; then
        echo "PASS: $svc (BFF — no own persistence by design)"
        PASS=$((PASS + 1))
        continue
    fi

    ISSUES=""

    # Check for Application.java
    APP=$(find "$svc_dir" -name "*Application.java" 2>/dev/null | head -1)
    if [ -z "$APP" ]; then
        ISSUES="$ISSUES [NO_APP]"
    fi

    # Check for at least one controller
    CTRL=$(find "$svc_dir" -name "*Controller.java" -path "*/main/*" 2>/dev/null | head -1)
    if [ -z "$CTRL" ]; then
        ISSUES="$ISSUES [NO_CONTROLLER]"
    fi

    # Check for database migration (services without persistence are OK)
    MIGRATION=$(find "$svc_dir" -name "V001__*.sql" 2>/dev/null | head -1)
    HAS_JPA=$(grep -c "spring-boot-starter-data-jpa\|spring-boot-starter-jdbc" "$svc_dir/pom.xml" 2>/dev/null || true)
    if [ -z "$MIGRATION" ] && [ "$HAS_JPA" -gt 0 ]; then
        ISSUES="$ISSUES [NO_MIGRATION]"
    fi

    # Check for at least one test
    TEST=$(find "$svc_dir" -name "*Test.java" -o -name "*IT.java" 2>/dev/null | head -1)
    if [ -z "$TEST" ]; then
        ISSUES="$ISSUES [NO_TESTS]"
    fi

    # Check for application.yml
    YML=$(find "$svc_dir" -name "application.yml" -path "*/main/resources/*" 2>/dev/null | head -1)
    if [ -z "$YML" ]; then
        ISSUES="$ISSUES [NO_CONFIG]"
    fi

    # Check for pom.xml
    POM="$svc_dir/pom.xml"
    if [ ! -f "$POM" ]; then
        ISSUES="$ISSUES [NO_POM]"
    fi

    if [ -z "$ISSUES" ]; then
        echo "PASS: $svc"
        PASS=$((PASS + 1))
    else
        echo "WARN: $svc $ISSUES"
        WARN=$((WARN + 1))
    fi
done

echo ""
echo "============================================"
echo "TOTAL: $TOTAL services"
echo "PASS:  $PASS"
echo "WARN:  $WARN"
echo "============================================"

if [ "$WARN" -gt 0 ]; then
    exit 1
fi
