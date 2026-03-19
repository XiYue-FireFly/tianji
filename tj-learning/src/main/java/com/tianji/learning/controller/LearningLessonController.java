package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author 沐雪聆曦
 * @since 2026-02-28
 */
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
@Api(tags = "我的课程表管理相关接口")
public class LearningLessonController {
    private final ILearningLessonService learningLessonService;

    @GetMapping("/page")
    @ApiOperation("分页查询我的课程表")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        return learningLessonService.queryMyLessons(pageQuery);
    }
    @GetMapping("/now")
    @ApiOperation("查询正在上的课程")
    public LearningLessonVO queryNowLesson() {
        return learningLessonService.queryNowLesson();
    }
    @DeleteMapping("/{courseId}")
    @ApiOperation("删除课程")
    public void deleteUserLessons(@PathVariable("courseId") Long courseId) {
        Long userId = UserContext.getUser();
        learningLessonService.deleteUserLessons(courseId, userId);
    }
    @GetMapping("course/{courseId}")
    @ApiOperation("查询当前用户指定课程的学习进度")
    public LearningLessonVO queryLearningRecordByCourse(@PathVariable("courseId") Long courseId) {
        return learningLessonService.queryLearningRecordByCourse(courseId);
    }
    /**
     * 校验当前用户是否可以学习当前课程
     * @param courseId 课程id
     * @return lessonId，如果是报名了则返回lessonId，否则返回空
     */
    @ApiOperation("校验当前用户是否可以学习当前课程")
    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@PathVariable("courseId") Long courseId){
        return learningLessonService.isLessonValid(courseId);
    }

    @ApiOperation("查询该课程的报名人数")
    @GetMapping("/lessons/{courseId}/count")
    Integer countLearningLessonByCourse(
            @ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long courseId){
        return learningLessonService.countLearningLessonByCourse(courseId);
    }
    @PostMapping("/plans")
    public void createLearningPlans(@RequestBody LearningPlanDTO planDTO){
        learningLessonService.createLearningPlans(planDTO.getCourseId(), planDTO.getFreq());
    }
    @GetMapping("plans")
    @ApiOperation("查询我的学习计划")
    public LearningPlanPageVO queryMyLearningPlans(PageQuery pageQuery){
       return learningLessonService.queryMyLearningPlans(pageQuery);
    }

}
