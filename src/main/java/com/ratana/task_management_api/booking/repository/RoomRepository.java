package com.ratana.task_management_api.booking.repository;

import com.ratana.task_management_api.booking.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {

    @Query("SELECT COUNT(r) > 0 FROM Room r WHERE lower(r.name) = lower(:name) AND r.id <> :excludeId")
    boolean existsByNameIgnoreCase(@Param("name") String name, @Param("excludeId") UUID excludeId);

    @Query("SELECT COUNT(r) > 0 FROM Room r WHERE lower(r.code) = lower(:code) AND r.id <> :excludeId")
    boolean existsByCodeIgnoreCase(@Param("code") String code, @Param("excludeId") UUID excludeId);
}
