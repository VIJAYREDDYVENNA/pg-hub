package com.hostelmanagement.room;

import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.occupancy.BuildingViewService;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.RoomView;
import com.hostelmanagement.room.RoomService.RoomRequest;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "Rooms")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_HOSTEL_VIEW')")
public class RoomController {

    private final RoomService roomService;
    private final BuildingViewService viewService;

    @Operation(summary = "Rooms with computed occupancy. status: FULLY_OCCUPIED, PARTIALLY_OCCUPIED, VACANT, "
            + "UNDER_MAINTENANCE, VACATING_SOON, HAS_AVAILABLE")
    @GetMapping
    public PageResponse<RoomView> list(@RequestParam(required = false) Long hostelId,
                                       @RequestParam(required = false) Long buildingId,
                                       @RequestParam(required = false) Long floorId,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        List<RoomView> all = viewService.rooms(hostelId, buildingId, floorId, status, q);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int from = Math.min(Math.max(page, 0) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new PageResponse<>(all.subList(from, to), page, safeSize, all.size(),
                (int) Math.ceil(all.size() / (double) safeSize));
    }

    @Operation(summary = "Room details: bed-by-bed status, residents and payment status")
    @GetMapping("/{id}")
    public RoomView get(@PathVariable Long id) {
        return viewService.roomDetails(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_ROOM_MANAGE')")
    public RoomView create(@Valid @RequestBody RoomRequest req) {
        return viewService.roomDetails(roomService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROOM_MANAGE')")
    public RoomView update(@PathVariable Long id, @Valid @RequestBody RoomRequest req) {
        return viewService.roomDetails(roomService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_ROOM_MANAGE')")
    public void delete(@PathVariable Long id) {
        roomService.delete(id);
    }
}
