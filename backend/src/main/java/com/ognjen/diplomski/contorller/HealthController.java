package com.ognjen.diplomski.contorller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {
    @GetMapping()
    private ResponseEntity<String> health(){
        return ResponseEntity.ok("100% health");
    }
}
