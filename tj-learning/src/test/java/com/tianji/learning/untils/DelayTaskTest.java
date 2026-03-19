package com.tianji.learning.untils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.DelayQueue;

@Slf4j
class DelayTaskTest {

    @Test
    void testSchedule() {
        log.info("start");
        DelayQueue<DelayTask<String>> delayeds = new DelayQueue<>();
        delayeds.add(new DelayTask<>(Duration.ofSeconds(3), "3"));
        delayeds.add(new DelayTask<>(Duration.ofSeconds(1), "1"));
        delayeds.add(new DelayTask<>(Duration.ofSeconds(2), "2"));
        log.info("{}", delayeds);
        while (true) {
            try {
                log.info("{}", delayeds.take());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}