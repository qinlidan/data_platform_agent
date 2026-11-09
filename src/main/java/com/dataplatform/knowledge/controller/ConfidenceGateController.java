package com.dataplatform.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.entity.LowConfidenceQuestion;
import com.dataplatform.knowledge.confidence.ThresholdCalibrator;
import com.dataplatform.mapper.LowConfidenceQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 置信度闸管理页面Controller
 */
@Slf4j
@Controller
@RequestMapping("/api/confidence")
@RequiredArgsConstructor
public class ConfidenceGateController {

    private final LowConfidenceQuestionMapper lowConfidenceQuestionMapper;
    private final ThresholdCalibrator thresholdCalibrator;

    /**
     * 低置信度问题池页面
     */
    @GetMapping("/pool")
    public String poolPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            Model model) {

        Page<LowConfidenceQuestion> pagination = new Page<>(page, pageSize);
        LambdaQueryWrapper<LowConfidenceQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(LowConfidenceQuestion::getCreatedAt);

        Page<LowConfidenceQuestion> result = lowConfidenceQuestionMapper.selectPage(pagination, wrapper);

        model.addAttribute("questions", result.getRecords());
        model.addAttribute("total", result.getTotal());
        model.addAttribute("pages", result.getPages());
        model.addAttribute("current", result.getCurrent());

        return "confidence/pool";
    }

    /**
     * 置信度阈值标定页面
     */
    @GetMapping("/calibrate-ui")
    public String calibrateUIPage() {
        return "confidence/calibrate";
    }

    /**
     * 标定阈值API
     */
    @PostMapping("/calibrate")
    @ResponseBody
    public ThresholdCalibrator.CalibrationReport calibrate() {
        try {
            log.info("Starting threshold calibration from evaluation set");
            return thresholdCalibrator.calibrateFromEvaluation();
        } catch (Exception e) {
            log.error("Calibration failed", e);
            throw new RuntimeException("阈值标定失败: " + e.getMessage());
        }
    }

    /**
     * 统计信息API
     */
    @GetMapping("/stats")
    @ResponseBody
    public PoolStats getStats() {
        Long total = lowConfidenceQuestionMapper.selectCount(null);

        LambdaQueryWrapper<LowConfidenceQuestion> retrievalWrapper = new LambdaQueryWrapper<>();
        retrievalWrapper.eq(LowConfidenceQuestion::getSource, "retrieval_low_conf");
        Long retrievalLow = lowConfidenceQuestionMapper.selectCount(retrievalWrapper);

        LambdaQueryWrapper<LowConfidenceQuestion> selfCheckWrapper = new LambdaQueryWrapper<>();
        selfCheckWrapper.eq(LowConfidenceQuestion::getSource, "self_check");
        Long selfCheck = lowConfidenceQuestionMapper.selectCount(selfCheckWrapper);

        return new PoolStats(total, retrievalLow, selfCheck);
    }

    public record PoolStats(Long total, Long retrievalLowConf, Long selfCheck) {}
}
