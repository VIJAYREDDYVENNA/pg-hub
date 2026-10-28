package com.hostelmanagement.occupancy;

import com.hostelmanagement.occupancy.dto.OccupancyDtos.BedView;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.BuildingView;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.ResidentOnBed;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.RoomView;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.Permissions;

import java.util.List;

/**
 * Occupancy screens are available with HOSTEL_VIEW, but they embed data that other permissions protect. A user who
 * can only view hostels sees which beds are occupied, not who sleeps in them, how to reach them, or what they owe.
 * <ul>
 *   <li>Identity and contact details need RESIDENT_VIEW.</li>
 *   <li>Rent agreed with the resident and balances need PAYMENT_VIEW.</li>
 * </ul>
 */
public final class ResidentPrivacy {

    public static final String HIDDEN_NAME = "Resident";

    private ResidentPrivacy() {
    }

    public static boolean canSeeIdentity() {
        return CurrentUser.get().has(Permissions.RESIDENT_VIEW);
    }

    public static boolean canSeeMoney() {
        return CurrentUser.get().has(Permissions.PAYMENT_VIEW);
    }

    public static ResidentOnBed redact(ResidentOnBed r, AuthUser user) {
        if (r == null) {
            return null;
        }
        boolean identity = user.has(Permissions.RESIDENT_VIEW);
        boolean money = user.has(Permissions.PAYMENT_VIEW);
        if (identity && money) {
            return r;
        }
        return new ResidentOnBed(r.residentId(), r.admissionId(),
                identity ? r.name() : HIDDEN_NAME, identity ? r.mobile() : null, identity && r.whatsappOptIn(),
                r.admissionDate(), r.allocatedSince(), r.expectedCheckoutDate(), r.checkoutConfirmed(),
                r.daysToCheckout(), r.vacatingSoon(), r.checkoutOverdue(),
                money ? r.monthlyRent() : null, money ? r.outstandingAmount() : null,
                money ? r.overdueAmount() : null, money ? r.paymentStatus() : null);
    }

    public static RoomView redact(RoomView v) {
        AuthUser user = CurrentUser.get();
        if (user.has(Permissions.RESIDENT_VIEW) && user.has(Permissions.PAYMENT_VIEW)) {
            return v;
        }
        List<BedView> beds = v.beds().stream()
                .map(b -> new BedView(b.id(), b.label(), b.state(), b.bedStatus(), b.maintenance(), b.rent(),
                        redact(b.resident(), user), b.reservation()))
                .toList();
        return new RoomView(v.id(), v.roomNumber(), v.roomType(), v.configuredStatus(), v.hostelId(), v.hostelName(),
                v.buildingId(), v.buildingName(), v.floorId(), v.floorName(), v.floorLevel(), v.status(),
                v.vacatingSoon(), v.roomUnderMaintenance(), v.totalBeds(), v.occupiedBeds(), v.availableBeds(),
                v.reservedBeds(), v.maintenanceBeds(), v.vacatingSoonBeds(), v.nextCheckoutDate(),
                v.monthlyRentPerBed(), v.securityDeposit(), v.amenities(), beds, v.planRow(), v.sortOrder());
    }

    public static List<RoomView> redact(List<RoomView> rooms) {
        return rooms.stream().map(ResidentPrivacy::redact).toList();
    }

    public static BuildingView redact(BuildingView v) {
        return new BuildingView(v.hostelId(), v.hostelName(), v.buildings(), v.selectedBuildingId(), v.selectedFloorId(),
                v.vacancyWarningDays(), v.summary(), redact(v.rooms()));
    }
}
