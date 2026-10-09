package com.nexaiot.server.device;

public class CapabilityConstraints {

    private Double min;
    private Double max;
    private Double step;

    public CapabilityConstraints() {
    }

    public Double getMin() {
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    public Double getStep() {
        return step;
    }

    public void setStep(Double step) {
        this.step = step;
    }
}
