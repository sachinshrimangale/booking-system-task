package com.booking.controller;

import com.booking.dto.CreateSlotRequest;
import com.booking.entity.Slot;
import com.booking.model.SlotStatus;
import com.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SlotController {

    private final BookingService bookingService;

    public SlotController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/slots")
    public ResponseEntity<Slot> createSlot(@Valid @RequestBody CreateSlotRequest request) {
        Slot created = bookingService.createSlot(request.startTime(), request.endTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/slots")
    public ResponseEntity<List<Slot>> getSlots(@RequestParam(required = false) SlotStatus status) {
        return ResponseEntity.ok(bookingService.getSlots(status));
    }
}