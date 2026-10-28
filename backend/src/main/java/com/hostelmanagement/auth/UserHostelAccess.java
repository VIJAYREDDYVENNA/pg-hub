package com.hostelmanagement.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(UserHostelAccess.Key.class)
@Table(name = "user_hostel_access")
public class UserHostelAccess {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "hostel_id")
    private Long hostelId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long userId;
        private Long hostelId;
    }
}
