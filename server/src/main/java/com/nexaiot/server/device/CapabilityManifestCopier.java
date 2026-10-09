package com.nexaiot.server.device;

import java.util.ArrayList;

public final class CapabilityManifestCopier {

    private CapabilityManifestCopier() {
    }

    public static CapabilityManifest copy(CapabilityManifest source) {
        if (source == null) {
            return null;
        }

        CapabilityManifest target = new CapabilityManifest();
        target.setVersion(source.getVersion());
        target.setSource(source.getSource());
        target.setTemplateId(source.getTemplateId());
        target.setCapabilities(new ArrayList<>());

        if (source.getCapabilities() != null) {
            for (CapabilityDefinition sourceDefinition : source.getCapabilities()) {
                target.getCapabilities().add(copyDefinition(sourceDefinition));
            }
        }

        return target;
    }

    private static CapabilityDefinition copyDefinition(CapabilityDefinition source) {
        CapabilityDefinition target = new CapabilityDefinition();
        target.setKey(source.getKey());
        target.setName(source.getName());
        target.setCategory(source.getCategory());
        target.setDataType(source.getDataType());
        target.setUnit(source.getUnit());
        target.setSemanticType(source.getSemanticType());
        target.setValueKind(source.getValueKind());
        target.setReadOnly(source.isReadOnly());
        target.setChartable(source.isChartable());
        target.setDescription(source.getDescription());

        if (source.getConstraints() != null) {
            CapabilityConstraints constraints = new CapabilityConstraints();
            constraints.setMin(source.getConstraints().getMin());
            constraints.setMax(source.getConstraints().getMax());
            constraints.setStep(source.getConstraints().getStep());
            target.setConstraints(constraints);
        }

        if (source.getControl() != null) {
            target.setControl(new CapabilityControl(source.getControl().getType()));
        }

        if (source.getOptions() != null) {
            target.setOptions(source.getOptions().stream()
                    .map(option -> new CapabilityOption(option.getValue(), option.getLabel()))
                    .toList());
        }

        if (source.getUi() != null) {
            target.setUi(new CapabilityUi(source.getUi().getIcon(), source.getUi().getOrder()));
        }

        return target;
    }
}
