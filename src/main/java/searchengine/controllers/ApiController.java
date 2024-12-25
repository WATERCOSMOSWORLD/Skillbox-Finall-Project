package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.services.IndexingService;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    // Получение статистики
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        // Возвращаем статистику через StatisticsService
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    // Запуск индексации
    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        Map<String, Object> response = new HashMap<>();

        // Проверка, не запущен ли процесс индексации
        if (indexingService.isIndexing()) {
            response.put("result", false);
            response.put("error", "Индексация уже запущена");
            return ResponseEntity.badRequest().body(response);
        }

        // Запуск индексации
        indexingService.startIndexing();
        response.put("result", true);
        response.put("message", "Индексация успешно запущена.");
        return ResponseEntity.ok(response);
    }
}
