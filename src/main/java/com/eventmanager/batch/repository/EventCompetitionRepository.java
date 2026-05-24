package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.EventCompetition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventCompetitionRepository extends JpaRepository<EventCompetition, Long> {}
