package com.hostelmanagement.building;

import com.hostelmanagement.building.BuildingService.BuildingRequest;
import com.hostelmanagement.building.BuildingService.BuildingResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Buildings")
@RestController
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_HOSTEL_VIEW')")
public class BuildingController {

    private final BuildingService service;

    @GetMapping
    public List<BuildingResponse> list(@RequestParam Long hostelId) {
        return service.list(hostelId);
    }

    @GetMapping("/{id}")
    public BuildingResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_HOSTEL_MANAGE')")
    public BuildingResponse create(@Valid @RequestBody BuildingRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_HOSTEL_MANAGE')")
    public BuildingResponse update(@PathVariable Long id, @Valid @RequestBody BuildingRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_HOSTEL_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
