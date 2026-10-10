package com.nexaiot.server.device;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CapabilityManifestValidator {

    private CapabilityManifestValidator() {
    }

    public static boolean isValid(CapabilityManifest manifest) {
        if (manifest == null
                || !Integer.valueOf(CapabilityManifest.SUPPORTED_VERSION).equals(manifest.getVersion())
                || manifest.getSource() == null) {
            return false;
        }

        List<CapabilityDefinition> definitions = manifest.getCapabilities();
        if (definitions == null) {
            return false;
        }

        Set<String> keys = new HashSet<>();
        for (CapabilityDefinition definition : definitions) {
            if (definition == null
                    || definition.getKey() == null
                    || definition.getKey().isBlank()
                    || definition.getName() == null
                    || definition.getName().isBlank()
                    || !keys.add(definition.getKey())
                    || definition.getCategory() == null
                    || definition.getDataType() == null) {
                return false;
            }

            CapabilityConstraints constraints = definition.getConstraints();
            if (constraints != null) {
                if (constraints.getMin() != null && constraints.getMax() != null
                        && constraints.getMin() > constraints.getMax()) {
                    return false;
                }
                if (constraints.getStep() != null && constraints.getStep() <= 0) {
                    return false;
                }
            }

            if (definition.getOptions() != null
                    && !validOptions(definition.getOptions())) {
                return false;
            }

            if (definition.getCategory() == CapabilityCategory.CONTROL) {
                if (definition.isReadOnly()
                        || definition.getControl() == null
                        || definition.getControl().getType() == null) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean validOptions(List<CapabilityOption> options) {
        if (options == null) {
            return false;
        }

        Set<String> values = new HashSet<>();
        for (CapabilityOption option : options) {
            if (option == null
                    || option.getValue() == null
                    || option.getValue().isBlank()
                    || option.getLabel() == null
                    || option.getLabel().isBlank()
                    || !values.add(option.getValue())) {
                return false;
            }
        }

        return true;
    }
}
