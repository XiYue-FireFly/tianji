package com.tianji.learning.untils;

import lombok.Data;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
@Data
public class DelayTask<D> implements Delayed {
    private long deadlineNanos;
    private D data;

    public DelayTask(Duration deadlineNanos, D data) {
        this.deadlineNanos = System.nanoTime() + deadlineNanos.toNanos();
        this.data = data;
    }


    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        long l = getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
        return l == 0 ? 0 : (l > 0 ? 1 : -1);
    }
}
