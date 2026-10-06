package flightfinder_backend.controller;

import flightfinder_backend.model.Booking.Flight;
import flightfinder_backend.service.FlightService;
import flightfinder_backend.service.FlightService.City;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FlightController {

    private final FlightService flights;

    public FlightController(FlightService flights) {
        this.flights = flights;
    }

    @GetMapping("/health")
    public java.util.Map<String, String> health() {
        return java.util.Map.of("status", "ok");
    }

    @GetMapping("/cities")
    public List<City> cities() {
        return flights.cities();
    }

    // GET /api/flights/search?from=BOM&to=DEL&date=2026-10-20
    @GetMapping("/flights/search")
    public List<Flight> search(@RequestParam String from,
                               @RequestParam String to,
                               @RequestParam String date) {
        if (!flights.isCity(from) || !flights.isCity(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid from/to city code");
        if (from.equals(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origin and destination must be different");
        if (!flights.isValidDate(date))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date must be YYYY-MM-DD");
        return flights.search(from, to, date);
    }
}
