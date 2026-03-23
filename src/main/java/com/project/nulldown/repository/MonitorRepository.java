package com.project.nulldown.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.project.nulldown.model.Monitor;

public interface MonitorRepository extends JpaRepository<Monitor, UUID> {
    
    List<Monitor> findAllByActiveTrue();

    List<Monitor> findAllByUserId(UUID userId); 
}
