package com.hostelmanagement.owner;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** An owner account (tenant). Every hostel and its data belong to exactly one owner. */
@Getter
@Setter
@Entity
@Table(name = "owners")
public class Owner extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}
