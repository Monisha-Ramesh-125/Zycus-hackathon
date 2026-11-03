package com.example.slotmind.repository;

import com.example.slotmind.entity.RescheduleSuggestion;
import com.example.slotmind.entity.SuggestionStatus;
import com.example.slotmind.entity.TriggerReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SuggestionRepository extends JpaRepository<RescheduleSuggestion, String> {

    @Query("select s from RescheduleSuggestion s join fetch s.appointment join fetch s.recommendedSlot rs " +
           "join fetch rs.provider left join fetch s.alternateProvider " +
           "where (:status is null or s.status = :status) order by s.createdAt desc")
    List<RescheduleSuggestion> search(@Param("status") SuggestionStatus status);

    Optional<RescheduleSuggestion> findFirstByAppointmentIdAndTriggerReasonAndStatus(
            String appointmentId, TriggerReason triggerReason, SuggestionStatus status);

    @Query("select s from RescheduleSuggestion s join fetch s.appointment join fetch s.recommendedSlot rs " +
           "join fetch rs.provider left join fetch s.alternateProvider " +
           "where s.appointment.id in :appointmentIds " +
           "and s.status = com.example.slotmind.entity.SuggestionStatus.PENDING order by s.createdAt desc")
    List<RescheduleSuggestion> findPendingFor(@Param("appointmentIds") Collection<String> appointmentIds);

    /** Slots a person already declined for this appointment (system-closed suggestions do not count). */
    @Query("select s.recommendedSlot.id from RescheduleSuggestion s where s.appointment.id = :appointmentId " +
           "and s.status = com.example.slotmind.entity.SuggestionStatus.REJECTED " +
           "and (s.systemClosed is null or s.systemClosed = false)")
    List<String> findRejectedSlotIds(@Param("appointmentId") String appointmentId);

    List<RescheduleSuggestion> findByAppointmentIdAndStatus(String appointmentId, SuggestionStatus status);
}
