package com.example.devpilot_ai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public Mono<String> health() {
        return Mono.just("DevPilot AI is running!");
    }
}
