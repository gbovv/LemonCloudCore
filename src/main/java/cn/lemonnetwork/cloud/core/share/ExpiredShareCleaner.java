package cn.lemonnetwork.cloud.core.share;

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExpiredShareCleaner {
    private final ShareService shareService = new ShareService();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(
                this::cleanTask,
                calculateInitialDelay(),
                24 * 60 * 60,
                TimeUnit.SECONDS
        );
    }

    private void cleanTask() {
        int count = shareService.cleanExpiredShares();
        System.out.println("已清理过期分享记录: " + count + "条");
    }

    private long calculateInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(3).withMinute(0).withSecond(0);
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).getSeconds();
    }
}