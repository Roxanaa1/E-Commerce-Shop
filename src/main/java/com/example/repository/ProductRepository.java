package com.example.repository;

import com.example.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository  extends JpaRepository<Product,Integer>
{
    List<Product> findByCategoryNameIgnoreCase(String categoryName);

    List<Product> findByNameContainingIgnoreCase(String query);
}
