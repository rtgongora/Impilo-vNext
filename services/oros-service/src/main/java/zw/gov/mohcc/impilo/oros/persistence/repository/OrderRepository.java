package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    /** Orders of a given type placed since a moment — the provider dashboard's daily counts. */
    long countByTenantIdAndOrderTypeAndPlacedAtGreaterThanEqual(
            java.util.UUID tenantId,
            zw.gov.mohcc.impilo.oros.domain.OrderType orderType,
            java.time.OffsetDateTime since);


    Optional<OrderEntity> findByOrderId(String orderId);

    /**
     * Tenant-scoped by-id lookup. Request-facing access MUST use this (not {@link #findByOrderId})
     * so an order/result cannot be read or mutated across tenant boundaries (orderIds are
     * time-sortable ULIDs and partially predictable / leaked via events and share slips).
     */
    Optional<OrderEntity> findByTenantIdAndOrderId(UUID tenantId, String orderId);

    /** Idempotency lookup for externally-originated orders (FHIR/HL7 inbound). */
    Optional<OrderEntity> findByTenantIdAndExternalOrderRef(UUID tenantId, String externalOrderRef);

    List<OrderEntity> findByPatientCpidOrderByPlacedAtDesc(String patientCpid);

    /**
     * Duplicate-order guard for teleconsult provenance (TM-B7): finds non-terminal orders
     * with the same originating source_ref and coded item within a tenant. A non-empty result
     * means an equivalent order for this teleconsult referral + code is still active.
     */
    List<OrderEntity> findByTenantIdAndSourceRefAndZiboOrderCodeAndStatusNotIn(
            UUID tenantId, String sourceRef, String ziboOrderCode, Collection<OrderStatus> statuses);

    /**
     * Finds all orders for a patient within a specific tenant.
     *
     * @param tenantId     the tenant UUID
     * @param patientCpid  the patient's CPID
     * @return list of matching orders
     */
    List<OrderEntity> findByTenantIdAndPatientCpid(UUID tenantId, String patientCpid);

    Page<OrderEntity> findByFacilityIdAndOrderTypeAndStatusAndPriority(
            UUID facilityId, OrderType type, OrderStatus status, OrderPriority priority, Pageable pageable);

    Page<OrderEntity> findByFacilityIdAndOrderTypeAndStatus(
            UUID facilityId, OrderType type, OrderStatus status, Pageable pageable);

    Page<OrderEntity> findByFacilityIdAndOrderType(
            UUID facilityId, OrderType type, Pageable pageable);

    Page<OrderEntity> findByFacilityIdAndStatus(
            UUID facilityId, OrderStatus status, Pageable pageable);

    Page<OrderEntity> findByFacilityId(UUID facilityId, Pageable pageable);

    Page<OrderEntity> findByFacilityIdAndWorkspaceIdAndOrderTypeAndStatusAndPriority(
            UUID facilityId, UUID workspaceId, OrderType type, OrderStatus status,
            OrderPriority priority, Pageable pageable);

    /**
     * Null-tolerant order search within a tenant, filtering by any combination of patient
     * (client), requester (matched against either the placing actor or the referring provider),
     * coarse status, and order type. Most-recent first.
     */
    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.tenantId = :tenantId
              AND (:patientCpid IS NULL OR o.patientCpid = :patientCpid)
              AND (:status IS NULL OR o.status = :status)
              AND (:orderType IS NULL OR o.orderType = :orderType)
              AND (:encounterRef IS NULL OR o.encounterRef = :encounterRef)
              AND (:requester IS NULL OR o.placedBy = :requester OR o.referringProviderId = :requester)
            ORDER BY o.placedAt DESC, o.createdAt DESC
            """)
    List<OrderEntity> search(@Param("tenantId") UUID tenantId,
                             @Param("patientCpid") String patientCpid,
                             @Param("requester") String requester,
                             @Param("status") OrderStatus status,
                             @Param("orderType") OrderType orderType,
                             @Param("encounterRef") String encounterRef);

    /**
     * Imaging-team worklist: IMAGING orders at the facility whose fine-grained imaging state is in
     * the requested set, most-recently-updated first.
     */
    List<OrderEntity> findByTenantIdAndFacilityIdAndOrderTypeAndImagingStateInOrderByUpdatedAtDesc(
            UUID tenantId, UUID facilityId, OrderType orderType, Collection<ImagingWorkflowState> states);

    /**
     * Category-agnostic fulfilment worklist: orders of a given type at the facility whose
     * generalised {@code workflow_state} (V011) is in the requested set, most-recently-updated
     * first. Backs the lab / procedure / assessment worklists.
     */
    List<OrderEntity> findByTenantIdAndFacilityIdAndOrderTypeAndWorkflowStateInOrderByUpdatedAtDesc(
            UUID tenantId, UUID facilityId, OrderType orderType, Collection<String> workflowStates);

    /**
     * Requester results-inbox: orders for a requester (placing actor or referring provider) that
     * have results to review — either coarse lab status (RESULT_AVAILABLE/RELEASED/REVIEWED) or a
     * fine-grained imaging reporting state.
     */
    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.tenantId = :tenantId
              AND (:requester IS NULL OR o.placedBy = :requester OR o.referringProviderId = :requester)
              AND (o.status IN :coarse OR o.imagingState IN :imaging)
            ORDER BY o.updatedAt DESC
            """)
    List<OrderEntity> resultsInbox(@Param("tenantId") UUID tenantId,
                                   @Param("requester") String requester,
                                   @Param("coarse") Collection<OrderStatus> coarse,
                                   @Param("imaging") Collection<ImagingWorkflowState> imaging);

    /**
     * Operational reconciliation: IMAGING orders dwelling in the given imaging states, optionally
     * only those untouched since {@code before} (SLA age threshold) and/or scoped to a facility.
     * Oldest-first so the most-stuck surface at the top.
     */
    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.tenantId = :tenantId
              AND o.orderType = zw.gov.mohcc.impilo.oros.domain.OrderType.IMAGING
              AND o.imagingState IN :states
              AND (:facilityId IS NULL OR o.facilityId = :facilityId)
              AND (:before IS NULL OR o.updatedAt < :before)
            ORDER BY o.updatedAt ASC
            """)
    List<OrderEntity> stuckImaging(@Param("tenantId") UUID tenantId,
                                   @Param("facilityId") UUID facilityId,
                                   @Param("states") Collection<ImagingWorkflowState> states,
                                   @Param("before") java.time.OffsetDateTime before);

    /**
     * Workload distribution: count of IMAGING orders grouped by imaging state, tenant- (and
     * optionally facility-) scoped. Returns rows of {@code [ImagingWorkflowState, Long]}.
     */
    @Query("""
            SELECT o.imagingState, COUNT(o) FROM OrderEntity o
            WHERE o.tenantId = :tenantId
              AND o.orderType = zw.gov.mohcc.impilo.oros.domain.OrderType.IMAGING
              AND o.imagingState IS NOT NULL
              AND (:facilityId IS NULL OR o.facilityId = :facilityId)
            GROUP BY o.imagingState
            """)
    List<Object[]> imagingStateCounts(@Param("tenantId") UUID tenantId,
                                      @Param("facilityId") UUID facilityId);
}
