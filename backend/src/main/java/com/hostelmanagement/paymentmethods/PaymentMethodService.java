package com.hostelmanagement.paymentmethods;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.common.crypto.FieldCipher;
import com.hostelmanagement.common.files.FileAttachment;
import com.hostelmanagement.common.files.FileStorageService;
import com.hostelmanagement.paymentmethods.PaymentMethodDtos.BankInfo;
import com.hostelmanagement.paymentmethods.PaymentMethodDtos.ManualPaymentInfo;
import com.hostelmanagement.paymentmethods.PaymentMethodDtos.SettingsView;
import com.hostelmanagement.paymentmethods.PaymentMethodDtos.UpdateRequest;
import com.hostelmanagement.paymentmethods.PaymentMethodDtos.UpiInfo;
import com.hostelmanagement.paymentmethods.PaymentMethodRepository.Row;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * How an owner accepts rent. A hostel uses its own row if it has one, otherwise the owner's default row.
 *
 * <p>Protection of sensitive data: the bank account number is encrypted at rest, bound to the owner, and never returned by
 * the settings endpoint (only the last four digits). Residents and staff who legitimately need to pay or to share the
 * details get them through {@link #manualInfo}. Nothing here confirms a payment; see {@link PaymentMethodDtos#MANUAL_NOTE}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository repository;
    private final FieldCipher cipher;
    private final HostelAccessService hostelAccess;
    private final FileStorageService files;
    private final AuditService auditService;

    // ------------------------------------------------------------------ owner-side settings

    @Transactional(readOnly = true)
    public SettingsView view(Long hostelId) {
        AuthUser me = settingsUser(hostelId);
        Resolved r = resolve(me.ownerId(), hostelId);
        return toView(r.row(), hostelId, r.inherited());
    }

    @Transactional
    public SettingsView update(Long hostelId, UpdateRequest req) {
        AuthUser me = settingsUser(hostelId);
        Long ownerId = me.ownerId();
        Resolved current = resolve(ownerId, hostelId);
        Row old = current.row();

        boolean upi = req.acceptsUpi();
        boolean bank = req.acceptsBankTransfer();
        String upiId = blank(req.upiId());
        String holder = blank(req.bankAccountHolder());
        String ifsc = blank(req.bankIfsc()) == null ? null : req.bankIfsc().trim().toUpperCase(Locale.ROOT);
        if (upi && upiId == null) {
            throw ApiException.badRequest("Enter the UPI ID you accept payments on, or switch UPI off");
        }
        if (bank && (holder == null || ifsc == null)) {
            throw ApiException.badRequest("Bank transfer needs the account holder's name and the IFSC code");
        }
        if (!req.acceptsCash() && !upi && !bank && !req.acceptsOnline()) {
            throw ApiException.badRequest("Accept at least one way of paying: cash, UPI, bank transfer or online payment");
        }

        byte[] encrypted = old.accountEnc();
        String last4 = old.last4();
        String number = req.bankAccountNumber();
        if (number != null) {
            if (number.isEmpty()) {
                encrypted = null;
                last4 = null;
            } else {
                if (!cipher.available()) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ENCRYPTION_UNAVAILABLE",
                            "Bank account numbers cannot be saved because the server has no DATA_ENCRYPTION_KEY configured. "
                                    + "Ask whoever runs the server to set it (see docs/PAYMENT_METHODS.md).");
                }
                encrypted = cipher.encrypt(number, context(ownerId));
                last4 = number.substring(number.length() - 4);
            }
        }
        if (bank && encrypted == null) {
            throw ApiException.badRequest("Enter the account number for bank transfers");
        }

        Row updated = new Row(ownerId, hostelId, req.acceptsCash(), upi, upiId, blank(req.upiPayeeName()), old.qrFileId(), bank, holder,
                blank(req.bankName()), blank(req.bankBranch()), ifsc, encrypted, last4, req.acceptsOnline(), blank(req.instructions()),
                old.updatedAt());
        repository.save(updated, me.userId());
        auditService.log("PAYMENT_SETTINGS_UPDATED", "PAYMENT_SETTINGS", hostelId,
                (hostelId == null ? "owner default" : "hostel " + hostelId) + ": changed " + String.join(", ", changedFields(old, updated)));
        return view(hostelId);
    }

    /** Removes a hostel's own settings so that it follows the owner default again. */
    @Transactional
    public SettingsView removeOverride(Long hostelId) {
        if (hostelId == null) {
            throw ApiException.badRequest("The owner default cannot be removed; edit it instead");
        }
        AuthUser me = settingsUser(hostelId);
        Optional<Row> existing = repository.find(me.ownerId(), hostelId);
        repository.delete(me.ownerId(), hostelId);
        existing.map(Row::qrFileId).filter(Objects::nonNull).ifPresent(files::remove);
        auditService.log("PAYMENT_SETTINGS_UPDATED", "PAYMENT_SETTINGS", hostelId, "hostel " + hostelId + ": now follows the owner default");
        return view(hostelId);
    }

    @Transactional
    public SettingsView setQr(Long hostelId, MultipartFile file) {
        AuthUser me = settingsUser(hostelId);
        Resolved current = resolve(me.ownerId(), hostelId);
        FileAttachment stored = files.store(file, "PAYMENT_QR", null, hostelId, "UPI_QR", true);
        Long previous = repository.find(me.ownerId(), hostelId).map(Row::qrFileId).orElse(null);
        Row r = current.row();
        repository.save(new Row(me.ownerId(), hostelId, r.acceptsCash(), r.acceptsUpi(), r.upiId(), r.upiPayeeName(), stored.getId(),
                r.acceptsBank(), r.holder(), r.bankName(), r.branch(), r.ifsc(), r.accountEnc(), r.last4(), r.acceptsOnline(),
                r.instructions(), r.updatedAt()), me.userId());
        if (previous != null) {
            files.remove(previous);
        }
        auditService.log("PAYMENT_QR_REPLACED", "PAYMENT_SETTINGS", hostelId, previous == null ? "QR added" : "QR replaced");
        return view(hostelId);
    }

    @Transactional
    public SettingsView removeQr(Long hostelId) {
        AuthUser me = settingsUser(hostelId);
        Row r = repository.find(me.ownerId(), hostelId).orElse(null);
        if (r != null && r.qrFileId() != null) {
            Long fileId = r.qrFileId();
            repository.save(new Row(me.ownerId(), hostelId, r.acceptsCash(), r.acceptsUpi(), r.upiId(), r.upiPayeeName(), null,
                    r.acceptsBank(), r.holder(), r.bankName(), r.branch(), r.ifsc(), r.accountEnc(), r.last4(), r.acceptsOnline(),
                    r.instructions(), r.updatedAt()), me.userId());
            files.remove(fileId);
            auditService.log("PAYMENT_QR_REMOVED", "PAYMENT_SETTINGS", hostelId, "QR removed");
        }
        return view(hostelId);
    }

    // ------------------------------------------------------------------ what residents / staff see

    /** For staff who share payment details with residents (PAYMENT_VIEW). Scoped to hostels the caller can access. */
    @Transactional(readOnly = true)
    public ManualPaymentInfo instructionsForStaff(Long hostelId) {
        AuthUser me = CurrentUser.get();
        if (hostelId != null) {
            hostelAccess.checkHostel(hostelId);
        }
        return manualInfo(me.ownerId(), hostelId);
    }

    /** The details a resident needs to pay manually. Contains the full account number: only call after authorising. */
    @Transactional(readOnly = true)
    public ManualPaymentInfo manualInfo(Long ownerId, Long hostelId) {
        Row r = resolve(ownerId, hostelId).row();
        UpiInfo upi = r.acceptsUpi() && r.upiId() != null ? new UpiInfo(r.upiId(), r.upiPayeeName(), r.qrFileId() != null) : null;
        BankInfo bank = null;
        if (r.acceptsBank() && r.accountEnc() != null && cipher.available()) {
            try {
                bank = new BankInfo(r.holder(), r.bankName(), r.branch(), r.ifsc(), cipher.decrypt(r.accountEnc(), context(ownerId)));
            } catch (RuntimeException e) {
                log.error("Could not decrypt the bank account of owner {}; leaving bank details out", ownerId);
            }
        }
        boolean anyManual = r.acceptsCash() || upi != null || bank != null;
        return new ManualPaymentInfo(r.acceptsCash(), upi, bank, r.acceptsOnline(), r.instructions(),
                anyManual ? PaymentMethodDtos.MANUAL_NOTE : null);
    }

    @Transactional(readOnly = true)
    public boolean isOnlineAccepted(Long ownerId, Long hostelId) {
        return resolve(ownerId, hostelId).row().acceptsOnline();
    }

    /** Refuses online checkout/links for hostels where the owner has switched online payment off. */
    @Transactional(readOnly = true)
    public void requireOnlineAccepted(Long ownerId, Long hostelId) {
        if (!isOnlineAccepted(ownerId, hostelId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ONLINE_PAYMENTS_OFF",
                    "Online payment is switched off for this hostel. Ask the owner to enable it in Settings > Payment methods.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<FileAttachment> qrFile(Long ownerId, Long hostelId) {
        Row r = resolve(ownerId, hostelId).row();
        return r.acceptsUpi() && r.qrFileId() != null ? Optional.of(files.get(r.qrFileId())) : Optional.empty();
    }

    // ------------------------------------------------------------------ helpers

    private record Resolved(Row row, boolean inherited) {
    }

    private Resolved resolve(Long ownerId, Long hostelId) {
        if (hostelId != null) {
            Optional<Row> own = repository.find(ownerId, hostelId);
            if (own.isPresent()) {
                return new Resolved(own.get(), false);
            }
        }
        Row def = repository.find(ownerId, null).orElse(Row.defaults(ownerId, null));
        // A hostel that follows the default is presented under its own id so edits create its override
        return new Resolved(hostelId == null ? def : withHostel(def, hostelId), hostelId != null);
    }

    private static Row withHostel(Row r, Long hostelId) {
        return new Row(r.ownerId(), hostelId, r.acceptsCash(), r.acceptsUpi(), r.upiId(), r.upiPayeeName(), r.qrFileId(), r.acceptsBank(),
                r.holder(), r.bankName(), r.branch(), r.ifsc(), r.accountEnc(), r.last4(), r.acceptsOnline(), r.instructions(), r.updatedAt());
    }

    /** Only the owner, or a user the owner trusted with settings, may see or change payment methods. */
    private AuthUser settingsUser(Long hostelId) {
        AuthUser me = CurrentUser.get();
        if (!me.has("SETTINGS_MANAGE")) {
            throw ApiException.forbidden("You do not have permission to manage payment methods");
        }
        if (hostelId != null) {
            hostelAccess.checkHostel(hostelId);
        }
        return me;
    }

    private SettingsView toView(Row r, Long hostelId, boolean inherited) {
        String masked = r.last4() == null ? null : "•••• " + r.last4();
        return new SettingsView(hostelId, inherited, r.acceptsCash(), r.acceptsUpi(), r.upiId(), r.upiPayeeName(), r.qrFileId(),
                r.acceptsBank(), r.holder(), r.bankName(), r.branch(), r.ifsc(), masked, r.accountEnc() != null, r.acceptsOnline(),
                r.instructions(), cipher.available(), r.updatedAt(), PaymentMethodDtos.MANUAL_NOTE);
    }

    private static List<String> changedFields(Row a, Row b) {
        List<String> changed = new ArrayList<>();
        if (a.acceptsCash() != b.acceptsCash()) changed.add("cash");
        if (a.acceptsUpi() != b.acceptsUpi()) changed.add("UPI on/off");
        if (!Objects.equals(a.upiId(), b.upiId())) changed.add("UPI ID");
        if (!Objects.equals(a.upiPayeeName(), b.upiPayeeName())) changed.add("UPI payee");
        if (a.acceptsBank() != b.acceptsBank()) changed.add("bank transfer on/off");
        if (!Objects.equals(a.holder(), b.holder()) || !Objects.equals(a.bankName(), b.bankName())
                || !Objects.equals(a.branch(), b.branch()) || !Objects.equals(a.ifsc(), b.ifsc())) changed.add("bank details");
        if (!java.util.Arrays.equals(a.accountEnc(), b.accountEnc())) changed.add("bank account number");
        if (a.acceptsOnline() != b.acceptsOnline()) changed.add("online payments");
        if (!Objects.equals(a.instructions(), b.instructions())) changed.add("instructions");
        return changed.isEmpty() ? List.of("nothing") : changed;
    }

    /** Binds the ciphertext to the owner, so it cannot be copied into another owner's row. */
    private static String context(Long ownerId) {
        return "bank-account:owner:" + ownerId;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
