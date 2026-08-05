package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiCredentialTest {

    @Test
    void requiresAccountIdentifierForLinkedinAndInstagram() {
        assertThatThrownBy(() -> create(CredentialType.SOCIAL_PLATFORM, "linkedin", null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> create(CredentialType.SOCIAL_PLATFORM, "instagram", " "))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void acceptsAndTrimsRequiredAccountIdentifier() {
        ApiCredential credential = create(
                CredentialType.SOCIAL_PLATFORM,
                " LinkedIn ",
                " urn:li:person:123 "
        );

        assertThat(credential.providerName()).isEqualTo("linkedin");
        assertThat(credential.accountIdentifier()).isEqualTo("urn:li:person:123");
    }

    @Test
    void allowsNullAccountIdentifierForOtherProviders() {
        ApiCredential socialCredential = create(CredentialType.SOCIAL_PLATFORM, "twitter", null);
        ApiCredential aiCredential = create(CredentialType.AI_PROVIDER, "openai", null);

        assertThat(socialCredential.accountIdentifier()).isNull();
        assertThat(aiCredential.accountIdentifier()).isNull();
    }

    @Test
    void rejectsBlankOptionalAccountIdentifier() {
        assertThatThrownBy(() -> create(CredentialType.SOCIAL_PLATFORM, "twitter", " "))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> create(CredentialType.AI_PROVIDER, "openai", " "))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void updatesAccountIdentifierUsingProviderRules() {
        ApiCredential linkedin = create(
                CredentialType.SOCIAL_PLATFORM,
                "linkedin",
                "urn:li:person:123"
        );
        linkedin.updateAccountIdentifier(" urn:li:organization:456 ");

        assertThat(linkedin.accountIdentifier()).isEqualTo("urn:li:organization:456");
        assertThatThrownBy(() -> linkedin.updateAccountIdentifier(null))
                .isInstanceOf(DomainException.class);
        assertThat(linkedin.accountIdentifier()).isEqualTo("urn:li:organization:456");

        ApiCredential twitter = create(CredentialType.SOCIAL_PLATFORM, "twitter", null);
        twitter.updateAccountIdentifier("account-123");
        assertThat(twitter.accountIdentifier()).isEqualTo("account-123");

        twitter.updateAccountIdentifier(null);
        assertThat(twitter.accountIdentifier()).isNull();
    }

    @Test
    void appliesProviderRequirementIndependentlyOfCredentialType() {
        assertThatThrownBy(() -> create(CredentialType.AI_PROVIDER, "linkedin", null))
                .isInstanceOf(DomainException.class);
    }

    private static ApiCredential create(
            CredentialType credentialType,
            String providerName,
            String accountIdentifier
    ) {
        return ApiCredential.create(
                credentialType,
                providerName,
                accountIdentifier,
                "encrypted-access-token",
                null,
                null
        );
    }
}
