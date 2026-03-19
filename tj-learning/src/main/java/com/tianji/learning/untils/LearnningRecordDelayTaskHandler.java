package com.tianji.learning.untils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

@Component
@RequiredArgsConstructor
@Slf4j
public class LearnningRecordDelayTaskHandler {
    private final StringRedisTemplate stringRedisTemplate;
    private final DelayQueue<DelayTask<RecordTaskData>> delayQueue = new DelayQueue<>();
    //学习记录缓存key模板，{}占位符，后面会解析为lessonId，sectionId，userId
    private final static String RECORD_KEY_TEMOLATE = "learning:record:{}";
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;
    private static volatile boolean begin = true;

    @PostConstruct
    public void init(){
        CompletableFuture.runAsync(this::handleDelayTask);
    }
    @PreDestroy
    public void stop() {
        begin = false;
        log.debug("学习记录延迟任务已停止");
    }

    public void handleDelayTask(){
        while (begin) {
            try {
                //获取到期延迟任务
                DelayTask<RecordTaskData> delayTask = delayQueue.take();
                //查看redis缓存
                LearningRecord cacheData = readRedisCache(delayTask.getData().getSectionId(),
                        delayTask.getData().getLessonId());
                //缓存不存在，直接忽略,继续下一次循环
                if (cacheData == null) {
                    //缓存中没有数据，直接忽略
                    continue;
                }
                //比较数据，moment
                if (!Objects.equals(cacheData.getMoment(), delayTask.getData().getMoment())) {
                    //不一样，说明学习记录已经变化，放弃旧数据
                    continue;
                }
                //一样，持久化播放进度到数据库，然后删除缓存
                //更新学习记录moment
                cacheData.setFinished(null);
                recordMapper.updateById(cacheData);
                //更新课表最近学习时间
                LearningLesson learningLesson = new LearningLesson();
                learningLesson.setId(delayTask.getData().getLessonId());
                learningLesson.setLatestSectionId(delayTask.getData().getSectionId());
                learningLesson.setLatestLearnTime(LocalDateTime.now());
                lessonService.updateById(learningLesson);
            } catch (Exception e) {
                log.error("学习记录延迟任务执行失败", e);
            }
        }
    }


    public void addLearningRecordDelayTask(LearningRecord learningRecord) {
        //添加数据到redis
        writeRecordCache(learningRecord);
        //添加延迟任务到延时队列
        delayQueue.add(new DelayTask<>(Duration.ofSeconds(20), new RecordTaskData(learningRecord)));

    }

    public void writeRecordCache(LearningRecord learningRecord) {
        try {
            log.debug("更新学习记录{}缓存", learningRecord);
            //数据转换
            String jsonStr = JsonUtils.toJsonStr(new RecordCacheData(learningRecord));
            //写入redis
            String key = StringUtils.format(RECORD_KEY_TEMOLATE, learningRecord.getLessonId());
            stringRedisTemplate.opsForHash().put(key, learningRecord.getSectionId().toString(), jsonStr);
            //添加过期时间
            stringRedisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("更新学习记录{}缓存失败", learningRecord, e);
        }

    }
    public LearningRecord readRedisCache(Long sectionId,Long lessonId) {
        try {
            String key = StringUtils.format(RECORD_KEY_TEMOLATE, lessonId);
            String jsonStr = (String) stringRedisTemplate.opsForHash().get(key, sectionId.toString());
            if (StringUtils.isBlank(jsonStr)) {
                return null;
            }
            return JsonUtils.toBean(jsonStr, LearningRecord.class);
        } catch (Exception e) {
            log.error("读取学习记录缓存失败",  e);
            return null;
        }
    }
    public void deleteRedisCache(Long sectionId,Long lessonId) {
        try {
            String key = StringUtils.format(RECORD_KEY_TEMOLATE, lessonId);
            stringRedisTemplate.opsForHash().delete(key, sectionId.toString());
        } catch (Exception e) {
            log.error("删除学习记录{}缓存失败", lessonId, e);
        }
    }

    @Data
    @NoArgsConstructor
    private static class RecordCacheData {
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord learningRecord) {
            this.id = learningRecord.getId();
            this.moment = learningRecord.getMoment();
            this.finished = learningRecord.getFinished();
        }
    }

    @Data
    @NoArgsConstructor
    private static class RecordTaskData {
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord learningRecord) {
            this.lessonId = learningRecord.getLessonId();
            this.sectionId = learningRecord.getSectionId();
            this.moment = learningRecord.getMoment();
        }
    }
}
