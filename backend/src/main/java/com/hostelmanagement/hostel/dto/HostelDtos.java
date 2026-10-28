package com.hostelmanagement.hostel.dto;

import com.hostelmanagement.hostel.HostelType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class HostelDtos {

    private HostelDtos() {
    }

    public record HostelRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull HostelType hostelType,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "PIN code must be 6 digits") String pinCode,
            @Pattern(regexp = "^$|^[+0-9 ]{7,20}$", message = "Enter a valid phone number") String contactNumber,
            @Email @Size(max = 150) String email,
            Long managerUserId,
            @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
    }

    public record OccupancyStats(long totalRooms, long totalBeds, long occupiedBeds, long vacantBeds,
                                 long reservedBeds, long maintenanceBeds, BigDecimal occupancyPercent) {
        public static OccupancyStats empty() {
            return new OccupancyStats(0, 0, 0, 0, 0, 0, BigDecimal.ZERO);
        }
    }

    public record HostelResponse(Long id, String name, HostelType hostelType, String address, String city,
                                 String state, String pinCode, String contactNumber, String email,
                                 Long managerUserId, String managerName, String imageUrl, String status,
                                 long buildingCount, OccupancyStats stats) {
    }
}
