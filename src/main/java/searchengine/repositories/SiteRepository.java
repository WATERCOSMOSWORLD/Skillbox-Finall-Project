package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;
import java.time.LocalDateTime;
import searchengine.model.Status;

public interface SiteRepository extends JpaRepository<Site, Integer> {

    // Method to delete a Site by its URL
    @Modifying
    @Transactional
    @Query("DELETE FROM Site s WHERE s.url = :url")
    void deleteByUrl(String url);

    // Method to count the number of Sites by URL (useful for tracking deletions)
    @Query("SELECT COUNT(s) FROM Site s WHERE s.url = :url")
    int countByUrl(String url);

    // Method to find a Site by URL
    Site findByUrl(String url);

    // Method to update the status of a Site
    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.status = :status, s.statusTime = :statusTime WHERE s.url = :url")
    void updateStatusByUrl(String url, Status status, LocalDateTime statusTime);
}
