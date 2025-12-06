package org.example.repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing Product entities.
 * Provides CRUD operations and custom query methods.
 */
public interface ProductRepository {
    
    /**
     * Retrieves a product by its unique identifier.
     * @param id the product ID
     * @return an Optional containing the product if found
     */
    Optional<Product> findById(Long id);
    
    /**
     * Retrieves all products from the repository.
     * @return a list of all products
     */
    List<Product> findAll();
    
    /**
     * Finds all products in a specific category.
     * @param category the category name
     * @return a list of products in the specified category
     */
    List<Product> findByCategory(String category);
    
    /**
     * Saves a product entity.
     * @param product the product to save
     * @return the saved product with generated ID if new
     */
    Product save(Product product);
    
    /**
     * Deletes a product by its ID.
     * @param id the product ID to delete
     */
    void deleteById(Long id);
    
    /**
     * Checks if a product exists with the given ID.
     * @param id the product ID to check
     * @return true if the product exists, false otherwise
     */
    boolean existsById(Long id);
    
    /**
     * Returns the total number of products.
     * @return the count of all products
     */
    long count();
    
    /**
     * Finds products within a specified price range.
     * @param minPrice the minimum price (inclusive)
     * @param maxPrice the maximum price (inclusive)
     * @return a list of products within the price range
     */
    List<Product> findByPriceRange(double minPrice, double maxPrice);
}