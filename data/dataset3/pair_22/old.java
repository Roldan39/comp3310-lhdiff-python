package org.example.repository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    
    Optional<Product> findById(Long id);
    
    List<Product> findAll();
    
    List<Product> findByCategory(String category);
    
    Product save(Product product);
    
    void deleteById(Long id);
    
    boolean existsById(Long id);
    
    long count();
    
    List<Product> findByPriceRange(double minPrice, double maxPrice);
}