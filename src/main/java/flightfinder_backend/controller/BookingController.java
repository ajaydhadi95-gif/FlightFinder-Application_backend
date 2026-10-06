package flightfinder_backend.controller;

import flightfinder_backend.model.Booking;
import flightfinder_backend.model.Booking.BookingResponse;
import flightfinder_backend.model.Booking.Flight;
import flightfinder_backend.repository.BookingRepository;
import flightfinder_backend.service.FlightService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private static final Pattern FLIGHT_ID =
            Pattern.compile("^([A-Z]{3})([A-Z]{3})(\\d{4}-\\d{2}-\\d{2})(\\d+)$");

    public record BookingRequest(
            @NotBlank(message = "flightId is required") String flightId,
            @Min(value = 1, message = "passengers must be 1-9")
            @Max(value = 9, message = "passengers must be 1-9") int passengers,
            @NotBlank(message = "name is required") @Size(max = 100) String name,
            @NotBlank(message = "email is required") @Email(message = "valid email is required") String email,
            @jakarta.validation.constraints.Pattern(regexp = "\\d{10}", message = "phone must be 10 digits") String phone) {}

    private final BookingRepository bookings;
    private final FlightService flights;

    public BookingController(BookingRepository bookings, FlightService flights) {
        this.bookings = bookings;
        this.flights = flights;
    }

    // POST /api/bookings
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody BookingRequest req) {
        Matcher m = FLIGHT_ID.matcher(req.flightId());
        if (!m.matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid flightId");

        String from = m.group(1), to = m.group(2), date = m.group(3);
        if (!flights.isCity(from) || !flights.isCity(to) || !flights.isValidDate(date))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid flightId");

        // Never trust the price from the client: rebuild the flight on the server
        Flight flight = flights.search(from, to, date).stream()
                .filter(f -> f.id().equals(req.flightId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));

        String id = "FF" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        Booking saved = bookings.save(
                new Booking(id, flight, req.passengers(), req.name().trim(), req.email().trim(), req.phone()));
        return saved.toResponse();
    }

    // GET /api/bookings   or   GET /api/bookings?email=a@b.com
    @GetMapping
    public List<BookingResponse> list(@RequestParam(required = false) String email) {
        List<Booking> rows = (email == null || email.isBlank())
                ? bookings.findAllByOrderByCreatedAtDesc()
                : bookings.findByEmailIgnoreCaseOrderByCreatedAtDesc(email.trim());
        return rows.stream().map(Booking::toResponse).toList();
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable String id) {
        return bookings.findById(id)
                .map(Booking::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    @DeleteMapping("/{id}")
    public Map<String, String> cancel(@PathVariable String id) {
        if (!bookings.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        bookings.deleteById(id);
        return Map.of("message", "Booking cancelled", "id", id);
    }
}