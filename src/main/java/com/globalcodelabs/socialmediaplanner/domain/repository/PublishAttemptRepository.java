package com.globalcodelabs.socialmediaplanner.domain.repository;

import com.globalcodelabs.socialmediaplanner.domain.model.PublishAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PublishAttemptRepository extends JpaRepository<PublishAttempt, UUID> {

    List<PublishAttempt> findAllByContent_IdOrderByAttemptedAtDescIdDesc(UUID contentId);
}
