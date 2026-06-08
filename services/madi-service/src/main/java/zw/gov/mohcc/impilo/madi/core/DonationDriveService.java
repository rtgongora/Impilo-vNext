package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.domain.DonorStatus;
import zw.gov.mohcc.impilo.madi.domain.DriveStatus;
import zw.gov.mohcc.impilo.madi.domain.ScreeningResult;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.*;
import zw.gov.mohcc.impilo.madi.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DonationDriveService {

    private final DonationDriveRepository driveRepository;
    private final DonationDriveSlotRepository slotRepository;
    private final DonationDriveAttendanceRepository attendanceRepository;
    private final BloodCollectionRepository collectionRepository;
    private final DonorProfileRepository donorProfileRepository;
    private final DonorService donorService;
    private final DonationDriveSyncConflictRepository syncConflictRepository;
    private final MadiEventEmitter eventEmitter;

    public DonationDriveService(DonationDriveRepository driveRepository,
                                DonationDriveSlotRepository slotRepository,
                                DonationDriveAttendanceRepository attendanceRepository,
                                BloodCollectionRepository collectionRepository,
                                DonorProfileRepository donorProfileRepository,
                                DonorService donorService,
                                DonationDriveSyncConflictRepository syncConflictRepository,
                                MadiEventEmitter eventEmitter) {
        this.driveRepository = driveRepository;
        this.slotRepository = slotRepository;
        this.attendanceRepository = attendanceRepository;
        this.collectionRepository = collectionRepository;
        this.donorProfileRepository = donorProfileRepository;
        this.donorService = donorService;
        this.syncConflictRepository = syncConflictRepository;
        this.eventEmitter = eventEmitter;
    }

    @Transactional
    public DonationDriveEntity create(DonationDriveEntity drive) {
        drive.setStatus(DriveStatus.DRAFT.name());
        drive.setUpdatedAt(OffsetDateTime.now());
        return driveRepository.save(drive);
    }

    @Transactional(readOnly = true)
    public List<DonationDriveEntity> list(UUID tenantId) {
        return driveRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public DonationDriveEntity publish(UUID tenantId, UUID driveId, String publishedBy) {
        DonationDriveEntity drive = requireDrive(tenantId, driveId);
        if (!DriveStatus.DRAFT.name().equals(drive.getStatus())) {
            throw new IllegalStateException("Only DRAFT drives can be published");
        }
        drive.setStatus(DriveStatus.PUBLISHED.name());
        drive.setPublishedBy(publishedBy);
        drive.setUpdatedAt(OffsetDateTime.now());
        DonationDriveEntity saved = driveRepository.save(drive);
        eventEmitter.emit("DONATION_DRIVE", driveId.toString(), "DRIVE_PUBLISHED", "DONATION_DRIVE",
                driveId.toString(), Map.of("title", drive.getTitle()), tenantId);
        return saved;
    }

    @Transactional
    public DonationDriveAttendanceEntity register(UUID tenantId, UUID driveId, UUID donorId, UUID slotId, UUID facilityId) {
        DonationDriveEntity drive = requireDrive(tenantId, driveId);
        if (!List.of(DriveStatus.PUBLISHED.name(), DriveStatus.OPEN.name()).contains(drive.getStatus())) {
            throw new IllegalStateException("Drive is not open for registration");
        }
        donorProfileRepository.findByDonorIdAndTenantId(donorId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Donor not found"));
        DonationDriveAttendanceEntity attendance = new DonationDriveAttendanceEntity();
        attendance.setTenantId(tenantId);
        attendance.setDriveId(driveId);
        attendance.setSlotId(slotId);
        attendance.setDonorId(donorId);
        attendance.setFacilityId(facilityId);
        attendance.setStatus("REGISTERED");
        attendance.setRegisteredAt(OffsetDateTime.now());
        if (DriveStatus.PUBLISHED.name().equals(drive.getStatus())) {
            drive.setStatus(DriveStatus.OPEN.name());
            drive.setUpdatedAt(OffsetDateTime.now());
            driveRepository.save(drive);
        }
        return attendanceRepository.save(attendance);
    }

    @Transactional
    public DonorEligibilityScreeningEntity screenAtDrive(UUID tenantId, UUID driveId, UUID donorId,
                                                         ScreeningResult result, String screenedBy, UUID facilityId) {
        requireDrive(tenantId, driveId);
        return donorService.screen(tenantId, donorId, driveId, result, null, screenedBy, facilityId);
    }

    @Transactional
    public BloodCollectionEntity recordDonation(UUID tenantId, UUID driveId, UUID donorId,
                                                String bagNumber, Integer volumeMl, String collectedBy, UUID facilityId) {
        DonationDriveEntity drive = requireDrive(tenantId, driveId);
        DonorProfileEntity donor = donorProfileRepository.findByDonorIdAndTenantId(donorId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Donor not found"));
        if (!DonorStatus.ACTIVE.name().equals(donor.getStatus())) {
            throw new IllegalStateException("Donor is not eligible to donate");
        }
        BloodCollectionEntity collection = new BloodCollectionEntity();
        collection.setTenantId(tenantId);
        collection.setDonorId(donorId);
        collection.setDriveId(driveId);
        collection.setBagNumber(bagNumber);
        collection.setVolumeMl(volumeMl);
        collection.setCollectedBy(collectedBy);
        collection.setFacilityId(facilityId);
        collection.setStatus("COLLECTED");
        collection.setCollectedAt(OffsetDateTime.now());
        BloodCollectionEntity saved = collectionRepository.save(collection);
        eventEmitter.emit("DONATION_DRIVE", driveId.toString(), "DONATION_RECORDED", "BLOOD_COLLECTION",
                saved.getCollectionId().toString(),
                Map.of("bagNumber", bagNumber, "donorId", donorId.toString()), tenantId);
        return saved;
    }

    @Transactional
    public DonationDriveEntity close(UUID tenantId, UUID driveId) {
        DonationDriveEntity drive = requireDrive(tenantId, driveId);
        drive.setStatus(DriveStatus.CLOSED.name());
        drive.setUpdatedAt(OffsetDateTime.now());
        DonationDriveEntity saved = driveRepository.save(drive);
        eventEmitter.emit("DONATION_DRIVE", driveId.toString(), "DRIVE_CLOSED", "DONATION_DRIVE",
                driveId.toString(), Map.of(), tenantId);
        return saved;
    }

    @Transactional
    public DonationDriveSyncConflictEntity recordSyncConflict(UUID tenantId, UUID driveId, String localOpId,
                                                              Map<String, Object> localState,
                                                              Map<String, Object> serverState) {
        requireDrive(tenantId, driveId);
        DonationDriveSyncConflictEntity conflict = new DonationDriveSyncConflictEntity();
        conflict.setTenantId(tenantId);
        conflict.setDriveId(driveId);
        conflict.setLocalOpId(localOpId);
        conflict.setLocalState(localState != null ? localState : Map.of());
        conflict.setServerState(serverState);
        DonationDriveSyncConflictEntity saved = syncConflictRepository.save(conflict);
        eventEmitter.emit("DONATION_DRIVE", driveId.toString(), "SYNC_CONFLICT_RECORDED", "DONATION_DRIVE",
                saved.getConflictId().toString(), Map.of("localOpId", localOpId), tenantId);
        return saved;
    }

    @Transactional
    public DonationDriveSyncConflictEntity resolveSyncConflict(UUID tenantId, UUID driveId, UUID conflictId,
                                                               String resolution, String resolvedBy) {
        requireDrive(tenantId, driveId);
        DonationDriveSyncConflictEntity conflict = syncConflictRepository
                .findByConflictIdAndDriveIdAndTenantId(conflictId, driveId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Sync conflict not found"));
        if (conflict.getResolution() != null) {
            throw new IllegalStateException("Sync conflict already resolved");
        }
        if (!List.of("KEEP_LOCAL", "USE_SERVER", "MERGE").contains(resolution)) {
            throw new IllegalArgumentException("Invalid resolution: " + resolution);
        }
        conflict.setResolution(resolution);
        conflict.setResolvedBy(resolvedBy);
        conflict.setResolvedAt(OffsetDateTime.now());
        DonationDriveSyncConflictEntity saved = syncConflictRepository.save(conflict);
        eventEmitter.emit("DONATION_DRIVE", driveId.toString(), "SYNC_CONFLICT_RESOLVED", "DONATION_DRIVE",
                conflictId.toString(), Map.of("resolution", resolution), tenantId);
        return saved;
    }

    private DonationDriveEntity requireDrive(UUID tenantId, UUID driveId) {
        return driveRepository.findByDriveIdAndTenantId(driveId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Drive not found"));
    }
}
