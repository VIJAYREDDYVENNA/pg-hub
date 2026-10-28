package com.hostelmanagement.hostel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HostelRepository extends JpaRepository<Hostel, Long> {

    List<Hostel> findByIdInAndDeletedAtIsNullOrderByNameAsc(Collection<Long> ids);

    Optional<Hostel> findByIdAndDeletedAtIsNull(Long id);
}
