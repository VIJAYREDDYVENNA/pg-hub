package com.hostelmanagement.paymentmethods;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class PaymentMethodDtos {

    /** UPI virtual payment address: handle@bank, e.g. sunrise.pg@okhdfcbank. */
    public static final String UPI_REGEX = "^$|^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9]{1,31}$";
    /** Indian bank IFSC: four letters, a zero, six alphanumerics. */
    public static final String IFSC_REGEX = "^$|^[A-Z]{4}0[A-Z0-9]{6}$";
    public static final String ACCOUNT_REGEX = "^$|^[0-9]{9,18}$";

    /** Shown wherever manual payment details are displayed, so a static QR is never mistaken for automatic confirmation. */
    public static final String MANUAL_NOTE = "UPI and bank-transfer payments made outside the online payment button are not "
            + "detected automatically. A QR code or UPI ID only shows where to send money; it does not tell the hostel who paid "
            + "or for which invoice. The hostel confirms the payment manually once it sees it, so keep your transaction reference.";

    private PaymentMethodDtos() {
    }

    /** What the owner edits. The bank account number is never returned, only masked. */
    public record SettingsView(Long hostelId, boolean inherited, boolean acceptsCash, boolean acceptsUpi, String upiId,
                               String upiPayeeName, Long qrFileId, boolean acceptsBankTransfer, String bankAccountHolder,
                               String bankName, String bankBranch, String bankIfsc, String bankAccountMasked,
                               boolean hasBankAccount, boolean acceptsOnline, String instructions,
                               boolean encryptionAvailable, Instant updatedAt, String note) {
    }

    /**
     * {@code bankAccountNumber}: null keeps the stored number, an empty string removes it, digits replace it.
     * Whether the UPI/bank fields are required follows from the corresponding "accepts" switch (checked in the service).
     */
    public record UpdateRequest(
            @NotNull Boolean acceptsCash,
            @NotNull Boolean acceptsUpi,
            @Pattern(regexp = UPI_REGEX, message = "Enter a UPI ID such as name@bank") @Size(max = 100) String upiId,
            @Size(max = 100) String upiPayeeName,
            @NotNull Boolean acceptsBankTransfer,
            @Size(max = 150) String bankAccountHolder,
            @Size(max = 100) String bankName,
            @Size(max = 100) String bankBranch,
            @Pattern(regexp = IFSC_REGEX, message = "Enter an 11-character IFSC such as HDFC0001234") String bankIfsc,
            @Pattern(regexp = ACCOUNT_REGEX, message = "Account number must be 9 to 18 digits") String bankAccountNumber,
            @NotNull Boolean acceptsOnline,
            @Size(max = 1000) String instructions) {
    }

    public record UpiInfo(String upiId, String payeeName, boolean hasQr) {
    }

    public record BankInfo(String accountHolder, String bankName, String branch, String ifsc, String accountNumber) {
    }

    /** What a resident (or staff sharing details with one) may see. Includes the full account number on purpose. */
    public record ManualPaymentInfo(boolean cash, UpiInfo upi, BankInfo bank, boolean online, String instructions, String note) {
    }
}
