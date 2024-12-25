package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;

public interface PageRepository extends JpaRepository<Page, Integer> {

    // Method to delete Pages by Site URL
    @Modifying
    @Transactional
    @Query("DELETE FROM Page p WHERE p.site.url = :siteUrl")
    void deleteBySiteUrl(String siteUrl);

    // Method to count the number of Pages by Site URL (useful for tracking deletions)
    @Query("SELECT COUNT(p) FROM Page p WHERE p.site.url = :siteUrl")
    int countBySiteUrl(String siteUrl);
}
