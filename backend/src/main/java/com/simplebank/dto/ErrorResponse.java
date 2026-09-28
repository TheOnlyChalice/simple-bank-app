package com.simplebank.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Every error from the API has this shape, so the React app (Step 4)
 * can always show "message", and "fieldErrors" next to form inputs.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors,
        LocalDateTime timestamp
) {
}
