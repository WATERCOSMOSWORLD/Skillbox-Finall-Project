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

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
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

                // Удаляем старые данные и получаем количество удаленных записей
                int deletedPages = deletePages(site);
                int deletedSites = deleteSite(site);

                // Логируем количество удаленных данных
                System.out.println("Для сайта " + site.getUrl() + " удалено " + deletedPages + " страниц и " + deletedSites + " записей о сайте.");

                site.setStatus(Status.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                System.out.println("Сайт с URL " + site.getUrl() + " начал индексацию.");

                crawlSiteAndSavePages(site);

                System.out.println("Страницы для сайта " + site.getUrl() + " добавлены в базу данных.");
            } catch (Exception e) {
                System.err.println("Ошибка при индексации сайта " + configSite.getUrl() + ": " + e.getMessage());
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
        // Get the count of Pages before deletion
        int countBeforeDelete = pageRepository.countBySiteUrl(site.getUrl());

        // Perform the deletion
        pageRepository.deleteBySiteUrl(site.getUrl());

        // Return the count of deleted Pages
        return countBeforeDelete;
    }

    @Transactional
    private int deleteSite(Site site) {
        // Get the count of Sites before deletion
        int countBeforeDelete = siteRepository.countByUrl(site.getUrl());

        // Perform the deletion
        siteRepository.deleteByUrl(site.getUrl());

        // Return the count of deleted Sites
        return countBeforeDelete;
    }


    private void crawlSiteAndSavePages(Site site) {
        Set<String> visitedUrls = new HashSet<>();
        crawlPage(site.getUrl(), visitedUrls, site);
    }

    private void crawlPage(String url, Set<String> visitedUrls, Site site) {
        if (visitedUrls.contains(url)) {
            return;
        }

        try {
            Document doc = Jsoup.connect(url).get();
            visitedUrls.add(url);

            Page page = new Page();
            page.setSite(site);
            page.setPath(url);
            page.setCode(200);
            page.setContent(doc.html());
            pageRepository.save(page);

            System.out.println("Страница " + url + " сохранена.");

            for (Element link : doc.select("a[href]")) {
                String nextUrl = link.absUrl("href");
                if (!nextUrl.isEmpty() && nextUrl.startsWith(site.getUrl())) {
                    crawlPage(nextUrl, visitedUrls, site);
                }
            }

            processMediaFiles(doc, site);

        } catch (IOException e) {
            System.err.println("Ошибка при обработке страницы " + url + ": " + e.getMessage());
        }
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
                mediaPage.setContent(""); // Пустое содержимое для медиафайлов
                pageRepository.save(mediaPage);

                System.out.println(fileType.toUpperCase() + " файл " + fileUrl + " сохранен.");
            }
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении " + fileType + " файла " + fileUrl + ": " + e.getMessage());
        }
    }

    private boolean isValidMediaFile(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            String fileType = url.openConnection().getContentType();
            return fileType != null && (fileType.startsWith("image/") || fileType.startsWith("text/") || fileType.startsWith("application/"));
        } catch (IOException e) {
            System.err.println("Ошибка при проверке типа медиафайла " + fileUrl + ": " + e.getMessage());
            return false;
        }
    }
}
