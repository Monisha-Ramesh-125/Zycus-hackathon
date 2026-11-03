package com.example.slotmind.repository;

import com.example.slotmind.entity.Slot;
import com.example.slotmind.entity.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SlotRepository extends JpaRepository<Slot, String> {

    @Query("select s from Slot s join fetch s.provider " +
           "where (:status is null or s.status = :status) " +
           "and (:providerId is null or s.provider.id = :providerId) order by s.startTime")
    List<Slot> search(@Param("status") SlotStatus status, @Param("providerId") String providerId);

    @Query("select s from Slot s join fetch s.provider " +
           "where s.status = com.example.slotmind.entity.SlotStatus.OPEN and s.startTime > :after order by s.startTime")
    List<Slot> findOpenAfter(@Param("after") LocalDateTime after);
}
