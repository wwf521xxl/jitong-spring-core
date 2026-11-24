package com.jitong.core.settings;

import java.math.BigDecimal;

public record SettingsView(int duplicateMinutes, BigDecimal tolerance) {
}
