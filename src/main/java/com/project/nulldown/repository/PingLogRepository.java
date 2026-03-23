package com.project.nulldown.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.nulldown.model.PingLog;

public interface PingLogRepository extends JpaRepository<PingLog, UUID> {

    List<PingLog> findByMonitorIdOrderByCheckedAtDesc(UUID monitorId);

    @Query("SELECT p FROM PingLog p WHERE p.monitorId = :monitorId ORDER BY p.checkedAt DESC")
    List<PingLog> findTop100ByMonitorId(@Param("monitorId") UUID monitorId, Pageable pageable);

    List<PingLog> findByMonitorId(UUID monitorId); // add this

}
