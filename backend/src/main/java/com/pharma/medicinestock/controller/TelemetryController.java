package com.pharma.medicinestock.controller;

import com.pharma.medicinestock.dto.TelemetryEvent;
import com.pharma.medicinestock.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @PostMapping
    public ResponseEntity<Void> record(
            @RequestBody TelemetryEvent event,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        telemetryService.record(username, event);
        return ResponseEntity.ok().build();
    }
}
