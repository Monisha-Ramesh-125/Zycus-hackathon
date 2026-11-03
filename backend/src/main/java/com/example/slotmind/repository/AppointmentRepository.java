package com.example.slotmind.repository;

import com.example.slotmind.entity.Appointment;
import com.example.slotmind.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, String> {

    @Query("select a from Appointment a join fetch a.provider join fetch a.slot s join fetch s.provider " +
           "where (:status is null or a.status = :status) order by s.startTime")
    List<Appointment> search(@Param("status") AppointmentStatus status);

    @Query("select a.id from Appointment a where a.provider.id = :providerId and a.status in :statuses")
    List<String> findIdsByProvider(@Param("providerId") String providerId,
                                   @Param("statuses") Collection<AppointmentStatus> statuses);

    @Query("select a.id from Appointment a where a.slot.id = :slotId and a.status in :statuses")
    List<String> findIdsBySlot(@Param("slotId") String slotId,
                               @Param("statuses") Collection<AppointmentStatus> statuses);
}
