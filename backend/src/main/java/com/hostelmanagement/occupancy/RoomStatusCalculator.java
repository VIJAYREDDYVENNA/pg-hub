package com.hostelmanagement.occupancy;

import com.hostelmanagement.occupancy.dto.OccupancyDtos.BedView;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.ReservationOnBed;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.ResidentOnBed;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.RoomSummary;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.RoomView;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure functions that turn bed facts into room display states.
 *
 * <ul>
 *   <li>A bed is OCCUPIED when it has an active allocation (even if maintenance is open on it).</li>
 *   <li>Otherwise it is MAINTENANCE when the bed/room is flagged or has an open blocking maintenance request.</li>
 *   <li>Otherwise RESERVED when it holds a live booking, else AVAILABLE.</li>
 *   <li>Room: FULLY_OCCUPIED when every usable bed is occupied, PARTIALLY_OCCUPIED when at least one bed is
 *       occupied and at least one usable bed is free, VACANT when nothing is occupied and a bed is usable,
 *       UNDER_MAINTENANCE when nothing is occupied and the room (or all of its beds) is unavailable.</li>
 *   <li>"Vacating soon" is an additional flag: an occupant has an expected checkout within the warning window
 *       (or past it without being checked out).</li>
 * </ul>
 */
public final class RoomStatusCalculator {

    private RoomStatusCalculator() {
    }

    public static BedState bedState(BedStateRow r) {
        if (r.occupied()) {
            return BedState.OCCUPIED;
        }
        if (r.underMaintenance()) {
            return BedState.MAINTENANCE;
        }
        if (r.reserved()) {
            return BedState.RESERVED;
        }
        return BedState.AVAILABLE;
    }

    public static boolean isVacatingSoon(LocalDate expectedCheckout, LocalDate today, int warningDays) {
        return expectedCheckout != null && !expectedCheckout.isAfter(today.plusDays(warningDays));
    }

    /** Groups rows (ordered by room) into room views, preserving order. */
    public static List<RoomView> toRooms(Collection<BedStateRow> rows, LocalDate today, int warningDays) {
        Map<Long, List<BedStateRow>> byRoom = new LinkedHashMap<>();
        for (BedStateRow r : rows) {
            byRoom.computeIfAbsent(r.roomId(), k -> new ArrayList<>()).add(r);
        }
        List<RoomView> rooms = new ArrayList<>(byRoom.size());
        byRoom.values().forEach(list -> rooms.add(toRoom(list, today, warningDays)));
        return rooms;
    }

    public static RoomView toRoom(List<BedStateRow> beds, LocalDate today, int warningDays) {
        BedStateRow first = beds.get(0);
        int occupied = 0, available = 0, reserved = 0, maintenance = 0, vacatingSoonBeds = 0;
        LocalDate nextCheckout = null;
        List<BedView> bedViews = new ArrayList<>(beds.size());
        for (BedStateRow r : beds) {
            if (r.bedId() == null) {
                continue; // room without beds (LEFT JOIN)
            }
            BedState state = bedState(r);
            ResidentOnBed resident = null;
            switch (state) {
                case OCCUPIED -> {
                    occupied++;
                    boolean soon = isVacatingSoon(r.expectedCheckoutDate(), today, warningDays);
                    if (soon) {
                        vacatingSoonBeds++;
                    }
                    if (r.expectedCheckoutDate() != null
                            && (nextCheckout == null || r.expectedCheckoutDate().isBefore(nextCheckout))) {
                        nextCheckout = r.expectedCheckoutDate();
                    }
                    Integer days = r.expectedCheckoutDate() == null ? null
                            : (int) ChronoUnit.DAYS.between(today, r.expectedCheckoutDate());
                    resident = new ResidentOnBed(r.residentId(), r.admissionId(), r.residentName(), r.residentMobile(),
                            r.residentWhatsappOptIn(), r.admissionDate(), r.allocationStart(), r.expectedCheckoutDate(),
                            r.checkoutConfirmed(), days, soon,
                            r.expectedCheckoutDate() != null && r.expectedCheckoutDate().isBefore(today),
                            r.admissionRent(), null, null, null);
                }
                case MAINTENANCE -> maintenance++;
                case RESERVED -> reserved++;
                case AVAILABLE -> available++;
            }
            ReservationOnBed reservation = r.reserved()
                    ? new ReservationOnBed(r.bookingId(), r.bookingGuestName(), r.bookingExpectedDate()) : null;
            bedViews.add(new BedView(r.bedId(), r.bedLabel(), state, r.bedStatus(), r.underMaintenance(),
                    r.effectiveRent(), resident, reservation));
        }
        int total = bedViews.size();
        boolean roomMaintenance = "MAINTENANCE".equals(first.roomStatus()) || first.roomMaintenanceOpen();
        int usable = total - maintenance;
        RoomStatus status;
        if (occupied == 0) {
            status = (roomMaintenance || usable == 0) ? RoomStatus.UNDER_MAINTENANCE : RoomStatus.VACANT;
        } else if (occupied >= usable) {
            status = RoomStatus.FULLY_OCCUPIED;
        } else {
            status = RoomStatus.PARTIALLY_OCCUPIED;
        }
        return new RoomView(first.roomId(), first.roomNumber(), first.roomType(), first.roomStatus(),
                first.hostelId(), first.hostelName(), first.buildingId(), first.buildingName(),
                first.floorId(), first.floorName(), first.floorLevel(),
                status, vacatingSoonBeds > 0, roomMaintenance,
                total, occupied, available, reserved, maintenance, vacatingSoonBeds, nextCheckout,
                first.roomRent(), first.roomDeposit(), first.amenities(), bedViews, first.planRow(), first.roomSortOrder());
    }

    public static RoomSummary summarize(Collection<RoomView> rooms) {
        int full = 0, partial = 0, vacant = 0, soon = 0, maint = 0, beds = 0, occ = 0, avail = 0, res = 0, mBeds = 0;
        for (RoomView r : rooms) {
            switch (r.status()) {
                case FULLY_OCCUPIED -> full++;
                case PARTIALLY_OCCUPIED -> partial++;
                case VACANT -> vacant++;
                case UNDER_MAINTENANCE -> maint++;
            }
            if (r.vacatingSoon()) {
                soon++;
            }
            beds += r.totalBeds();
            occ += r.occupiedBeds();
            avail += r.availableBeds();
            res += r.reservedBeds();
            mBeds += r.maintenanceBeds();
        }
        return new RoomSummary(rooms.size(), full, partial, vacant, soon, maint, beds, occ, avail, res, mBeds);
    }
}
