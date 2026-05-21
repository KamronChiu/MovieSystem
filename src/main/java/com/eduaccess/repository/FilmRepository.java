package com.eduaccess.repository;

import com.eduaccess.domain.Film;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FilmRepository extends JpaRepository<Film, Long> {

    List<Film> findByTitleContainingIgnoreCaseOrderByTitleAsc(String title);

    List<Film> findByGenreIgnoreCaseOrderByTitleAsc(String genre);
}