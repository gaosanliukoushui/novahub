package com.novahub.feed.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedScheduledService {

    private final FeedService feedService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void refreshRecommendFeed() {
        log.info("定时任务: 刷新推荐流");
        feedService.buildRecommendFeed();
    }
}
