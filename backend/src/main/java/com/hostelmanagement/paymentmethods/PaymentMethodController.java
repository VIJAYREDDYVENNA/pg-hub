package com.hostelmanagement.paymentmethods;

import com.hostelmanagement.paymentmethods.PaymentMethodDtos.ManualPaymentInfo;
import com.hostelmanagement.paymentmethods.PaymentMethodDtos.SettingsView;
import com.hostelmanagement.paymentmethods.PaymentMethodDtos.UpdateRequest;
import com.hostelmanagement.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** {@code hostelId} absent = the owner-wide default; present = that hostel's own override. */
@Tag(name = "Payment methods")
@RestController
@RequestMapping("/api/payment-settings")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService service;

    @Operation(summary = "Payment methods an owner accepts (bank account number is masked)")
    @GetMapping
    public SettingsView get(@RequestParam(required = false) Long hostelId) {
        return service.view(hostelId);
    }

    @PutMapping
    public SettingsView update(@RequestParam(required = false) Long hostelId, @Valid @RequestBody UpdateRequest req) {
        return service.update(hostelId, req);
    }

    @Operation(summary = "Make a hostel follow the owner default again")
    @DeleteMapping
    public SettingsView removeOverride(@RequestParam Long hostelId) {
        return service.removeOverride(hostelId);
    }

    @PostMapping("/qr")
    public SettingsView uploadQr(@RequestParam(required = false) Long hostelId, @RequestParam("file") MultipartFile file) {
        return service.setQr(hostelId, file);
    }

    @DeleteMapping("/qr")
    public SettingsView deleteQr(@RequestParam(required = false) Long hostelId) {
        return service.removeQr(hostelId);
    }

    @Operation(summary = "Details staff may share with a resident, including the full account number")
    @GetMapping("/instructions")
    public ManualPaymentInfo instructions(@RequestParam(required = false) Long hostelId) {
        CurrentUser.require("PAYMENT_VIEW");
        return service.instructionsForStaff(hostelId);
    }
}
