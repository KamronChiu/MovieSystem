package com.eduaccess.repository;

import com.eduaccess.domain.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CinemaRepository extends JpaRepository<Cinema, Long> {

    List<Cinema> findByCityIgnoreCaseOrderByNameAsc(String city);

    Optional<Cinema> findByNameIgnoreCase(String name);

    Optional<Cinema> findByCityIgnoreCaseAndNameIgnoreCase(String city, String name);

    boolean existsByCityIgnoreCaseAndNameIgnoreCase(String city, String name);
}
