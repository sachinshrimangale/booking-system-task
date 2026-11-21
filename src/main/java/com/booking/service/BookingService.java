package com.booking.service;

import com.booking.entity.Booking;
import com.booking.entity.Slot;
import com.booking.exception.ConflictException;
import com.booking.exception.ForbiddenActionException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.model.BookingStatus;
import com.booking.model.SlotStatus;
import com.booking.repository.BookingRepository;
import com.booking.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;

    public BookingService(SlotRepository slotRepository, BookingRepository bookingRepository) {
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public Slot createSlot(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        Slot slot = new Slot(startTime, endTime, SlotStatus.AVAILABLE);
        return slotRepository.save(slot);
    }

    @Transactional(readOnly = true)
    public List<Slot> getSlots(SlotStatus status) {
        if (status != null) {
            return slotRepository.findByStatus(status);
        }
        return slotRepository.findAll();
    }

    @Transactional
    public Booking bookSlot(Long slotId, String userId) {
        Slot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with id: " + slotId));

        if (slot.getStatus() != SlotStatus.AVAILABLE ||
                bookingRepository.existsBySlotIdAndStatus(slotId, BookingStatus.ACTIVE)) {
            throw new ConflictException("Slot is already booked");
        }

        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);

        Booking booking = new Booking(slotId, userId, BookingStatus.ACTIVE, LocalDateTime.now());
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking cancelBookingAsUser(Long bookingId, String currentUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!booking.getUserId().equals(currentUserId)) {
            throw new ForbiddenActionException("You can only cancel your own booking");
        }

        return cancelBookingInternal(booking);
    }

    @Transactional
    public Booking cancelBookingAsAdmin(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        return cancelBookingInternal(booking);
    }

    private Booking cancelBookingInternal(Booking booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking is already cancelled");
        }

        Slot slot = slotRepository.findByIdForUpdate(booking.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Associated slot not found"));

        booking.setStatus(BookingStatus.CANCELLED);
        slot.setStatus(SlotStatus.AVAILABLE);

        slotRepository.save(slot);
        return bookingRepository.save(booking);
    }
}