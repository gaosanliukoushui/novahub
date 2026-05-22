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
public class PvUvService {

    private final RedisUtils redisUtils;

    private static final String PV_KEY_PREFIX = "pv:";
    private static final String UV_KEY_PREFIX = "uv:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public long getPv(String date) {
        String key = PV_KEY_PREFIX + date;
        String val = redisUtils.get(key);
        return val != null ? Long.parseLong(val) : 0L;
    }

    public long getUv(String date) {
        String key = UV_KEY_PREFIX + date;
        Long count = redisUtils.pfCount(key);
        return count != null ? count : 0L;
    }

    public Map<String, Object> getPvUv(String date) {
        Map<String, Object> result = new HashMap<>();
        result.put("date", date);
        result.put("pv", getPv(date));
        result.put("uv", getUv(date));
        return result;
    }

    public Map<String, Long> getPvTrend(int days) {
        Map<String, Long> trend = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            String date = today.minusDays(i).format(DATE_FMT);
            trend.put(date, getPv(date));
        }
        return trend;
    }

    public Map<String, Long> getUvTrend(int days) {
        Map<String, Long> trend = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            String date = today.minusDays(i).format(DATE_FMT);
            trend.put(date, getUv(date));
        }
        return trend;
    }

    public void recordDailyStats() {
        String yesterday = LocalDate.now().minusDays(1).format(DATE_FMT);
        String pvKey = PV_KEY_PREFIX + yesterday;
        String uvKey = UV_KEY_PREFIX + yesterday;

        long pv = getPv(yesterday);
        long uv = getUv(yesterday);

        log.info("日报: 日期={}, PV={}, UV={}", yesterday, pv, uv);
    }
}
