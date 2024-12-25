package searchengine.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.SiteRepository;
import searchengine.repositories.PageRepository;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IndexingService {

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    public IndexingService(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    public boolean isIndexing() {
        return isIndexing.get();
    }

    public void startIndexing() {
        if (isIndexing.compareAndSet(false, true)) {
            System.out.println("Индексация началась...");

            executorService.submit(() -> {
                try {
                    performIndexing();
                } catch (Exception e) {
                    System.err.println("Ошибка при индексации: " + e.getMessage());
                } finally {
                    isIndexing.set(false);
                    System.out.println("Индексация завершена.");
                }
            });
        } else {
            System.out.println("Индексация уже в процессе.");
        }
    }

    @Transactional
    private void performIndexing() {
        for (searchengine.config.Site configSite : sitesList.getSites()) {
            try {
                Site site = convertToModelSite(configSite);

                Site existingSite = siteRepository.findByUrl(site.getUrl());
                if (existingSite != null) {
                    updateSiteStatusToIndexing(existingSite);
                } else {
                    createNewSiteWithIndexingStatus(site);
                }

                System.out.println("Начинаем удаление данных для сайта: " + site.getUrl());
                deleteSiteData(site);
                simulateSiteProcessing();
                System.out.println("Данные для сайта " + site.getUrl() + " удалены.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Индексация была прервана", e);
            }
        }
    }

    private Site convertToModelSite(searchengine.config.Site configSite) {
        Site site = new Site();
        site.setUrl(configSite.getUrl());
        site.setName(configSite.getName());
        return site;
    }

    private void createNewSiteWithIndexingStatus(Site site) {
        Site newSite = new Site();
        newSite.setUrl(site.getUrl());
        newSite.setName(site.getName());
        newSite.setStatus(Status.INDEXING);
        newSite.setStatusTime(LocalDateTime.now());
        siteRepository.save(newSite);

        System.out.println("Новый сайт с URL " + site.getUrl() + " создан со статусом INDEXING.");
    }

    private void updateSiteStatusToIndexing(Site site) {
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        System.out.println("Статус сайта " + site.getUrl() + " обновлен на INDEXING.");
    }

    private void deleteSiteData(Site site) {
        System.out.println("Удаляем страницы для сайта: " + site.getUrl());
        pageRepository.deleteBySiteUrl(site.getUrl());
        System.out.println("Удаляем сайт по URL: " + site.getUrl());
        siteRepository.deleteByUrl(site.getUrl());
    }

    private void simulateSiteProcessing() throws InterruptedException {
        System.out.println("Симуляция обработки сайта...");
        Thread.sleep(1000);
    }
}
