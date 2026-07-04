package com.eventmanager.batch.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SequenceSynchronizationServiceTest {

    private SequenceSynchronizationService service;

    @BeforeEach
    void setUp() {
        service = new SequenceSynchronizationService();
    }

    @Test
    void shouldSkipSpringBatchFrameworkSequences() {
        assertThat(service.shouldSkipSequenceSync("batch_job_seq", null)).isTrue();
        assertThat(service.shouldSkipSequenceSync("batch_job_execution_seq", null)).isTrue();
        assertThat(service.shouldSkipSequenceSync("batch_step_execution_seq", null)).isTrue();
        assertThat(service.shouldSkipSequenceSync("batch_job_execution_log_id_seq", null)).isTrue();
    }

    @Test
    void shouldSkipJoinTableSequencesAndTables() {
        assertThat(service.shouldSkipSequenceSync("rel_event_details__discount_codes_id_seq", "rel_event_details__discount_codes"))
            .isTrue();
    }

    @Test
    void shouldAllowUserApplicationTableSequences() {
        assertThat(service.shouldSkipSequenceSync("promotion_email_sent_log_id_seq", "promotion_email_sent_log")).isFalse();
        assertThat(service.shouldSkipSequenceSync("tenant_email_addresses_id_seq", "tenant_email_addresses")).isFalse();
    }
}
