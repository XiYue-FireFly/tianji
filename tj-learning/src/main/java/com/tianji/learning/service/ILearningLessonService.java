package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author 沐雪聆曦
 * @since 2026-02-28
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    /**
     * 为用户添加课程
     * @param userId 用户id
     * @param courseIds 课程id
     */
    void addUserLessons(Long userId, List<Long> courseIds);

    /**
     * 查询用户课程
     * @param pageQuery 分页查询参数
     * @return 用户课程列表
     */
    PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery);

    /**
     * 删除用户课程
     * @param userId 用户id
     * @param courseIds 课程id
     */
    void deleteUserLessons(Long userId, Long courseIds);

    /**
     * 查询用户当前正在学习的课程
     * @return 正在学习的课程
     */
    LearningLessonVO queryNowLesson();

    /**
     * 根据课程id查询学习记录
     * @param courseId 课程id
     * @return 学习记录
     */
    LearningLessonVO queryLearningRecordByCourse(Long courseId);

    /**
     * 校验课程是否有效
     * @param courseId 课程id
     * @return 课程id
     */
    Long isLessonValid(Long courseId);

    /**
     * 根据课程id统计学习课程
     * @param courseId 课程id
     * @return 学习课程数量
     */
    Integer countLearningLessonByCourse(Long courseId);
}
