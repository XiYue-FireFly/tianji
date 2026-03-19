package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author 沐雪聆曦
 * @since 2026-02-28
 */
@Service
@Slf4j
@RequiredArgsConstructor // 生成一个构造函数，包含所有 final 修饰的字段
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper learningRecordMapper;

    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        //查询课程有效期
        List<CourseSimpleInfoDTO> simpleInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(simpleInfoList)) {
            log.error("课程不存在，无法添加课程到学习表{}", courseIds);
            return;
        }
        //循环遍历，处理LearningLesson数据
        List<LearningLesson> lessons = new ArrayList<>(courseIds.size());
        for (CourseSimpleInfoDTO course : simpleInfoList) {
            LearningLesson learningLesson = new LearningLesson();

            Integer validDuration = course.getValidDuration();
            /**
             * 添加用户课程
             * @param userId 用户ID
             * @param courseIds 课程ID
             */
            if (validDuration != null && validDuration > 0) {
                //有有效期则设置有效期
                LocalDateTime now = LocalDateTime.now();
                learningLesson.setCreateTime(now);
                learningLesson.setExpireTime(now.plusMonths(validDuration));
            } else {
                //否则永不过期
                learningLesson.setCreateTime(null);
                learningLesson.setExpireTime(null);
            }
            learningLesson.setUserId(userId);
            learningLesson.setCourseId(course.getId());
            lessons.add(learningLesson);
        }
        //3.添加课程到学习表
        saveBatch(lessons);
        log.info("用户{}添加课程{}到学习表", userId, courseIds);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        //1.获取用户ID
        Long userId = UserContext.getUser();
        //2.分页查询
        Page<LearningLesson> page = lambdaQuery().
                eq(LearningLesson::getUserId, userId)
                .page(pageQuery.toMpPage("latest_learn_time", false));

        /**
         * 查询用户课程
         * @param pageQuery 分页查询参数
         * @return 用户课程分页结果
         */
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        //3.查询课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = getLongCourseSimpleInfoDTOMap(records);

        //4.封装vo
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        for (LearningLesson record : records) {
            //4.1拷贝
            LearningLessonVO vo = BeanUtils.copyBean(record, LearningLessonVO.class);
            //4.2设置课程信息
            CourseSimpleInfoDTO courseSimpleInfoDTO = cMap.get(record.getCourseId());
            //4.3设置课程名称
            vo.setCourseName(courseSimpleInfoDTO.getName());
            //4.4设置课程封面
            vo.setCourseCoverUrl(courseSimpleInfoDTO.getCoverUrl());
            //4.5设置课程章节数
            vo.setSections(courseSimpleInfoDTO.getSectionNum());
            list.add(vo);
        }
        return PageDTO.of(page, list);
    }

    private Map<Long, CourseSimpleInfoDTO> getLongCourseSimpleInfoDTOMap(List<LearningLesson> records) {
        //3.1获取课程ID
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        //3.2查询课程信息
        List<CourseSimpleInfoDTO> courseInfos = courseClient.getSimpleInfoList(new ArrayList<>(courseIds));
        if (CollUtils.isEmpty(courseInfos)) {
            //课程信息不存在，抛出异常
            throw new BadRequestException("课程信息不存在");
        }
        //3.3课程信息转map, key为课程ID，value为课程信息,方便后面对课程信息进行获取
        Map<Long, CourseSimpleInfoDTO> cMap = courseInfos.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId,
                        c -> c));
        return cMap;
    }

    @Override
    @Transactional
    public void deleteUserLessons(Long userId, Long courseIds) {
        //1.校验课程是否存在
        /**
         * 删除用户课程
         * @param userId 用户ID
         * @param courseIds 课程ID
         */
        List<CourseSimpleInfoDTO> simpleInfoList = courseClient.getSimpleInfoList(List.of(courseIds));
        if (CollUtils.isEmpty(simpleInfoList)) {
            log.error("课程不存在，无法删除课程{}", courseIds);
            return;
        }
        //2.删除课程
        lambdaUpdate().eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getCourseId, courseIds)
                .remove();
        log.info("用户{}删除课程{}从学习表", userId, courseIds);
    }

    /**
     * 查询用户当前正在学习的课程
     *
     * @return 正在学习的课程
     */
    @Override
    @Transactional
    public LearningLessonVO queryNowLesson() {
        //1.获取用户ID
        Long userId = UserContext.getUser();
        //2.查询用户当前正在学习的课程
        // 2.查询正在学习的课程 select * from xx where user_id = #{userId} AND status = 1 order by latest_learn_time limit 1
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null) {
            return null;
        }

        //3.查询课程详细信息
        CourseFullInfoDTO courseInfo = courseClient
                .getCourseInfoById(lesson.getCourseId(), false, false);
        if (courseInfo == null) {
            throw new BizIllegalException("课程信息不存在");
        }

        //4.拷贝PO为VO并设置课程信息
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setCourseName(courseInfo.getName());
        vo.setCourseCoverUrl(courseInfo.getCoverUrl());
        vo.setSections(courseInfo.getSectionNum());

        // 查询当前用户课表中 已报名总的课程数
        Integer courseAmount = Math.toIntExact(lambdaQuery().eq(LearningLesson::getUserId, userId).count());
        vo.setCourseAmount(courseAmount);
        // 远程调用课程服务 获取小节名称 小节编号
        Long latestSectionId = lesson.getLatestSectionId();
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSectionId));
        if (CollUtils.isNotEmpty(cataSimpleInfoDTOS)) {
            // 取第一个 就是最新的章节
            CataSimpleInfoDTO cataSimpleInfoDTO = cataSimpleInfoDTOS.get(0);
            // 设置最新章节名称和编号
            vo.setLatestSectionName(cataSimpleInfoDTO.getName());
            // 设置最新章节编号
            vo.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());
        }
        return vo;
    }

    /**
     * 根据课程ID查询学习记录
     *
     * @param courseId 课程ID
     * @return 学习记录
     */
    @Override
    @Transactional
    public LearningLessonVO queryLearningRecordByCourse(Long courseId) {
        Long userId = UserContext.getUser();
        //查询课表learning_Lesson 条件user_id course_id
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }
        return BeanUtils.copyProperties(lesson, LearningLessonVO.class);
    }

    /**
     * 校验课程是否有效
     *
     * @param courseId 课程ID
     * @return 课程ID
     */
    @Override
    @Transactional
    public Long isLessonValid(Long courseId) {
        Long userId = UserContext.getUser();
        // 查询课表learning_Lesson 条件user_id course_id
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }
        LocalDateTime expireTime = lesson.getExpireTime();
        if (expireTime == null || LocalDateTime.now().isAfter(expireTime)) {
            return null;
        }
        return lesson.getId();
    }

    /**
     * 根据课程ID统计学习记录
     *
     * @param courseId 课程ID
     * @return 学习记录数
     */
    @Override
    @Transactional
    public Integer countLearningLessonByCourse(Long courseId) {
        return Math.toIntExact(lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .count());
    }

    @Override
    public LearningLesson queryByCourseId(Long user, Long courseId) {
        //select * from learning_lesson where user_id = 1 and course_id = 1;
        return lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
    }

    @Override
    public void createLearningPlans(Long courseId, Integer freq) {
        Long userId = UserContext.getUser();
        //1.查询指定课表数据
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            throw new BizIllegalException("课程信息不存在");
        }
        //2.修改数据
        //update learning_lesson set week_freq = 1, plan_status = 1 where id = 1;
        lambdaUpdate()
                .eq(LearningLesson::getId, lesson.getId())
                .set(LearningLesson::getWeekFreq, freq)
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING.getValue())
                .update();
    }

    @Override
    public LearningPlanPageVO queryMyLearningPlans(PageQuery pageQuery) {
        LearningPlanPageVO learningPlanPageVO = new LearningPlanPageVO();
        //获取用户
        Long userId = UserContext.getUser();
        //2.获取本周开始结束时间
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);
        //3.查询总的统计数据
        //3.1 本周总的已经学习的小节数
        //select count(*) from learning_record where user_id = 1 and finished = 1 and create_time between 2025-07-10 00:00:00 and 2025-07-16 23:59:59;
        Long count = learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .ge(LearningRecord::getCreateTime, weekBeginTime)
                .le(LearningRecord::getCreateTime, weekEndTime)
        );
        learningPlanPageVO.setWeekFinished(Math.toIntExact(count));
        //3.2 本周总的计划小节数
        //select count(*) from learning_lesson where user_id = 1 and status in (1,2) and plan_status = 1;
        Long Total = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN.getValue(), LessonStatus.LEARNING.getValue())
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING.getValue())
                .count();
        learningPlanPageVO.setWeekTotalPlan(Math.toIntExact(Total));
        //TODD 3.3 本周积分
        //4.查询分页数据
        //4.1分页查询课表和学习计划
        //select * from learning_lesson where user_id = 1 and status in (1,2) and plan_status = 1 limit 10;
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING.getValue())
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN.getValue(), LessonStatus.LEARNING.getValue())
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return learningPlanPageVO;
        }
        //4.2 查询课表对应的课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = getLongCourseSimpleInfoDTOMap(records);
        //4.3统计每一个课程的本周学习的数量
        List<IdAndNumDTO> list = learningRecordMapper.countLearnedSections(userId, weekBeginTime, weekEndTime);
        Map<Long, Integer> idNumMap = IdAndNumDTO.toMap(list);
        //5.封装数据返回
        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        for (LearningLesson record : records) {
            LearningPlanVO vo = BeanUtils.copyProperties(record, LearningPlanVO.class);
            CourseSimpleInfoDTO cInfo = cMap.get(record.getCourseId());
            if (cInfo != null) {
                vo.setCourseName(cInfo.getName());
                vo.setSections(cInfo.getSectionNum());
            }
            Integer num = idNumMap.getOrDefault(record.getId(), 0);
            vo.setWeekLearnedSections(num);
            voList.add(vo);
        }
        return learningPlanPageVO.pageInfo(page.getTotal(), page.getPages(), voList);
    }

}



