package com.novahub.monitor.service;

import com.novahub.common.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityStatsService {

    private final RedisUtils redisUtils;
    private final PvUvService pvUvService;

    private static final String DAU_KEY_PREFIX = "dau:";
    private static final String WAU_KEY_PREFIX = "wau:";
    private static final String MAU_KEY_PREFIX = "mau:";
    private static final String PUBLISH_COUNT_KEY = "stats:publish:count";
    private static final String FOLLOW_COUNT_KEY = "stats:follow:count";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public void incrPublishCount() {
        String today = LocalDate.now().format(DATE_FMT);
        redisUtils.hincr("stats:publish:daily", today, 1);
    }

    public void incrFollowCount() {
        String today = LocalDate.now().format(DATE_FMT);
        redisUtils.hincr("stats:follow:daily", today, 1);
    }

    public long getDau() {
        String today = LocalDate.now().format(DATE_FMT);
        String key = DAU_KEY_PREFIX + today;
        Long count = redisUtils.pfCount(key);
        return count != null ? count : 0L;
    }

    public long getWau() {
        LocalDate today = LocalDate.now();
        long total = 0;
        for (int i = 0; i < 7; i++) {
            String date = today.minusDays(i).format(DATE_FMT);
            total += getDauByDate(date);
        }
        return total;
    }

    public long getMau() {
        LocalDate today = LocalDate.now();
        long total = 0;
        for (int i = 0; i < 30; i++) {
            String date = today.minusDays(i).format(DATE_FMT);
            total += getDauByDate(date);
        }
        return total;
    }

    private long getDauByDate(String date) {
        String key = DAU_KEY_PREFIX + date;
        Long count = redisUtils.pfCount(key);
        return count != null ? count : 0L;
    }

    public Map<String, Object> getActivityOverview() {
        String today = LocalDate.now().format(DATE_FMT);
        Map<String, Object> result = new HashMap<>();
        result.put("dau", getDau());
        result.put("wau", getWau());
        result.put("mau", getMau());
        result.put("pv", pvUvService.getPv(today));
        result.put("uv", pvUvService.getUv(today));
        return result;
    }

    public Map<String, Long> getPublishTrend(int days) {
        Map<String, Long> trend = new HashMap<>();
        Map<Object, Object> daily = redisUtils.hGetAll("stats:publish:daily");
        if (daily != null) {
            for (Map.Entry<Object, Object> entry : daily.entrySet()) {
                trend.put(entry.getKey().toString(), Long.parseLong(entry.getValue().toString()));
            }
        }
        return trend;
    }
}
