package com.hostelmanagement.occupancy;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One bed with its current allocation / reservation / maintenance facts, as read from the database. */
public record BedStateRow(
        Long hostelId, String hostelName,
        Long buildingId, String buildingName,
        Long floorId, String floorName, int floorLevel,
        Long roomId, String roomNumber, String roomType, String roomStatus,
        BigDecimal roomRent, BigDecimal roomDeposit, String amenities,
        Long bedId, String bedLabel, String bedStatus, BigDecimal bedRentOverride,
        Long allocationId, LocalDate allocationStart,
        Long admissionId, LocalDate admissionDate, LocalDate expectedCheckoutDate, boolean checkoutConfirmed,
        BigDecimal admissionRent,
        Long residentId, String residentName, String residentMobile, boolean residentWhatsappOptIn,
        Long bookingId, String bookingGuestName, LocalDate bookingExpectedDate,
        boolean bedMaintenanceOpen, boolean roomMaintenanceOpen,
        String planRow, int roomSortOrder) {

    public boolean occupied() {
        return allocationId != null;
    }

    /** True when the bed itself (or its whole room) is unavailable because of maintenance. */
    public boolean underMaintenance() {
        return "MAINTENANCE".equals(bedStatus) || "MAINTENANCE".equals(roomStatus)
                || bedMaintenanceOpen || roomMaintenanceOpen;
    }

    public boolean reserved() {
        return bookingId != null;
    }

    public BigDecimal effectiveRent() {
        return bedRentOverride != null ? bedRentOverride : roomRent;
    }
}
