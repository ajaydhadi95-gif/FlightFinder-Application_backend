package flightfinder_backend.service;

import flightfinder_backend.model.Booking.Flight;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class FlightService {

    public record City(String code, String name) {}

    private static final List<City> CITIES = List.of(
            new City("BOM", "Mumbai"),
            new City("DEL", "Delhi"),
            new City("BLR", "Bengaluru"),
            new City("HYD", "Hyderabad"),
            new City("MAA", "Chennai"),
            new City("CCU", "Kolkata"),
            new City("GOI", "Goa"),
            new City("PNQ", "Pune"));

    private static final String[] AIRLINES = {"IndiGo", "Air India", "Vistara", "SpiceJet", "Akasa Air"};

    public List<City> cities() {
        return CITIES;
    }

    public boolean isCity(String code) {
        return CITIES.stream().anyMatch(c -> c.code().equals(code));
    }

    public boolean isValidDate(String date) {
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeParseException | NullPointerException e) {
            return false;
        }
    }

    /** Deterministic mock flights: the same search always returns the same list. */
    public List<Flight> search(String from, String to, String date) {
        long seed = 0;
        for (char c : (from + to + date).toCharArray()) seed += c;
        long[] state = {seed};

        List<Flight> flights = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String airline = AIRLINES[(int) Math.floor(rand(state) * AIRLINES.length)];
            int depH = 5 + (int) Math.floor(rand(state) * 17);
            int depM = (int) Math.floor(rand(state) * 4) * 15;
            int duration = 60 + (int) Math.floor(rand(state) * 15) * 10;
            int arrival = depH * 60 + depM + duration;
            int stops = rand(state) > 0.75 ? 1 : 0;
            String number = airline.substring(0, 2).toUpperCase() + "-"
                    + (100 + (int) Math.floor(rand(state) * 900));
            int price = 2500 + (int) Math.floor(rand(state) * 9000) - (stops == 1 ? 500 : 0);

            flights.add(new Flight(from + to + date + i, airline, number, from, to, date,
                    fmt(depH * 60 + depM), fmt(arrival), duration + stops * 90, stops, price));
        }
        return flights;
    }

    private static double rand(long[] state) {
        state[0] = (state[0] * 9301 + 49297) % 233280;
        return state[0] / 233280.0;
    }

    private static String fmt(int minutes) {
        return String.format("%02d:%02d", (minutes / 60) % 24, minutes % 60);
    }
}
