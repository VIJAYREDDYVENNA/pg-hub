package com.hostelmanagement.floor;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "floors")
public class Floor extends BaseEntity {

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "level_number", nullable = false)
    private int levelNumber;

    @Column(length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** CORRIDOR (rooms on both sides of a corridor), SINGLE_ROW or GRID. */
    @Column(name = "plan_layout", nullable = false, length = 20)
    private String planLayout = "CORRIDOR";

    @Column(name = "has_lift", nullable = false)
    private boolean hasLift = true;

    @Column(name = "has_stairs", nullable = false)
    private boolean hasStairs = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static String defaultName(int level) {
        if (level == 0) {
            return "Ground Floor";
        }
        if (level < 0) {
            return "Basement " + (-level);
        }
        int mod100 = level % 100;
        String suffix = (mod100 >= 11 && mod100 <= 13) ? "th"
                : switch (level % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return level + suffix + " Floor";
    }
}
