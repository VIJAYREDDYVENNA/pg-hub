package com.hostelmanagement.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Builds safe Pageable instances from request parameters (whitelisted sort fields, capped size). */
public final class PageRequests {

    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(int page, int size, String sort, Set<String> allowedSorts, Sort defaultSort) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Sort resolved = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String field = parts[0].trim();
            if (allowedSorts.contains(field)) {
                Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                        ? Sort.Direction.DESC : Sort.Direction.ASC;
                resolved = Sort.by(dir, field);
            }
        }
        return PageRequest.of(safePage, safeSize, resolved);
    }
}
