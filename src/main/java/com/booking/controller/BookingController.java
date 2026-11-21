package com.booking.controller;

import com.booking.dto.CreateBookingRequest;
import com.booking.entity.Booking;
import com.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    public ResponseEntity<Booking> bookSlot(
            @Valid @RequestBody CreateBookingRequest request,
            Authentication authentication
    ) {
        Booking booking = bookingService.bookSlot(request.slotId(), authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<Booking> cancelOwnBooking(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Booking booking = bookingService.cancelBookingAsUser(id, authentication.getName());
        return ResponseEntity.ok(booking);
    }

    @PostMapping("/admin/bookings/{id}/cancel")
    public ResponseEntity<Booking> cancelAnyBooking(@PathVariable Long id) {
        Booking booking = bookingService.cancelBookingAsAdmin(id);
        return ResponseEntity.ok(booking);
    }
}
