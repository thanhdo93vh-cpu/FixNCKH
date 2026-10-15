package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.Story;
import com.example.hauiTrash.entity.StoryCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface StoryRepository extends JpaRepository<Story, Long> {
    Optional<Story> findBySlug(String slug);

    @Query("SELECT s FROM Story s WHERE s.isPublished = true ORDER BY s.publishedAt DESC")
    List<Story> findAllPublished(Pageable pageable);

    @Query("SELECT s FROM Story s WHERE s.isPublished = true AND s.category = :category ORDER BY s.publishedAt DESC")
    List<Story> findByCategory(@Param("category") StoryCategory category, Pageable pageable);
    // StoryRepository.java

//    @Query("SELECT s FROM Story s ORDER BY s.viewCount DESC")
//    List<Story> findMostViewed(Pageable pageable);
}