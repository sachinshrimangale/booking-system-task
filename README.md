A backend Spring Boot REST API for slot booking and cancellation with role-based access control, database transactions, and race condition protection.

Roles and Permissions

USER: Can view slots, book available slots, and cancel their own bookings.

ADMIN: Can create slots and cancel any booking.

API Endpoints

POST /slots (ADMIN): Creates a new bookable slot with status AVAILABLE.

GET /slots (USER, ADMIN): Returns the list of available and booked slots.

POST /bookings (USER): Attempts to book an available slot; succeeds only if the slot is AVAILABLE.

POST /bookings/{id}/cancel (USER): Cancels the authenticated user's own booking and frees the slot back to AVAILABLE.

POST /admin/bookings/{id}/cancel (ADMIN): Cancels any active booking and frees the slot back to AVAILABLE.

Chosen Locking Strategy

Pessimistic database-level locking is used when loading/locking the slot during booking so concurrent transactions wait at the database level rather than racing in memory (no in-memory locks or synchronized blocks).

Because state is persisted in the H2 database with database locking, application restarts do not break correctness.

Transaction Boundaries

The booking and cancellation flows run inside transactional boundaries (@Transactional) so updating/creating the Booking record and updating the Slot status happen atomically.

If a conflict or failure occurs, no partial updates are committed, keeping the Slot and Booking tables consistent.

How Race Conditions Are Prevented

When multiple users send concurrent POST /bookings requests for the same slot, database-level locking serializes access to that slot across transactions.

The first transaction that acquires the lock and sees the slot as AVAILABLE creates an ACTIVE booking and updates the slot status to BOOKED before committing.

Subsequent concurrent transactions acquire the lock after commit, see that the slot is no longer AVAILABLE, and fail cleanly with a conflict response rather than creating a duplicate booking.

Example Concurrent Booking Scenario

A slot (id = 1) is created with status AVAILABLE.

User A and User B send concurrent POST /bookings requests for slotId = 1 at the same time.

User A's transaction locks the slot, books it (Booking status = ACTIVE, Slot status = BOOKED), and returns a success response.

User B's transaction sees the slot is already BOOKED and receives a failure/conflict response (409 Conflict). Only one booking exists for slot 1.

Steps to Run the Application

Ensure Java 21 and Maven are installed.

Open the project or navigate to the project root directory.

Run the application using:
mvn spring-boot:run

Run the test suite using:
mvn test