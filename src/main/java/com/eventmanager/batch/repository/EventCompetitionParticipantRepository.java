package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.EventCompetitionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventCompetitionParticipantRepository extends JpaRepository<EventCompetitionParticipant, Long> {}
