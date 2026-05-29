package com.scf.common.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/actuator/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "scf-server", "version", "1.1.0");
    }
}
