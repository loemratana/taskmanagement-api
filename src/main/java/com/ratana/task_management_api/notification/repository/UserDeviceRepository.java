package com.ratana.task_management_api.notification.repository;


import com.ratana.task_management_api.notification.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    List<UserDevice> findByUserIdAndActiveTrue(UUID userId);
    Optional<UserDevice> findByUserIdAndDeviceToken(UUID userId, String deviceToken);
    Optional<UserDevice> findByDeviceToken(String deviceToken);
    Optional<UserDevice> findByIdAndUserId(UUID id, UUID userId);
    /** Own transaction: called by the push sender, which runs outside one. */
    @Transactional
    @Modifying
    @Query("UPDATE UserDevice d SET d.active = false WHERE d.deviceToken = :token")
    int deactivateByToken(String token);

    @Modifying
    @Query("UPDATE UserDevice d SET d.active = false WHERE d.userId = :userId AND d.deviceToken = :token")
    int deactivateByUserAndToken(UUID userId, String token);
}
