Booking System with Race Condition Protection

A backend Spring Boot REST API for slot booking and cancellation with role-based access control, database transactions, and race condition protection.

Roles and Permissions

USER: Can view available and booked slots, book available slots, and cancel their own bookings.

ADMIN: Can create bookable slots and cancel any active booking.

Domain Model and States

Slot: id, start_time, end_time, status (AVAILABLE, BOOKED).

Booking: id, slot_id, user_id, status (ACTIVE, CANCELLED), created_at.

API Endpoints

POST /slots (ADMIN): Creates a new bookable slot with initial status AVAILABLE.

GET /slots (USER, ADMIN): Returns the list of available and booked slots.

POST /bookings (USER): Attempts to book a slot; succeeds only if the slot is AVAILABLE.

POST /bookings/{id}/cancel (USER): Cancels the authenticated user's own booking and sets the slot status back to AVAILABLE.

POST /admin/bookings/{id}/cancel (ADMIN): Cancels any active booking and sets the slot status back to AVAILABLE.

Chosen Locking Strategy

Pessimistic database-level locking (PESSIMISTIC_WRITE) is used when loading/locking the slot during booking so concurrent transactions wait at the database level rather than racing in memory.

In-memory locks or synchronized blocks are not used, and because state and locks are backed by the H2 database, application restarts do not break correctness.

Transaction Boundaries

Booking and cancellation operations execute inside transactional boundaries (@Transactional) so creating/updating the Booking record and updating the Slot status happen atomically.

If a conflict or failure occurs, no partial updates are committed, keeping the Slot and Booking tables consistent.

How Race Conditions Are Prevented

When multiple users send concurrent POST /bookings requests for the same slot, database-level pessimistic locking serializes access to that slot across transactions.

The first transaction that acquires the lock and observes the slot as AVAILABLE creates an ACTIVE booking and marks the slot as BOOKED before committing.

Subsequent transactions acquire the lock after commit, see that the slot is no longer AVAILABLE, and fail cleanly with a conflict response (such as 409 Conflict) so only one booking can ever succeed for a slot.

Example Concurrent Booking Scenario

An ADMIN creates a slot (id = 1) with status AVAILABLE.

User A and User B send concurrent POST /bookings requests for slotId = 1 at the same time.

User A's transaction acquires the database lock on slot 1, creates an ACTIVE booking, sets slot 1 status to BOOKED, and succeeds.

User B's transaction sees slot 1 is already BOOKED and receives a failure/conflict response (409 Conflict), ensuring no double booking occurs.

Steps to Run the Application

Ensure Java 21 and Maven are installed.

Open the project in Spring Tools for Eclipse or navigate to the project root directory.

Run the application using:
mvn spring-boot:run

Run the test suite using:
mvn test