package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author 沐雪聆曦
 * @since 2026-03-17
 */
@RestController
@RequestMapping("/learning-records")
@Api("学习记录相关接口")
@RequiredArgsConstructor // 生成一个构造函数，包含所有 final 修饰的字段
public class LearningRecordController {
    private final ILearningRecordService learningRecordService;
    @ApiOperation("查询指定课程的学习记录")
    @GetMapping("/course/{courseId}")
    public LearningLessonDTO queryLearningRecordByCourseId(
      @ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long courseId){

        return learningRecordService.queryLearningRecordByCourseId(courseId);
    }
    @PostMapping
    @ApiOperation("添加学习记录")
    public void addLearningRecord(@Valid @RequestBody LearningRecordFormDTO form){
        learningRecordService.addLearningRecord(form);
    }


}
