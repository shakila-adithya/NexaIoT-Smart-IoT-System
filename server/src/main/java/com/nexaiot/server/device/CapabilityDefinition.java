package com.nexaiot.server.device;

import java.util.ArrayList;
import java.util.List;

public class CapabilityDefinition {

    private String key;
    private String name;
    private CapabilityCategory category;
    private CapabilityDataType dataType;
    private String unit;
    private String semanticType;
    private CapabilityValueKind valueKind;
    private boolean readOnly;
    private boolean chartable;
    private String description;
    private CapabilityConstraints constraints;
    private CapabilityControl control;
    private List<CapabilityOption> options = new ArrayList<>();
    private CapabilityUi ui;

    public CapabilityDefinition() {
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CapabilityCategory getCategory() { return category; }
    public void setCategory(CapabilityCategory category) { this.category = category; }
    public CapabilityDataType getDataType() { return dataType; }
    public void setDataType(CapabilityDataType dataType) { this.dataType = dataType; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSemanticType() { return semanticType; }
    public void setSemanticType(String semanticType) { this.semanticType = semanticType; }
    public CapabilityValueKind getValueKind() { return valueKind; }
    public void setValueKind(CapabilityValueKind valueKind) { this.valueKind = valueKind; }
    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
    public boolean isChartable() { return chartable; }
    public void setChartable(boolean chartable) { this.chartable = chartable; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public CapabilityConstraints getConstraints() { return constraints; }
    public void setConstraints(CapabilityConstraints constraints) { this.constraints = constraints; }
    public CapabilityControl getControl() { return control; }
    public void setControl(CapabilityControl control) { this.control = control; }
    public List<CapabilityOption> getOptions() { return options; }
    public void setOptions(List<CapabilityOption> options) { this.options = options; }
    public CapabilityUi getUi() { return ui; }
    public void setUi(CapabilityUi ui) { this.ui = ui; }
}
