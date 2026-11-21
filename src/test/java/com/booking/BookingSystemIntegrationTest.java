package com.booking;

import com.booking.entity.Booking;
import com.booking.entity.Slot;
import com.booking.model.BookingStatus;
import com.booking.model.SlotStatus;
import com.booking.repository.BookingRepository;
import com.booking.repository.SlotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingSystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @BeforeEach
    void cleanDb() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
    }

    @Test
    void adminCanCreateSlot_andUserCannot() throws Exception {
        String body = """
                {
                  "startTime": "2026-12-01T10:00:00",
                  "endTime": "2026-12-01T11:00:00"
                }
                """;

        mockMvc.perform(post("/slots")
                        .with(httpBasic("user1", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/slots")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void getSlots_requiresAuth_andWorksForUserAndAdmin() throws Exception {
        slotRepository.save(new Slot(
                LocalDateTime.of(2026, 12, 1, 9, 0),
                LocalDateTime.of(2026, 12, 1, 10, 0),
                SlotStatus.AVAILABLE
        ));

        mockMvc.perform(get("/slots"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/slots")
                        .with(httpBasic("user1", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/slots")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void bookSlot_andPreventDuplicateBooking() throws Exception {
        Slot slot = slotRepository.save(new Slot(
                LocalDateTime.of(2026, 12, 1, 14, 0),
                LocalDateTime.of(2026, 12, 1, 15, 0),
                SlotStatus.AVAILABLE
        ));

        String requestBody = "{\"slotId\": " + slot.getId() + "}";

        mockMvc.perform(post("/bookings")
                        .with(httpBasic("user1", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/bookings")
                        .with(httpBasic("user2", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    void userCanCancelOwnBooking_butNotOthersBooking() throws Exception {
        Slot slot = slotRepository.save(new Slot(
                LocalDateTime.of(2026, 12, 2, 10, 0),
                LocalDateTime.of(2026, 12, 2, 11, 0),
                SlotStatus.AVAILABLE
        ));

        String response = mockMvc.perform(post("/bookings")
                        .with(httpBasic("user1", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\": " + slot.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Booking created = objectMapper.readValue(response, Booking.class);

        mockMvc.perform(post("/bookings/" + created.getId() + "/cancel")
                        .with(httpBasic("user2", "password123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/bookings/" + created.getId() + "/cancel")
                        .with(httpBasic("user1", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Slot updatedSlot = slotRepository.findById(slot.getId()).orElseThrow();
        assertThat(updatedSlot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void adminCanCancelAnyBooking() throws Exception {
        Slot slot = slotRepository.save(new Slot(
                LocalDateTime.of(2026, 12, 3, 10, 0),
                LocalDateTime.of(2026, 12, 3, 11, 0),
                SlotStatus.AVAILABLE
        ));

        String response = mockMvc.perform(post("/bookings")
                        .with(httpBasic("user1", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\": " + slot.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Booking created = objectMapper.readValue(response, Booking.class);

        mockMvc.perform(post("/admin/bookings/" + created.getId() + "/cancel")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void concurrentBookingRequests_onlyOneSucceeds() throws Exception {
        Slot slot = slotRepository.save(new Slot(
                LocalDateTime.of(2026, 12, 5, 16, 0),
                LocalDateTime.of(2026, 12, 5, 17, 0),
                SlotStatus.AVAILABLE
        ));

        int threads = 8;
        var pool = Executors.newFixedThreadPool(threads);
        var startGate = new CountDownLatch(1);
        var tasks = new ArrayList<Callable<Integer>>();

        for (int i = 0; i < threads; i++) {
            String username = (i % 2 == 0) ? "user1" : "user2";
            tasks.add(() -> {
                startGate.await();
                return mockMvc.perform(post("/bookings")
                                .with(httpBasic(username, "password123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"slotId\": " + slot.getId() + "}"))
                        .andReturn().getResponse().getStatus();
            });
        }

        var futures = new ArrayList<Future<Integer>>();
        for (var task : tasks) {
            futures.add(pool.submit(task));
        }

        startGate.countDown();

        int successCount = 0;
        int conflictCount = 0;
        for (var future : futures) {
            int code = future.get(10, TimeUnit.SECONDS);
            if (code == 201) {
                successCount++;
            } else if (code == 409) {
                conflictCount++;
            }
        }
        pool.shutdown();

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(threads - 1);
        assertThat(bookingRepository.findBySlotId(slot.getId()))
                .filteredOn(b -> b.getStatus() == BookingStatus.ACTIVE)
                .hasSize(1);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);
    }
}