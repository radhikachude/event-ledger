package com.eventledger.gateway.repository;

import com.eventledger.gateway.domain.GatewayEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GatewayEventRepository extends JpaRepository<GatewayEvent, String> {
    List<GatewayEvent> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
