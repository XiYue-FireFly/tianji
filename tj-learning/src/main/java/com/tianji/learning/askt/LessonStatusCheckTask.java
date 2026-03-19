package com.tianji.learning.askt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LessonStatusCheckTask {

    private final ILearningLessonService lessonService;

    @Scheduled(cron = "0 * * * * ?")    // 每分钟执行一次
    public void lessonStatusCheck() {
        //查询状态为未过期的课程
        LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<>();
        //课程状态不等于已失效
        wrapper.ne(LearningLesson::getStatus, LessonStatus.EXPIRED);
        List<LearningLesson> list = lessonService.list(wrapper);

        LocalDateTime now = LocalDateTime.now();
        //  将当前时间和为过期的课程比较 判断是否过期
        for (LearningLesson lesson : list) {
            if (now.isAfter(lesson.getExpireTime())) {
                lesson.setStatus(LessonStatus.EXPIRED);
            }
        }
        // 3. 批量更新
        lessonService.updateBatchById(list);
    }
}
