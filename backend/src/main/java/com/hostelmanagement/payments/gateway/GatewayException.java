package com.hostelmanagement.payments.gateway;

import lombok.Getter;

/** A provider-level failure. {@link Kind} decides how callers react (reject, retry later, or report misconfiguration). */
@Getter
public class GatewayException extends RuntimeException {

    public enum Kind {
        /** Signature/authentication of a callback or webhook did not verify. Never retry; never trust the input. */
        INVALID_SIGNATURE,
        /** Provider unreachable or answered 5xx/429. The operation can be retried later. */
        UNAVAILABLE,
        /** The provider rejected the request (4xx). */
        REJECTED,
        /** A required setting is missing, e.g. no webhook secret configured. */
        NOT_CONFIGURED
    }

    private final Kind kind;

    public GatewayException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public GatewayException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }
}
