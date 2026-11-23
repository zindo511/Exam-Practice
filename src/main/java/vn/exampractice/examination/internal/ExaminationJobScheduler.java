package vn.exampractice.examination.internal;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Chạy {@link ExaminationJob} định kỳ, mặc định 30 giây sau khi lượt trước xong (ADR-0013).
 *
 * <p>Tắt bằng {@code app.examination.job.enabled=false}; test tắt nó để luồng chạy nền không ghi vào
 * dữ liệu của test, và tự gọi {@link ExaminationJob#runOnce} khi cần.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.examination.job.enabled", havingValue = "true", matchIfMissing = true)
class ExaminationJobScheduler {

    private final ExaminationJob job;

    ExaminationJobScheduler(ExaminationJob job) {
        this.job = job;
    }

    @Scheduled(fixedDelayString = "${app.examination.job.interval:PT30S}",
            initialDelayString = "${app.examination.job.interval:PT30S}")
    void run() {
        job.runOnce(Instant.now());
    }
}
