package com.eduaccess.repository;

import com.eduaccess.domain.Screen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    List<Screen> findByCinemaIdOrderByScreenNumberAsc(Long cinemaId);

    Optional<Screen> findByCinemaIdAndScreenNumber(Long cinemaId, int screenNumber);
}