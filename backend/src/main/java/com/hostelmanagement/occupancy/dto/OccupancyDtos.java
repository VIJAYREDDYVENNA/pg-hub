package com.hostelmanagement.occupancy.dto;

import com.hostelmanagement.occupancy.BedState;
import com.hostelmanagement.occupancy.RoomStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class OccupancyDtos {

    private OccupancyDtos() {
    }

    public record ResidentOnBed(Long residentId, Long admissionId, String name, String mobile, boolean whatsappOptIn,
                                LocalDate admissionDate, LocalDate allocatedSince, LocalDate expectedCheckoutDate,
                                boolean checkoutConfirmed, Integer daysToCheckout, boolean vacatingSoon,
                                boolean checkoutOverdue, BigDecimal monthlyRent,
                                /* Filled only on room details */ BigDecimal outstandingAmount,
                                BigDecimal overdueAmount, String paymentStatus) {
    }

    public record ReservationOnBed(Long bookingId, String guestName, LocalDate expectedAdmissionDate) {
    }

    public record BedView(Long id, String label, BedState state, String bedStatus, boolean maintenance,
                          BigDecimal rent, ResidentOnBed resident, ReservationOnBed reservation) {
    }

    public record RoomView(Long id, String roomNumber, String roomType, String configuredStatus,
                           Long hostelId, String hostelName, Long buildingId, String buildingName,
                           Long floorId, String floorName, int floorLevel,
                           RoomStatus status, boolean vacatingSoon, boolean roomUnderMaintenance,
                           int totalBeds, int occupiedBeds, int availableBeds, int reservedBeds, int maintenanceBeds,
                           int vacatingSoonBeds, LocalDate nextCheckoutDate,
                           BigDecimal monthlyRentPerBed, BigDecimal securityDeposit, String amenities,
                           List<BedView> beds, String planRow, int sortOrder) {
    }

    public record RoomSummary(int totalRooms, int fullyOccupied, int partiallyOccupied, int vacant,
                              int vacatingSoon, int underMaintenance, int totalBeds, int occupiedBeds,
                              int availableBeds, int reservedBeds, int maintenanceBeds) {
    }

    public record FloorNode(Long id, String name, int levelNumber, int roomCount, int totalBeds, int occupiedBeds,
                            int sortOrder, String planLayout, boolean hasLift, boolean hasStairs) {
    }

    public record BuildingNode(Long id, String name, List<FloorNode> floors) {
    }

    public record BuildingView(Long hostelId, String hostelName, List<BuildingNode> buildings,
                               Long selectedBuildingId, Long selectedFloorId, int vacancyWarningDays,
                               RoomSummary summary, List<RoomView> rooms) {
    }

    public record StructureRoom(Long id, String roomNumber, String roomType, int totalBeds,
                                BigDecimal monthlyRentPerBed, BigDecimal securityDeposit, String amenities,
                                String status, String planRow, int sortOrder) {
    }

    public record StructureFloor(Long id, String name, int levelNumber, String description, int sortOrder,
                                 String planLayout, boolean hasLift, boolean hasStairs, List<StructureRoom> rooms) {
    }

    public record StructureNode(Long id, String name, String description, String status, int sortOrder,
                                List<StructureFloor> floors) {
    }
}
