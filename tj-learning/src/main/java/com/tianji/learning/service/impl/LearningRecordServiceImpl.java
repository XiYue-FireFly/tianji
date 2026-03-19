package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.tianji.learning.untils.LearnningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author 沐雪聆曦
 * @since 2026-03-17
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService learningLessonService;
    private final CourseClient courseClient;
    private final LearnningRecordDelayTaskHandler learnningRecordDelayTaskHandler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourseId(Long courseId) {
        //获取用户
        Long user = UserContext.getUser();
        //获取课表
        LearningLesson learningLesson = learningLessonService.queryByCourseId(user, courseId);
        if (learningLesson == null) {
            //抛出异常 课程不存在
            throw new IllegalArgumentException("课表不存在");
        }
        //查询学习记录
        List<LearningRecord> records = lambdaQuery().
                eq(LearningRecord::getLessonId, learningLesson.getId())
                .list();
        //dto返回
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));
        dto.setLatestSectionId(learningLesson.getLatestSectionId());
        dto.setId(learningLesson.getId());
        return dto;
    }

    @Override
    public void addLearningRecord(LearningRecordFormDTO form) {
        //1.获取用户
        Long user = UserContext.getUser();
        //2.处理学习记录
        boolean finished = false;
        if (form.getSectionType() == SectionType.VIDEO) {
            //2.1处理视频
            finished = handleVideoRecord(user, form);
        } else {
            //2.2处理考试
            finished = handleExamRecord(user, form);

        }
        if (!finished) {
            //没有学习完，不需要跟新，使用异步延迟任务，不需要跟新课表
            return;
        }
        //3.处理课表
        handleLearningLessonChanges(form);
    }

    private void handleLearningLessonChanges(LearningRecordFormDTO form) {
        //查询课表
        LearningLesson byId = learningLessonService.getById(form.getLessonId());
        if (byId == null) {
            throw new BizIllegalException("课程不存在，无法更新");
        }
        boolean allLearned = false;

            //有   查询课程数据
            CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(byId.getCourseId(), false, false);
            if (cInfo == null) {
                throw new BizIllegalException("课程不存在，无法更新");
            }
            //比较课程有没有全部学习完：已经学习的>=课程的小节总数
            allLearned = byId.getLearnedSections() + 1 >= cInfo.getSectionNum();

        //更新
        //sql语句：update learning_lesson set status=1,learned_sections=learned_sections+1,latest_section_id=xxx,latest_learn_time=xxx where id=xxx;
        learningLessonService.lambdaUpdate()
                ////.set(lesson.getStatus() == LessonStatus.NOT_BEGIN ,LearningLesson::getStatus,LessonStatus.LEARNING)
                .set(byId.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .setSql("learned_sections=learned_sections+1")
                .eq(LearningLesson::getId, byId.getId())
                .update();
    }


    private boolean handleExamRecord(Long user, LearningRecordFormDTO form) {
        //1.dto->po
        LearningRecord po = BeanUtils.copyBean(form, LearningRecord.class);
        po.setUserId(user);
        po.setFinished(true);
        po.setFinishTime(form.getCommitTime());
        //2.保存记录
        boolean success = save(po);
        if (!success) {
            throw new DbException("保存考试记录失败");
        }
        return true;
    }

    private boolean handleVideoRecord(Long user, LearningRecordFormDTO form) {
        //查看有没有记录
        //select * from learning_record where user_id=xxx and lesson_id=xxx and section_id=xxx;
        LearningRecord old = queryOldRecord(form.getLessonId(),form.getSectionId());
        //有  更新
        if (old != null) {
            //判断是不是第一次
            boolean finished = form.getMoment() * 2 >= form.getDuration() && !old.getFinished();
            if (!finished) {
                //添加完成任务
                LearningRecord learningRecord = new LearningRecord();
                learningRecord.setLessonId(form.getLessonId());
                learningRecord.setSectionId(form.getSectionId());
                learningRecord.setMoment(form.getMoment());
                learningRecord.setId(old.getId());
                learningRecord.setFinished(old.getFinished());
                learnningRecordDelayTaskHandler.addLearningRecordDelayTask(learningRecord);
                return false;
            }
            //update learning_record set moment=xxx,finished=xxx,finish_time=xxx where id=xxx;
            boolean success = lambdaUpdate()
                    .set(LearningRecord::getMoment, form.getMoment())
                    .set(finished, LearningRecord::getFinished, true)
                    .set(LearningRecord::getFinishTime, form.getCommitTime())
                    .eq(LearningRecord::getId, old.getId())
                    .update();
            if (!success) {
                throw new DbException("更新视频记录失败");
            }
            //清理缓存
            learnningRecordDelayTaskHandler.deleteRedisCache(form.getLessonId(), form.getSectionId());
            return true;
        } else {
            //没有 插入
            //dto->po
            LearningRecord po = BeanUtils.copyBean(form, LearningRecord.class);
            po.setUserId(user);
            boolean success = save(po);
            if (!success) {
                throw new DbException("保存视频记录失败");
            }
        }
        return false;
    }

    private LearningRecord queryOldRecord(@NotNull(message = "课表id不能为空") Long lessonId,
                                          @NotNull(message = "节的id不能为空") Long sectionId) {
    //查询缓存
        LearningRecord cache = learnningRecordDelayTaskHandler.readRedisCache(lessonId, sectionId);
        //有返回
        if (cache != null) {
            return cache;
        }
        //没有查数据库
        LearningRecord old = queryOldRecordMySQL(lessonId, sectionId);
    //写入缓存
        learnningRecordDelayTaskHandler.writeRecordCache(old);
        return old;
    }

    private LearningRecord queryOldRecordMySQL(Long lessonId, Long sectionId) {
        return lambdaQuery()
                .eq(LearningRecord::getLessonId,lessonId )
                .eq(LearningRecord::getSectionId,sectionId)
                .one();
    }
}
