package com.rtsio.kubemonitor.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an instance of maintenance
 */
@Data
public class MaintenanceState {

    private MaintenanceRequest maintenanceRequest;
    private MaintenanceStatus maintenanceStatus;
    private Map<String, Integer> originalReplicaCounts;

    public MaintenanceState(MaintenanceRequest maintenanceRequest) {

        this.maintenanceRequest = maintenanceRequest;
        this.maintenanceStatus = MaintenanceStatus.NOT_STARTED;
        this.originalReplicaCounts = new HashMap<>();
    }
}
