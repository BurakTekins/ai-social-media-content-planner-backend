package com.globalcodelabs.socialmediaplanner.domain.repository;

import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiCredentialRepository extends JpaRepository<ApiCredential, UUID> {

    boolean existsByCredentialTypeAndProviderName(CredentialType credentialType, String providerName);

    Optional<ApiCredential> findByCredentialTypeAndProviderName(
            CredentialType credentialType,
            String providerName
    );

    Optional<ApiCredential> findByCredentialTypeAndProviderNameAndActiveTrue(
            CredentialType credentialType,
            String providerName
    );

    List<ApiCredential> findAllByOrderByCredentialTypeAscProviderNameAsc();

    List<ApiCredential> findAllByCredentialTypeAndActiveTrue(CredentialType credentialType);
}
