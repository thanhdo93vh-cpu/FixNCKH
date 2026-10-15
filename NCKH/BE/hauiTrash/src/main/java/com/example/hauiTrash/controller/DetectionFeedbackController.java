package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.FeedbackRequest;
import com.example.hauiTrash.service.DetectionFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/detections")
@RequiredArgsConstructor
public class DetectionFeedbackController {

    private final DetectionFeedbackService detectionFeedbackService;

//    @PostMapping("/{id}/feedback")
//    public ResponseEntity<Void> submitFeedback(
//            @PathVariable Integer id,
//            @RequestBody FeedbackRequest req
//    ) {
//        detectionFeedbackService.submitFeedback(
//                id,
//                req.getConfirmedLabel(),
//                req.getFeedbackType(),
//                req.getComment()
//        );
//        return ResponseEntity.ok().build();
//    }

}