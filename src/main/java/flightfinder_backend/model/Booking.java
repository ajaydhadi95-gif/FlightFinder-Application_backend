package flightfinder_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", indexes = @Index(name = "idx_email", columnList = "email"))
public class Booking {

    @Id
    @Column(length = 12)
    private String id;

    private String flightId;
    private String airline;
    private String flightNumber;
    private String origin;
    private String destination;
    private LocalDate travelDate;
    private String departure;
    private String arrival;
    private int durationMin;
    private int stops;
    private int price;
    private int passengers;
    private int total;
    private String name;
    private String email;
    private String phone;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    protected Booking() {} // required by JPA

    public Booking(String id, Flight f, int passengers, String name, String email, String phone) {
        this.id = id;
        this.flightId = f.id();
        this.airline = f.airline();
        this.flightNumber = f.number();
        this.origin = f.from();
        this.destination = f.to();
        this.travelDate = LocalDate.parse(f.date());
        this.departure = f.departure();
        this.arrival = f.arrival();
        this.durationMin = f.duration();
        this.stops = f.stops();
        this.price = f.price();
        this.passengers = passengers;
        this.total = f.price() * passengers;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Same JSON shape the React frontend already uses. */
    public BookingResponse toResponse() {
        Flight flight = new Flight(flightId, airline, flightNumber, origin, destination,
                travelDate.toString(), departure, arrival, durationMin, stops, price);
        return new BookingResponse(id, flight, passengers, total, name, email, phone, createdAt);
    }

    public record Flight(String id, String airline, String number, String from, String to,
                         String date, String departure, String arrival,
                         int duration, int stops, int price) {}

    public record BookingResponse(String id, Flight flight, int passengers, int total,
                                  String name, String email, String phone,
                                  LocalDateTime bookedAt) {}
}
