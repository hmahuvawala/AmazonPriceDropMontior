package com.amazonpricemonitor.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CreateProductRequest {

    @NotBlank
    @Size(max = 2048)
    private String amazonUrl;

    @NotBlank
    @Size(max = 512)
    private String displayName;

    @DecimalMin(value = "0.01")
    @DecimalMax(value = "100.00")
    private BigDecimal thresholdPct;

    @DecimalMin(value = "0.01")
    @DecimalMax(value = "9999999999.99")
    private BigDecimal thresholdAmount;

    private boolean active = true;

    @AssertTrue(message = "At least one of thresholdPct or thresholdAmount must be set")
    public boolean isAtLeastOneThresholdSet() {
        return thresholdPct != null || thresholdAmount != null;
    }

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

    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    public void setThresholdAmount(BigDecimal thresholdAmount) {
        this.thresholdAmount = thresholdAmount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
