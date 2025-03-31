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
                1,
                TimeUnit.DAYS
        );
    }

    private void cleanTask() {
        int count = shareService.cleanExpiredShares();
        System.out.println("本喵清理了过期分享记录 " + count + "条喵 明天继续努力ψ(｀∇´)ψ");
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