package com.nongxinle.ai.metric;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 加载 {@code classpath:ai-metrics/catalog-v1.yaml}，提供 metric_id → {@link MetricDefinition} 查询。
 */
@Slf4j
@Component
public class AiMetricCatalog {

    @Getter
    private String loadedVersion = "";

    private Map<String, MetricDefinition> byId = Map.of();

    public Optional<MetricDefinition> find(String metricId) {
        if (metricId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(metricId.trim()));
    }

    public Map<String, MetricDefinition> all() {
        return Collections.unmodifiableMap(byId);
    }

    @PostConstruct
    public void load() {
        ClassPathResource res = new ClassPathResource("ai-metrics/catalog-v1.yaml");
        if (!res.exists()) {
            log.warn("[AI-METRIC] catalog missing: ai-metrics/catalog-v1.yaml");
            return;
        }
        try (InputStream in = res.getInputStream()) {
            Yaml yaml = new Yaml();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                return;
            }
            loadedVersion = root.get("version") != null ? root.get("version").toString() : "unknown";
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> metricsRaw = (List<Map<String, Object>>) root.get("metrics");
            if (metricsRaw == null) {
                byId = Map.of();
                return;
            }
            Map<String, MetricDefinition> map = new LinkedHashMap<>();
            for (Map<String, Object> row : metricsRaw) {
                MetricDefinition def = MetricDefinition.fromYamlMap(loadedVersion, row);
                if (def == null || def.getMetricId() == null || def.getMetricId().isBlank()) {
                    continue;
                }
                map.put(def.getMetricId(), def);
            }
            byId = Collections.unmodifiableMap(map);
            log.info("[AI-METRIC] catalog loaded version={} metrics={}", loadedVersion, byId.size());
        } catch (Exception e) {
            log.error("[AI-METRIC] catalog load failed: {}", e.getMessage(), e);
            byId = Map.of();
        }
    }
}
