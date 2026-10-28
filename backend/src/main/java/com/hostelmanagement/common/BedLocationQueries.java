package com.hostelmanagement.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves human-readable locations (hostel / building / floor / room / bed) for bed IDs in one query. */
@Repository
@RequiredArgsConstructor
public class BedLocationQueries {

    private final NamedParameterJdbcTemplate jdbc;

    public record BedLocation(Long bedId, String bedLabel, Long roomId, String roomNumber, Long floorId,
                              String floorName, Long buildingId, String buildingName, Long hostelId, String hostelName) {
        public String display() {
            return hostelName + " - " + buildingName + ", " + floorName + ", Room " + roomNumber + " / Bed " + bedLabel;
        }
    }

    public Map<Long, BedLocation> locate(Collection<Long> bedIds) {
        Map<Long, BedLocation> result = new HashMap<>();
        if (bedIds == null || bedIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT b.id bed_id, b.bed_label, r.id room_id, r.room_number, f.id floor_id, f.name floor_name,
                       bl.id building_id, bl.name building_name, h.id hostel_id, h.name hostel_name
                FROM beds b JOIN rooms r ON r.id = b.room_id JOIN floors f ON f.id = r.floor_id
                JOIN buildings bl ON bl.id = f.building_id JOIN hostels h ON h.id = r.hostel_id
                WHERE b.id IN (:ids)""", new MapSqlParameterSource("ids", bedIds), rs -> {
            result.put(rs.getLong("bed_id"), new BedLocation(rs.getLong("bed_id"), rs.getString("bed_label"),
                    rs.getLong("room_id"), rs.getString("room_number"), rs.getLong("floor_id"),
                    rs.getString("floor_name"), rs.getLong("building_id"), rs.getString("building_name"),
                    rs.getLong("hostel_id"), rs.getString("hostel_name")));
        });
        return result;
    }

    public BedLocation locate(Long bedId) {
        return locate(List.of(bedId)).get(bedId);
    }
}
