package com.hostelmanagement.floor;

import com.hostelmanagement.floor.FloorService.FloorRequest;
import com.hostelmanagement.floor.FloorService.FloorResponse;
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

@Tag(name = "Floors")
@RestController
@RequestMapping("/api/floors")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_HOSTEL_VIEW')")
public class FloorController {

    private final FloorService service;

    @GetMapping
    public List<FloorResponse> list(@RequestParam Long buildingId) {
        return service.list(buildingId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_ROOM_MANAGE')")
    public FloorResponse create(@Valid @RequestBody FloorRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROOM_MANAGE')")
    public FloorResponse update(@PathVariable Long id, @Valid @RequestBody FloorRequest req) {
        return service.update(id, req);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/layout")
    @PreAuthorize("hasAuthority('PERM_ROOM_MANAGE')")
    public FloorResponse saveLayout(@PathVariable Long id, @Valid @RequestBody FloorService.LayoutRequest req) {
        return service.saveLayout(id, req);
    }

    @PutMapping("/order")
    @PreAuthorize("hasAuthority('PERM_ROOM_MANAGE')")
    public List<FloorResponse> reorder(@RequestParam Long buildingId, @Valid @RequestBody FloorService.FloorOrderRequest req) {
        return service.reorder(buildingId, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_ROOM_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
