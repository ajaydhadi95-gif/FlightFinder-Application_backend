package flightfinder_backend.repository;

import flightfinder_backend.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, String> {
    List<Booking> findAllByOrderByCreatedAtDesc();

    List<Booking> findByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
