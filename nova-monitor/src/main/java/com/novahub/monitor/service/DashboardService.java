package com.novahub.monitor.service;

import com.novahub.monitor.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PvUvService pvUvService;
    private final ActivityStatsService activityStatsService;
    private final ContentStatsService contentStatsService;

    public DashboardVO getTodayDashboard() {
        String today = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));

        long pv = pvUvService.getPv(today);
        long uv = pvUvService.getUv(today);
        long dau = activityStatsService.getDau();
        long wau = activityStatsService.getWau();

        return DashboardVO.builder()
                .date(today)
                .pv(pv)
                .uv(uv)
                .dau(dau)
                .wau(wau)
                .newContentCount(getTodayNewContentCount())
                .newUserCount(getTodayNewUserCount())
                .build();
    }

    public Map<String, Object> getTrend(int days) {
        Map<String, Object> result = new HashMap<>();
        result.put("pvTrend", pvUvService.getPvTrend(days));
        result.put("uvTrend", pvUvService.getUvTrend(days));
        result.put("publishTrend", activityStatsService.getPublishTrend(days));
        return result;
    }

    private long getTodayNewContentCount() {
        return 0;
    }

    private long getTodayNewUserCount() {
        return 0;
    }
}
