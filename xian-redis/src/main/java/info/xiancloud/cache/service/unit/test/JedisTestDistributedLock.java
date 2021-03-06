package info.xiancloud.cache.service.unit.test;

import com.alibaba.fastjson.JSONObject;
import info.xiancloud.cache.service.CacheGroup;
import info.xiancloud.core.Group;
import info.xiancloud.core.Input;
import info.xiancloud.core.Unit;
import info.xiancloud.core.UnitMeta;
import info.xiancloud.core.message.SyncXian;
import info.xiancloud.core.message.UnitRequest;
import info.xiancloud.core.message.UnitResponse;
import info.xiancloud.core.support.cache.lock.DistributedLockSynchronizer;
import info.xiancloud.core.thread_pool.ThreadPoolManager;
import info.xiancloud.core.util.LOG;

import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * Redis Distributed Lock
 */
public class JedisTestDistributedLock implements Unit {

    @Override
    public Group getGroup() {
        return CacheGroup.singleton;
    }

    @Override
    public String getName() {
        return "jedisTestDistributedLock";
    }

    @Override
    public UnitMeta getMeta() {
        return UnitMeta.create().setDescription("jedisTestDistributedLock").setPublic(false);
    }

    @Override

    public Input getInput() {
        return new Input().add("number", int.class, "", NOT_REQUIRED);
    }

    @Override
    public UnitResponse execute(UnitRequest msg) {
        int number = msg.get("number", int.class, 10000);

        final CountDownLatch startCountDownLatch = new CountDownLatch(1);

        final CountDownLatch finishCountDownLatch = new CountDownLatch(number);

        final List<Long> consumeTimes = new CopyOnWriteArrayList<>();

        for (int i = 0; i < number; i++) {
            int _i = i;
            ThreadPoolManager.execute(() -> {
                try {
                    startCountDownLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                long startTime = System.nanoTime();

                // Redis Lock
                DistributedLockSynchronizer.call("QPS_" + _i, 3, () -> {
                    return null;
                }, 3);

                // ZK Lock
//                try
//                {
//                    Synchronizer.call("QPS_" + _i, () -> {
//                        return null;
//                    }, 3L);
//                }
//                catch (Exception e)
//                {
//                    LOG.error(e);
//                }

                finishCountDownLatch.countDown();

                long endTime = System.nanoTime();

                consumeTimes.add(endTime - startTime);
            });
        }

        try {
            startCountDownLatch.countDown();

            finishCountDownLatch.await();
        } catch (Exception e) {
            LOG.error(e);
        }

        long totalConsumeTime = 0;
        for (long consumeTime : consumeTimes)
            totalConsumeTime += consumeTime;

        long avgConsumeTime = totalConsumeTime / number;

        LongSummaryStatistics intSummaryStatistics = consumeTimes.stream().collect(Collectors.summarizingLong(value -> value));

        String log = String.format("QPS, 加锁解锁, 任务数量: %s, 累计耗时: %s, %s, 平均耗时：%s, %s, %s", number, totalConsumeTime, totalConsumeTime / 1000000, avgConsumeTime, avgConsumeTime / 1000000, intSummaryStatistics);

        LOG.info(log);

        UnitResponse unitResponseObject = SyncXian.call("cache", "cacheKeys", new JSONObject() {{
            put("pattern", "LOCK_QPS_*");
        }});
        if (unitResponseObject.succeeded() && unitResponseObject.getData() != null) {
            Set<String> keys = unitResponseObject.getData();

            LOG.info(String.format("分布锁剩余数量: %s", keys.size()));
        }

        SyncXian.call("diyMonitor", "jedisLockMonitor", new JSONObject() {{
        }});

        return UnitResponse.success(log);
    }

}
