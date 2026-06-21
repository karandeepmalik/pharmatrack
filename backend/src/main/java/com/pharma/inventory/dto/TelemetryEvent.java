package com.pharma.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryEvent {
    private String eventName;
    private String page;
    private Map<String, Object> properties;
}
