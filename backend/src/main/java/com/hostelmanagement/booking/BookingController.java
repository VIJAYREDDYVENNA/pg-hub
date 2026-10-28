package com.hostelmanagement.booking;

import com.hostelmanagement.booking.BookingService.BookingDto;
import com.hostelmanagement.booking.BookingService.BookingRequest;
import com.hostelmanagement.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Bookings (bed reservations)")
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_ENQUIRY_MANAGE')")
public class BookingController {

    private final BookingService service;

    @GetMapping
    public PageResponse<BookingDto> list(@RequestParam(required = false) Long hostelId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(hostelId, status, page, size);
    }

    @GetMapping("/{id}")
    public BookingDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto create(@Valid @RequestBody BookingRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/cancel")
    public BookingDto cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.cancel(id, body == null ? null : body.get("reason"));
    }
}
