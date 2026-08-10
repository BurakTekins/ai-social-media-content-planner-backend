package com.globalcodelabs.socialmediaplanner.domain.repository;

import com.globalcodelabs.socialmediaplanner.domain.model.ApplicationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, String> {
}
