package com.eduaccess.repository;

import com.eduaccess.domain.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    List<FoodItem> findByActiveTrueOrderByCategoryAscNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
