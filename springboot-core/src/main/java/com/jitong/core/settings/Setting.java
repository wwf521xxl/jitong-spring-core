package com.jitong.core.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "settings")
@Getter
@Setter
@NoArgsConstructor
public class Setting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String value;
}
