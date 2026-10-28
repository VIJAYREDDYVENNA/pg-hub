-- Customisable floor plans: layout style per floor and room placement/order on the plan.

ALTER TABLE floors
    ADD COLUMN plan_layout VARCHAR(20) NOT NULL DEFAULT 'CORRIDOR' AFTER sort_order,
    ADD COLUMN has_lift    BOOLEAN     NOT NULL DEFAULT TRUE AFTER plan_layout,
    ADD COLUMN has_stairs  BOOLEAN     NOT NULL DEFAULT TRUE AFTER has_lift;

ALTER TABLE rooms
    -- TOP / BOTTOM side of the corridor, or AUTO (balanced automatically)
    ADD COLUMN plan_row   VARCHAR(10) NOT NULL DEFAULT 'AUTO' AFTER status,
    ADD COLUMN sort_order INT         NOT NULL DEFAULT 0 AFTER plan_row;
