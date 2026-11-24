package com.jitong.core.settings;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingRepository repository;

    @Transactional(readOnly = true)
    public String raw(String key, String fallback) {
        return repository.findById(key).map(Setting::getValue).orElse(fallback);
    }

    @Transactional(readOnly = true)
    public int duplicateMinutes() {
        try {
            return Integer.parseInt(raw("duplicate_minutes", "10"));
        } catch (NumberFormatException ex) {
            return 10;
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal tolerance() {
        try {
            return new BigDecimal(raw("tolerance", "0.05"));
        } catch (NumberFormatException ex) {
            return new BigDecimal("0.05");
        }
    }

    @Transactional(readOnly = true)
    public SettingsView view() {
        return new SettingsView(duplicateMinutes(), tolerance());
    }
}
