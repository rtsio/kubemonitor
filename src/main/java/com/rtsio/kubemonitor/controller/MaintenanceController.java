package com.rtsio.kubemonitor.controller;

import com.rtsio.kubemonitor.model.MaintenanceRequest;
import com.rtsio.kubemonitor.service.MaintenanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@Slf4j
public class MaintenanceController {

    @Autowired
    private MaintenanceService maintenanceService;

    @GetMapping("/now")
    public Instant getCurrentTime() {

        return Instant.now();
    }

    @PostMapping("/maintenance")
    public MaintenanceRequest scheduleMaintenance(@RequestBody MaintenanceRequest maintenanceRequest) {

        log.info("Received request to schedule maintenance {}", maintenanceRequest);
        maintenanceService.scheduleMaintenance(maintenanceRequest);
        return maintenanceRequest;
    }
}
