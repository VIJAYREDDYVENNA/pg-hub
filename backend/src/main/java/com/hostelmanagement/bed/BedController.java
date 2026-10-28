package com.hostelmanagement.bed;

import com.hostelmanagement.bed.BedService.BedRequest;
import com.hostelmanagement.bed.BedService.BedResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Beds")
@RestController
@RequestMapping("/api/beds")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_ROOM_MANAGE')")
public class BedController {

    private final BedService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BedResponse create(@Valid @RequestBody BedRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public BedResponse update(@PathVariable Long id, @Valid @RequestBody BedRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
