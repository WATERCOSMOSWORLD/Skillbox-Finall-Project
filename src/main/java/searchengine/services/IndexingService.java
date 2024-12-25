package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.config.Site;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IndexingService {

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final SitesList sitesList;

    public IndexingService(SitesList sitesList) {
        this.sitesList = sitesList;
    }

    public boolean isIndexing() {
        return isIndexing.get();
    }

    public void startIndexing() {
        if (isIndexing.compareAndSet(false, true)) {
            executorService.submit(() -> {
                try {
                    System.out.println("Начата индексация всех сайтов.");
                    performIndexing();
                    System.out.println("Индексация завершена.");
                } catch (Exception e) {
                    System.err.println("Ошибка при индексации: " + e.getMessage());
                } finally {
                    isIndexing.set(false);
                }
            });
        }
    }

    private void performIndexing() {
        // Получаем список сайтов из конфигурации
        for (Site site : sitesList.getSites()) {
            System.out.printf("Индексация сайта: %s (%s)%n", site.getName(), site.getUrl());
            // Здесь должна быть логика обработки каждого сайта
            try {
                Thread.sleep(1000); // Симуляция обработки сайта
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Индексация была прервана", e);
            }
        }
    }
}
