package com.amazonpricemonitor.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CreateProductRequest {

    @NotBlank
    @Size(max = 2048)
    private String amazonUrl;

    @Size(max = 512)
    private String displayName;

    @NotNull
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "100.00")
    private BigDecimal thresholdPct;

    private boolean active = true;

    public String getAmazonUrl() {
        return amazonUrl;
    }

    public void setAmazonUrl(String amazonUrl) {
        this.amazonUrl = amazonUrl;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public BigDecimal getThresholdPct() {
        return thresholdPct;
    }

    public void setThresholdPct(BigDecimal thresholdPct) {
        this.thresholdPct = thresholdPct;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
