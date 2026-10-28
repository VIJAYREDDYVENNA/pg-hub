package com.hostelmanagement.resident.dto;

import com.hostelmanagement.admission.dto.AdmissionDtos.AdmissionDto;
import com.hostelmanagement.common.files.FileStorageService.FileDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ResidentDtos {

    public static final String PHONE_REGEX = "^[+]?[0-9 ]{10,15}$";
    public static final String OPTIONAL_PHONE_REGEX = "^$|^[+]?[0-9 ]{10,15}$";

    private ResidentDtos() {
    }

    public record ResidentRequest(
            @NotNull Long hostelId,
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "Enter a valid 10-digit mobile number") String mobile,
            @Pattern(regexp = OPTIONAL_PHONE_REGEX, message = "Enter a valid mobile number") String alternateMobile,
            @Email @Size(max = 150) String email,
            @Past LocalDate dateOfBirth,
            @Size(max = 20) String gender,
            @Size(max = 500) String address,
            @Size(max = 150) String emergencyContactName,
            @Pattern(regexp = OPTIONAL_PHONE_REGEX, message = "Enter a valid phone number") String emergencyContactPhone,
            @Size(max = 40) String idDocumentType,
            @Size(max = 60) String idDocumentNumber,
            @Size(max = 150) String guardianName,
            @Pattern(regexp = OPTIONAL_PHONE_REGEX, message = "Enter a valid phone number") String guardianPhone,
            @Size(max = 40) String guardianRelation,
            @Size(max = 100) String occupation,
            boolean whatsappOptIn,
            @Size(max = 1000) String notes) {
    }

    public record ResidentSummary(Long id, String fullName, String mobile, String email, Long hostelId,
                                  String hostelName, String status, Long admissionId, LocalDate admissionDate,
                                  LocalDate expectedCheckoutDate, String roomNumber, String bedLabel, Long roomId,
                                  String photoUrl, boolean whatsappOptIn, BigDecimal outstandingAmount,
                                  BigDecimal overdueAmount) {
    }

    public record Balance(BigDecimal outstanding, BigDecimal overdue, BigDecimal credit, BigDecimal depositHeld) {
    }

    public record ResidentProfile(Long id, Long hostelId, String hostelName, String fullName, String photoUrl,
                                  String mobile, String alternateMobile, String email, LocalDate dateOfBirth,
                                  String gender, String address, String emergencyContactName,
                                  String emergencyContactPhone, String idDocumentType, String idDocumentNumber,
                                  String guardianName, String guardianPhone, String guardianRelation,
                                  String occupation, String status, boolean whatsappOptIn, Instant whatsappOptInAt,
                                  String notes, Instant createdAt, AdmissionDto currentAdmission,
                                  List<AdmissionDto> admissionHistory, Balance balance, List<FileDto> documents) {
    }

    public record ConsentRequest(boolean whatsappOptIn) {
    }
}
