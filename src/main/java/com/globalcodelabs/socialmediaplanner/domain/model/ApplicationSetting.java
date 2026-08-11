package com.globalcodelabs.socialmediaplanner.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

@Entity
@Table(name = "application_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Accessors(fluent = true)
public class ApplicationSetting {

    @Id
    @Column(name = "setting_key")
    @Getter
    private String key;

    @Column(name = "setting_value", nullable = false)
    @Getter
    private String value;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private ApplicationSetting(String key, String value) {
        this.key = requireValue(key, "Setting key cannot be blank");
        this.value = requireValue(value, "Setting value cannot be blank");
        this.updatedAt = OffsetDateTime.now();
    }

    public static ApplicationSetting create(String key, String value) {
        return new ApplicationSetting(key, value);
    }

    public void update(String value) {
        this.value = requireValue(value, "Setting value cannot be blank");
        this.updatedAt = OffsetDateTime.now();
    }

    private static String requireValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
