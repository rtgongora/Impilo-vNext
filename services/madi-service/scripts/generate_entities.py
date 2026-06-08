#!/usr/bin/env python3
"""Generate JPA entities and repositories for madi-service tables."""
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent / "src/main/java/zw/gov/mohcc/impilo/madi/persistence"
SCHEMA = "madi"

TABLES = [
    ("donor_profiles", "DonorProfile", "donor_id", ["personCpid:String", "bloodGroup:String", "rhFactor:String", "status:String", "facilityId:UUID", "jurisdiction:String", "registeredBy:String", "updatedAt:OffsetDateTime"]),
    ("donor_eligibility_screenings", "DonorEligibilityScreening", "screening_id", ["donorId:UUID", "driveId:UUID", "result:String", "status:String", "screeningNotes:String", "screenedBy:String", "facilityId:UUID", "jurisdiction:String", "screenedAt:OffsetDateTime"]),
    ("donor_deferrals", "DonorDeferral", "deferral_id", ["donorId:UUID", "reasonCode:String", "status:String", "deferredUntil:LocalDate", "notes:String", "deferredBy:String", "facilityId:UUID", "jurisdiction:String", "updatedAt:OffsetDateTime"]),
    ("donor_communication_preferences", "DonorCommunicationPreference", "preference_id", ["donorId:UUID", "channel:String", "enabled:boolean", "locale:String", "facilityId:UUID", "jurisdiction:String", "updatedBy:String", "updatedAt:OffsetDateTime"]),
    ("donor_feedback", "DonorFeedback", "feedback_id", ["donorId:UUID", "driveId:UUID", "rating:Integer", "comments:String", "status:String", "facilityId:UUID", "jurisdiction:String", "submittedAt:OffsetDateTime"]),
    ("donor_notifications", "DonorNotification", "notification_id", ["donorId:UUID", "channel:String", "subject:String", "body:String", "status:String", "facilityId:UUID", "jurisdiction:String", "sentAt:OffsetDateTime"]),
    ("donation_drives", "DonationDrive", "drive_id", ["title:String", "description:String", "status:String", "driveType:String", "facilityId:UUID", "locationRef:String", "ndilaSiteRef:String", "jurisdiction:String", "startAt:OffsetDateTime", "endAt:OffsetDateTime", "capacity:Integer", "publishedBy:String", "createdBy:String", "updatedAt:OffsetDateTime"]),
    ("donation_drive_slots", "DonationDriveSlot", "slot_id", ["driveId:UUID", "slotStart:OffsetDateTime", "slotEnd:OffsetDateTime", "capacity:Integer", "bookedCount:Integer", "status:String", "facilityId:UUID", "jurisdiction:String"]),
    ("donation_drive_attendances", "DonationDriveAttendance", "attendance_id", ["driveId:UUID", "slotId:UUID", "donorId:UUID", "status:String", "facilityId:UUID", "jurisdiction:String", "registeredAt:OffsetDateTime", "checkedInAt:OffsetDateTime"]),
    ("donation_events", "DonationEvent", "event_id", ["driveId:UUID", "donorId:UUID", "eventType:String", "status:String", "facilityId:UUID", "jurisdiction:String", "recordedBy:String", "recordedAt:OffsetDateTime"]),
    ("blood_collections", "BloodCollection", "collection_id", ["donorId:UUID", "driveId:UUID", "bagNumber:String", "volumeMl:Integer", "status:String", "facilityId:UUID", "jurisdiction:String", "collectedBy:String", "collectedAt:OffsetDateTime"]),
    ("blood_products", "BloodProduct", "product_id", ["productCode:String", "productName:String", "componentType:String", "bloodGroup:String", "shelfLifeDays:Integer", "status:String", "jurisdiction:String", "updatedAt:OffsetDateTime"]),
    ("blood_units", "BloodUnit", "unit_id", ["collectionId:UUID", "bagNumber:String", "bloodGroup:String", "rhFactor:String", "componentType:String", "status:String", "expiryAt:OffsetDateTime", "facilityId:UUID", "bloodBankId:UUID", "jurisdiction:String", "updatedAt:OffsetDateTime"]),
    ("blood_components", "BloodComponent", "component_id", ["unitId:UUID", "productId:UUID", "componentType:String", "volumeMl:Integer", "status:String", "facilityId:UUID", "jurisdiction:String", "preparedAt:OffsetDateTime"]),
    ("blood_processing_batches", "BloodProcessingBatch", "batch_id", ["unitId:UUID", "batchStatus:String", "testResultsJson:String", "quarantineReason:String", "releasedBy:String", "facilityId:UUID", "jurisdiction:String", "receivedAt:OffsetDateTime", "releasedAt:OffsetDateTime", "updatedAt:OffsetDateTime"]),
    ("blood_banks", "BloodBank", "blood_bank_id", ["facilityId:UUID", "bankName:String", "bankType:String", "status:String", "jurisdiction:String", "updatedAt:OffsetDateTime"]),
    ("blood_storage_locations", "BloodStorageLocation", "location_id", ["bloodBankId:UUID", "locationCode:String", "locationName:String", "status:String", "facilityId:UUID", "jurisdiction:String"]),
    ("blood_fridges", "BloodFridge", "fridge_id", ["bloodBankId:UUID", "locationId:UUID", "fridgeCode:String", "temperatureC:BigDecimal", "status:String", "facilityId:UUID", "jurisdiction:String", "updatedAt:OffsetDateTime"]),
    ("blood_inventory_balances", "BloodInventoryBalance", "balance_id", ["bloodBankId:UUID", "inventoryItemCode:String", "bloodGroup:String", "componentType:String", "quantityOnHand:Integer", "quantityReserved:Integer", "status:String", "facilityId:UUID", "jurisdiction:String", "updatedAt:OffsetDateTime"]),
    ("blood_stock_movements", "BloodStockMovement", "movement_id", ["bloodBankId:UUID", "unitId:UUID", "inventoryItemCode:String", "movementType:String", "quantity:Integer", "referenceType:String", "referenceId:String", "status:String", "facilityId:UUID", "jurisdiction:String", "movedBy:String", "movedAt:OffsetDateTime"]),
    ("blood_stock_reconciliations", "BloodStockReconciliation", "reconciliation_id", ["bloodBankId:UUID", "status:String", "varianceCount:Integer", "notes:String", "reconciledBy:String", "facilityId:UUID", "jurisdiction:String", "reconciledAt:OffsetDateTime"]),
    ("blood_orders", "BloodOrder", "order_id", ["orosOrderRef:String", "patientCpid:String", "bloodGroup:String", "componentType:String", "unitsRequested:Integer", "status:String", "priority:String", "facilityId:UUID", "orderingProvider:String", "jurisdiction:String", "updatedAt:OffsetDateTime"]),
    ("blood_order_items", "BloodOrderItem", "item_id", ["orderId:UUID", "componentType:String", "bloodGroup:String", "quantity:Integer", "status:String", "facilityId:UUID", "jurisdiction:String"]),
    ("blood_samples", "BloodSample", "sample_id", ["orderId:UUID", "sampleNumber:String", "status:String", "collectedBy:String", "facilityId:UUID", "jurisdiction:String", "collectedAt:OffsetDateTime"]),
    ("crossmatch_requests", "CrossmatchRequest", "request_id", ["orderId:UUID", "sampleId:UUID", "unitId:UUID", "status:String", "requestedBy:String", "facilityId:UUID", "jurisdiction:String", "requestedAt:OffsetDateTime"]),
    ("crossmatch_results", "CrossmatchResult", "result_id", ["requestId:UUID", "resultStatus:String", "resultNotes:String", "testedBy:String", "facilityId:UUID", "jurisdiction:String", "testedAt:OffsetDateTime"]),
    ("blood_reservations", "BloodReservation", "reservation_id", ["orderId:UUID", "unitId:UUID", "status:String", "reservedBy:String", "facilityId:UUID", "jurisdiction:String", "reservedAt:OffsetDateTime", "expiresAt:OffsetDateTime"]),
    ("blood_issues", "BloodIssue", "issue_id", ["orderId:UUID", "unitId:UUID", "reservationId:UUID", "status:String", "issuedBy:String", "facilityId:UUID", "jurisdiction:String", "issuedAt:OffsetDateTime"]),
    ("blood_dispatches", "BloodDispatch", "dispatch_id", ["issueId:UUID", "destinationFacilityId:UUID", "status:String", "dispatchedBy:String", "facilityId:UUID", "jurisdiction:String", "dispatchedAt:OffsetDateTime"]),
    ("blood_receipts", "BloodReceipt", "receipt_id", ["dispatchId:UUID", "status:String", "receivedBy:String", "facilityId:UUID", "jurisdiction:String", "receivedAt:OffsetDateTime"]),
    ("transfusion_episodes", "TransfusionEpisode", "episode_id", ["orderId:UUID", "issueId:UUID", "patientCpid:String", "status:String", "facilityId:UUID", "wardId:UUID", "startedBy:String", "jurisdiction:String", "startedAt:OffsetDateTime", "completedAt:OffsetDateTime", "updatedAt:OffsetDateTime"]),
    ("transfusion_observations", "TransfusionObservation", "observation_id", ["episodeId:UUID", "observationType:String", "valueNumeric:BigDecimal", "valueText:String", "unit:String", "observedBy:String", "facilityId:UUID", "jurisdiction:String", "observedAt:OffsetDateTime"]),
    ("transfusion_outcomes", "TransfusionOutcome", "outcome_id", ["episodeId:UUID", "outcomeStatus:String", "outcomeNotes:String", "recordedBy:String", "facilityId:UUID", "jurisdiction:String", "recordedAt:OffsetDateTime"]),
    ("adverse_transfusion_reactions", "AdverseTransfusionReaction", "reaction_id", ["episodeId:UUID", "reactionType:String", "severity:String", "status:String", "description:String", "reportedBy:String", "facilityId:UUID", "jurisdiction:String", "reportedAt:OffsetDateTime"]),
    ("haemovigilance_cases", "HaemovigilanceCase", "case_id", ["reactionId:UUID", "status:String", "investigationNotes:String", "assignedTo:String", "facilityId:UUID", "jurisdiction:String", "openedAt:OffsetDateTime", "closedAt:OffsetDateTime", "updatedAt:OffsetDateTime"]),
    ("blood_returns", "BloodReturn", "return_id", ["unitId:UUID", "bloodBankId:UUID", "reasonCode:String", "status:String", "returnedBy:String", "facilityId:UUID", "jurisdiction:String", "returnedAt:OffsetDateTime"]),
    ("blood_wastage", "BloodWastage", "wastage_id", ["unitId:UUID", "bloodBankId:UUID", "reasonCode:String", "status:String", "recordedBy:String", "facilityId:UUID", "jurisdiction:String", "recordedAt:OffsetDateTime"]),
    ("blood_discards", "BloodDiscard", "discard_id", ["unitId:UUID", "bloodBankId:UUID", "reasonCode:String", "status:String", "discardedBy:String", "facilityId:UUID", "jurisdiction:String", "discardedAt:OffsetDateTime"]),
    ("blood_forecasts", "BloodForecast", "forecast_id", ["bloodBankId:UUID", "bloodGroup:String", "componentType:String", "forecastPeriod:String", "predictedDemand:Integer", "predictedSupply:Integer", "status:String", "facilityId:UUID", "jurisdiction:String", "forecastAt:OffsetDateTime"]),
    ("blood_audit_events", "BloodAuditEvent", "audit_event_id", ["aggregateType:String", "aggregateId:String", "eventType:String", "actorId:String", "facilityId:UUID", "jurisdiction:String", "payloadJson:String", "occurredAt:OffsetDateTime"]),
]


def snake_to_camel(s: str) -> str:
    parts = s.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def field_to_column(field: str) -> str:
    result = []
    for i, c in enumerate(field):
        if c.isupper() and i > 0:
            result.append("_")
        result.append(c.lower())
    return "".join(result)


def java_type(t: str) -> str:
    return {
        "String": "String",
        "UUID": "UUID",
        "Integer": "Integer",
        "boolean": "boolean",
        "OffsetDateTime": "OffsetDateTime",
        "LocalDate": "LocalDate",
        "BigDecimal": "java.math.BigDecimal",
    }[t]


def imports_for_fields(fields):
    imps = {"jakarta.persistence.*", "java.time.OffsetDateTime", "java.util.UUID"}
    for f in fields:
        t = f.split(":")[1]
        if t == "LocalDate":
            imps.add("java.time.LocalDate")
        if t == "BigDecimal":
            imps.add("java.math.BigDecimal")
    return sorted(imps)


def gen_entity(table, class_name, id_col, fields):
    id_field = snake_to_camel(id_col)
    extra_imports = imports_for_fields(fields)
    lines = [f"package zw.gov.mohcc.impilo.madi.persistence.entity;", "",]
    for imp in extra_imports:
        if imp == "jakarta.persistence.*":
            lines.append("import jakarta.persistence.*;")
        else:
            lines.append(f"import {imp};")
    lines += ["", f"@Entity", f'@Table(name = "{table}", schema = "{SCHEMA}")', f"public class {class_name}Entity {{", "", "    @Id", "    @GeneratedValue(strategy = GenerationType.IDENTITY)", "    private Long id;", "", f"    @Column(name = \"{id_col}\", nullable = false, unique = true)", f"    private UUID {id_field};", "", "    @Column(name = \"tenant_id\", nullable = false)", "    private UUID tenantId;", ""]
    for f in fields:
        name, typ = f.split(":")
        col = field_to_column(name)
        jt = java_type(typ)
        short = jt.split(".")[-1]
        nullable = "nullable = false" if name in ("title", "personCpid", "patientCpid", "bagNumber", "bloodGroup", "componentType", "bankName", "productCode", "productName", "sampleNumber", "reactionType", "outcomeStatus", "resultStatus", "movementType", "inventoryItemCode", "reasonCode", "channel", "eventType", "observationType", "aggregateType", "aggregateId", "eventType") else ""
        col_def = f'@Column(name = "{col}"' + (f", {nullable}" if nullable else "") + ")"
        if typ == "String" and name.endswith("Notes") or name in ("description", "comments", "body", "investigationNotes", "outcomeNotes", "resultNotes", "screeningNotes", "notes", "payloadJson", "testResultsJson"):
            col_def = f'@Column(name = "{col}", columnDefinition = "TEXT")'
        lines.append(col_def)
        lines.append(f"    private {short} {name};")
        lines.append("")
    lines += ["    @Column(name = \"created_at\", nullable = false, updatable = false)", "    private OffsetDateTime createdAt;", "", "    @PrePersist", "    protected void onCreate() {", f"        if ({id_field} == null) {{", f"            {id_field} = UUID.randomUUID();", "        }", "        createdAt = OffsetDateTime.now();", "    }", ""]
    # getters/setters
    for prop, typ in [("id", "Long"), (id_field, "UUID"), ("tenantId", "UUID")] + [(f.split(":")[0], java_type(f.split(":")[1]).split(".")[-1]) for f in fields] + [("createdAt", "OffsetDateTime")]:
        cap = prop[0].upper() + prop[1:]
        jt = typ
        lines.append(f"    public {jt} get{cap}() {{ return {prop}; }}")
        lines.append(f"    public void set{cap}({jt} {prop}) {{ this.{prop} = {prop}; }}")
        lines.append("")
    lines.append("}")
    return "\n".join(lines)


def gen_repo(class_name, id_col):
    id_field = snake_to_camel(id_col)
    return f"""package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.{class_name}Entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface {class_name}Repository extends JpaRepository<{class_name}Entity, Long> {{
    Optional<{class_name}Entity> findBy{id_field[0].upper() + id_field[1:]}AndTenantId(UUID {id_field}, UUID tenantId);
    List<{class_name}Entity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}}
"""


def main():
    entity_dir = BASE / "entity"
    repo_dir = BASE / "repository"
    entity_dir.mkdir(parents=True, exist_ok=True)
    repo_dir.mkdir(parents=True, exist_ok=True)
    for table, class_name, id_col, fields in TABLES:
        (entity_dir / f"{class_name}Entity.java").write_text(gen_entity(table, class_name, id_col, fields))
        (repo_dir / f"{class_name}Repository.java").write_text(gen_repo(class_name, id_col))
    print(f"Generated {len(TABLES)} entities and repositories")


if __name__ == "__main__":
    main()
