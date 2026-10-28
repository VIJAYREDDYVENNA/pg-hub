package com.hostelmanagement.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(Instant timestamp, int status, String code, String message,
                       Map<String, String> fieldErrors, String path) {
}
