package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.policy.DomainValidation;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Locale;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public class AiModelSelection {

    private String provider;
    private String model;

    private AiModelSelection(String provider, String model, String capability) {
        this.provider = DomainValidation.requireText(provider, capability + " provider cannot be blank")
                .toLowerCase(Locale.ROOT);
        this.model = DomainValidation.requireText(model, capability + " model cannot be blank");
    }

    public static AiModelSelection required(String provider, String model, String capability) {
        return new AiModelSelection(provider, model, capability);
    }

    public static AiModelSelection optional(
            boolean included,
            String provider,
            String model,
            String capability
    ) {
        boolean providerPresent = provider != null && !provider.isBlank();
        boolean modelPresent = model != null && !model.isBlank();
        if (included && (!providerPresent || !modelPresent)) {
            throw new DomainException(capability + " provider and model are required");
        }
        if (!included && (providerPresent || modelPresent)) {
            throw new DomainException(capability + " provider and model must be empty when disabled");
        }
        return included ? new AiModelSelection(provider, model, capability) : null;
    }

}
