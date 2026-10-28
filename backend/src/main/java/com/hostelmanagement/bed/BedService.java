package com.hostelmanagement.bed;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.occupancy.OccupancyQueries;
import com.hostelmanagement.room.Room;
import com.hostelmanagement.room.RoomService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BedService {

    private final BedRepository bedRepository;
    private final RoomService roomService;
    private final OccupancyQueries occupancyQueries;
    private final AuditService auditService;
    private final com.hostelmanagement.subscription.SubscriptionLimitService limits;

    public record BedRequest(@NotNull Long roomId,
                             @NotBlank @Size(max = 20) String bedLabel,
                             @Pattern(regexp = "AVAILABLE|MAINTENANCE|INACTIVE") String status,
                             @DecimalMin("0.00") BigDecimal rentOverride) {
    }

    public record BedResponse(Long id, Long roomId, String bedLabel, String status, BigDecimal rentOverride) {
        static BedResponse from(Bed b) {
            return new BedResponse(b.getId(), b.getRoomId(), b.getBedLabel(), b.getStatus(), b.getRentOverride());
        }
    }

    public record BedRef(Bed bed, Room room) {
    }

    /** Loads a bed with its room, enforcing hostel access. */
    public BedRef getRef(Long bedId) {
        Bed bed = bedRepository.findByIdAndDeletedAtIsNull(bedId).orElseThrow(() -> ApiException.notFound("Bed"));
        Room room;
        try {
            room = roomService.getEntity(bed.getRoomId());
        } catch (ApiException e) {
            throw ApiException.notFound("Bed");
        }
        return new BedRef(bed, room);
    }

    @Transactional
    public BedResponse create(BedRequest req) {
        Room room = roomService.getEntity(req.roomId());
        // The per-room cap used to be enforced only when a room was created or resized; a single bed added here bypassed it
        if (bedRepository.countByRoomIdAndDeletedAtIsNull(room.getId()) >= RoomService.MAX_BEDS_PER_ROOM) {
            throw ApiException.conflict("A room can have at most " + RoomService.MAX_BEDS_PER_ROOM + " beds");
        }
        limits.assertCanAddBeds(com.hostelmanagement.security.CurrentUser.get().ownerId(), room.getHostelId(), 1);
        if (bedRepository.existsByRoomIdAndBedLabelIgnoreCaseAndDeletedAtIsNull(room.getId(), req.bedLabel().trim())) {
            throw ApiException.conflict("Bed " + req.bedLabel() + " already exists in this room");
        }
        Bed bed = new Bed();
        bed.setRoomId(room.getId());
        apply(bed, req);
        bedRepository.save(bed);
        auditService.log("BED_CREATED", "BED", bed.getId(), "Room " + room.getRoomNumber() + " bed " + bed.getBedLabel());
        return BedResponse.from(bed);
    }

    @Transactional
    public BedResponse update(Long id, BedRequest req) {
        BedRef ref = getRef(id);
        Bed bed = ref.bed();
        String label = req.bedLabel().trim();
        if (!bed.getBedLabel().equalsIgnoreCase(label)
                && bedRepository.existsByRoomIdAndBedLabelIgnoreCaseAndDeletedAtIsNull(bed.getRoomId(), label)) {
            throw ApiException.conflict("Bed " + label + " already exists in this room");
        }
        if ("INACTIVE".equals(req.status()) && occupancyQueries.bedIsOccupied(id)) {
            throw ApiException.conflict("An occupied bed cannot be deactivated");
        }
        apply(bed, req);
        auditService.log("BED_UPDATED", "BED", id, "status=" + bed.getStatus());
        return BedResponse.from(bed);
    }

    @Transactional
    public void delete(Long id) {
        BedRef ref = getRef(id);
        if (occupancyQueries.bedIsOccupied(id) || occupancyQueries.bedIsReserved(id)) {
            throw ApiException.conflict("This bed is occupied or reserved and cannot be removed");
        }
        ref.bed().setDeletedAt(Instant.now());
        auditService.log("BED_DELETED", "BED", id, "Room " + ref.room().getRoomNumber() + " bed " + ref.bed().getBedLabel());
    }

    private void apply(Bed bed, BedRequest req) {
        bed.setBedLabel(req.bedLabel().trim());
        bed.setStatus(req.status() == null ? "AVAILABLE" : req.status());
        bed.setRentOverride(req.rentOverride() == null ? null : Money.of(req.rentOverride()));
    }
}
