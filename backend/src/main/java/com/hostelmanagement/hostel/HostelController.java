package com.hostelmanagement.hostel;

import com.hostelmanagement.hostel.dto.HostelDtos.HostelRequest;
import com.hostelmanagement.hostel.dto.HostelDtos.HostelResponse;
import com.hostelmanagement.occupancy.BuildingViewService;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.BuildingView;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.StructureNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Hostels")
@RestController
@RequestMapping("/api/hostels")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_HOSTEL_VIEW')")
public class HostelController {

    private final HostelService service;
    private final BuildingViewService buildingViewService;

    @Operation(summary = "List hostels the current user can access, with occupancy statistics")
    @GetMapping
    public List<HostelResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public HostelResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_HOSTEL_MANAGE')")
    public HostelResponse create(@Valid @RequestBody HostelRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_HOSTEL_MANAGE')")
    public HostelResponse update(@PathVariable Long id, @Valid @RequestBody HostelRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_HOSTEL_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_HOSTEL_MANAGE')")
    public HostelResponse uploadImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return service.uploadImage(id, file);
    }

    @Operation(summary = "Buildings, floors and rooms of a hostel (property structure tree)")
    @GetMapping("/{id}/structure")
    public List<StructureNode> structure(@PathVariable Long id) {
        return buildingViewService.structure(id);
    }

    @Operation(summary = "Visual building view: floors and room tiles with computed status")
    @GetMapping("/{id}/building-view")
    public BuildingView buildingView(@PathVariable Long id,
                                     @RequestParam(required = false) Long buildingId,
                                     @RequestParam(required = false) Long floorId) {
        return buildingViewService.buildingView(id, buildingId, floorId);
    }
}
