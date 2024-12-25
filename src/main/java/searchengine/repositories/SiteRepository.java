package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;
import java.time.LocalDateTime;
import searchengine.model.Status;

public interface SiteRepository extends JpaRepository<Site, Integer> {

    @Modifying
    @Transactional
    @Query("DELETE FROM Site s WHERE s.url = :url")
    void deleteByUrl(String url);

    Site findByUrl(String url);

    // Обновление статуса сайта
    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.status = :status, s.statusTime = :statusTime WHERE s.url = :url")
    void updateStatusByUrl(String url, Status status, LocalDateTime statusTime);
}
