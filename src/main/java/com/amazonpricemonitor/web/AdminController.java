package com.amazonpricemonitor.web;

import com.amazonpricemonitor.service.PriceMonitoringService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PriceMonitoringService priceMonitoringService;

    public AdminController(PriceMonitoringService priceMonitoringService) {
        this.priceMonitoringService = priceMonitoringService;
    }

    @PostMapping("/run-checks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void runChecksNow() {
        priceMonitoringService.runChecksForActiveProducts();
    }
}
