package com.novahub.hotrank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final HotRankService hotRankService;

    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyRankRefresh() {
        log.info("定时任务: 小时级热榜刷新");
        hotRankService.fullRecalculateRank();
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void dailyRankRefresh() {
        log.info("定时任务: 日榜重算");
        hotRankService.fullRecalculateRank();
    }

    @Scheduled(cron = "0 0 0 * * MON")
    public void weeklyRankRefresh() {
        log.info("定时任务: 周榜重算");
        hotRankService.fullRecalculateRank();
    }
}
