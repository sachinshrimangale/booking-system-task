package com.booking.repository;

import com.booking.entity.Booking;
import com.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsBySlotIdAndStatus(Long slotId, BookingStatus status);

    List<Booking> findBySlotId(Long slotId);
}