package searchengine.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.model.Site;
import searchengine.model.Page;
import searchengine.model.Status;
import searchengine.repositories.SiteRepository;
import searchengine.repositories.PageRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.concurrent.atomic.AtomicBoolean;


import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

@Service
public class IndexingService {

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final ForkJoinPool forkJoinPool = new ForkJoinPool();

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
            System.out.println("[" + LocalDateTime.now() + "] Индексация началась...");
            executorService.submit(() -> {
                try {
                    performIndexing();
                } catch (Exception e) {
                    System.err.println("[" + LocalDateTime.now() + "] Ошибка при индексации: " + e.getMessage());
                } finally {
                    isIndexing.set(false);
                    System.out.println("[" + LocalDateTime.now() + "] Индексация завершена.");
                }
            });
        } else {
            System.out.println("[" + LocalDateTime.now() + "] Индексация уже в процессе.");
        }
    }

    @Transactional
    private void performIndexing() {
        for (searchengine.config.Site configSite : sitesList.getSites()) {
            try {
                Site site = convertToModelSite(configSite);

                int deletedPages = deletePages(site);
                int deletedSites = deleteSite(site);

                System.out.println("[" + LocalDateTime.now() + "] Для сайта " + site.getUrl() + " удалено " + deletedPages + " страниц и " + deletedSites + " записей о сайте.");

                site.setStatus(Status.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                System.out.println("[" + LocalDateTime.now() + "] Сайт с URL " + site.getUrl() + " начал индексацию.");

                updateSiteStatusTime(site);

                crawlSiteAndSavePages(site);

                System.out.println("[" + LocalDateTime.now() + "] Страницы для сайта " + site.getUrl() + " добавлены в базу данных.");

                site.setStatus(Status.INDEXED);
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                System.out.println("[" + LocalDateTime.now() + "] Статус сайта " + site.getUrl() + " изменен на INDEXED.");
            } catch (Exception e) {
                System.err.println("[" + LocalDateTime.now() + "] Ошибка при индексации сайта " + configSite.getUrl() + ": " + e.getMessage());

                // Обновление статуса на FAILED и сохранение ошибки
                Site site = convertToModelSite(configSite);
                site.setStatus(Status.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError("Ошибка при индексации: " + e.getMessage());
                siteRepository.save(site);

                e.printStackTrace();
            }
        }
    }

    private Site convertToModelSite(searchengine.config.Site configSite) {
        Site site = new Site();
        site.setUrl(configSite.getUrl());
        site.setName(configSite.getName());
        return site;
    }

    @Transactional
    private int deletePages(Site site) {
        int countBeforeDelete = pageRepository.countBySiteUrl(site.getUrl());
        pageRepository.deleteBySiteUrl(site.getUrl());
        return countBeforeDelete;
    }

    @Transactional
    private int deleteSite(Site site) {
        int countBeforeDelete = siteRepository.countByUrl(site.getUrl());
        siteRepository.deleteByUrl(site.getUrl());
        return countBeforeDelete;
    }

    @Transactional
    private void updateSiteStatusTime(Site site) {
        siteRepository.updateStatusByUrl(site.getUrl(), site.getStatus(), LocalDateTime.now());
    }

    private void crawlSiteAndSavePages(Site site) {
        Set<String> visitedUrls = new HashSet<>();
        crawlPage(site.getUrl(), visitedUrls, site);
    }

    private void crawlPage(String url, Set<String> visitedUrls, Site site) {
        if (visitedUrls.contains(url)) {
            return;
        }

        // Создаем новую задачу для обработки страницы
        forkJoinPool.submit(new RecursiveTask<Void>() {
            @Override
            protected Void compute() {
                try {
                    Document doc = Jsoup.connect(url).get();
                    visitedUrls.add(url);

                    String contentType = Jsoup.connect(url).execute().contentType();
                    if (contentType == null || contentType.startsWith("text/") || contentType.startsWith("application/xml") || contentType.startsWith("application/*+xml")) {
                        Page page = new Page();
                        page.setSite(site);
                        page.setPath(url);
                        page.setCode(200);
                        page.setContent(doc.html());
                        pageRepository.save(page);
                        System.out.println("[" + LocalDateTime.now() + "] Страница " + url + " сохранена.");
                    } else {
                        System.out.println("[" + LocalDateTime.now() + "] Пропущена страница " + url + " с неподдерживаемым типом содержимого.");
                    }

                    updateSiteStatusTime(site);

                    // Обрабатываем все ссылки на текущей странице
                    for (Element link : doc.select("a[href]")) {
                        String nextUrl = link.absUrl("href");
                        if (!nextUrl.isEmpty() && nextUrl.startsWith(site.getUrl())) {
                            // Для каждой найденной ссылки создаем новую задачу
                            crawlPage(nextUrl, visitedUrls, site);
                        }
                    }

                    // Обрабатываем медиафайлы (изображения, CSS, JS)
                    processMediaFiles(doc, site);
                } catch (IOException e) {
                    System.err.println("[" + LocalDateTime.now() + "] Ошибка при обработке страницы " + url + ": " + e.getMessage());
                }
                return null;
            }
        });
    }

    private void processMediaFiles(Document doc, Site site) {
        for (Element img : doc.select("img[src]")) {
            String imageUrl = img.absUrl("src");
            if (!imageUrl.isEmpty()) {
                saveMediaFile(imageUrl, site, "image");
            }
        }

        for (Element link : doc.select("link[href]")) {
            String cssUrl = link.absUrl("href");
            if (!cssUrl.isEmpty()) {
                saveMediaFile(cssUrl, site, "css");
            }
        }

        for (Element script : doc.select("script[src]")) {
            String jsUrl = script.absUrl("src");
            if (!jsUrl.isEmpty()) {
                saveMediaFile(jsUrl, site, "js");
            }
        }
    }

    private void saveMediaFile(String fileUrl, Site site, String fileType) {
        try {
            if (isValidMediaFile(fileUrl)) {
                Page mediaPage = new Page();
                mediaPage.setSite(site);
                mediaPage.setPath(fileUrl);
                mediaPage.setCode(200);
                mediaPage.setContent("");
                pageRepository.save(mediaPage);

                System.out.println("[" + LocalDateTime.now() + "] " + fileType.toUpperCase() + " файл " + fileUrl + " сохранен.");
            }
        } catch (Exception e) {
            System.err.println("[" + LocalDateTime.now() + "] Ошибка при сохранении " + fileType + " файла " + fileUrl + ": " + e.getMessage());
        }
    }

    private boolean isValidMediaFile(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            String fileType = url.openConnection().getContentType();
            return fileType != null && (fileType.startsWith("image/") || fileType.startsWith("text/") || fileType.startsWith("application/"));
        } catch (IOException e) {
            System.err.println("[" + LocalDateTime.now() + "] Ошибка при проверке типа медиафайла " + fileUrl + ": " + e.getMessage());
            return false;
        }
    }
}
