package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.R;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.Transient;
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
}
