package com.globalcodelabs.socialmediaplanner.interfaces.rest.controller;

import com.globalcodelabs.socialmediaplanner.application.command.CreateGenerationBatchCommand;
import com.globalcodelabs.socialmediaplanner.application.command.UploadedDocument;
import com.globalcodelabs.socialmediaplanner.infrastructure.scheduler.GenerationBatchJob;
import com.globalcodelabs.socialmediaplanner.application.service.GenerationBatchService;
import com.globalcodelabs.socialmediaplanner.application.service.GenerationBudgetPolicy;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.request.AiModelSelectionRequest;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.request.CreateGenerationBatchRequest;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.request.GenerationBudgetEstimateRequest;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.response.GenerationBatchResponse;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.response.GenerationBatchPageResponse;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.response.GenerationBudgetResponse;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.response.GenerationBudgetEstimateResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/generation-batches")
@RequiredArgsConstructor
@Validated
public class GenerationBatchController {

    private final GenerationBatchService generationBatchService;
    private final GenerationBatchJob generationBatchProcessingService;
    private final GenerationBudgetPolicy generationBudgetPolicy;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GenerationBatchResponse> create(
            @Valid @RequestPart("request") CreateGenerationBatchRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        GenerationBatch batch = generationBatchService.create(toCommand(request, files));
        startProcessing(batch);
        return ResponseEntity.accepted().body(GenerationBatchResponse.from(batch));
    }

    @GetMapping("/{batchId}")
    public GenerationBatchResponse get(@PathVariable UUID batchId) {
        return GenerationBatchResponse.from(generationBatchService.get(batchId));
    }

    @GetMapping("/budget")
    public GenerationBudgetResponse getBudgetPolicy() {
        return GenerationBudgetResponse.from(generationBudgetPolicy.limits());
    }

    @PostMapping("/budget/estimate")
    public GenerationBudgetEstimateResponse estimateBudget(
            @Valid @RequestBody GenerationBudgetEstimateRequest request
    ) {
        return GenerationBudgetEstimateResponse.from(
                generationBudgetPolicy.estimate(new GenerationBudgetPolicy.Request(
                        request.requestedCount(),
                        request.includeImage(),
                        request.includeVideo(),
                        request.textModel().provider(),
                        request.textModel().model()
                ))
        );
    }

    @PostMapping("/{batchId}/retry")
    public ResponseEntity<GenerationBatchResponse> retry(@PathVariable UUID batchId) {
        GenerationBatch batch = generationBatchService.retry(batchId);
        startProcessing(batch);
        return ResponseEntity.accepted().body(GenerationBatchResponse.from(batch));
    }

    @GetMapping
    public GenerationBatchPageResponse list(
            @RequestParam(required = false) GenerationBatchStatus status,
            @RequestParam(required = false) Platform platform,
            @RequestParam(required = false) ContentType contentType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return GenerationBatchPageResponse.from(
                generationBatchService.list(status, platform, contentType, pageRequest)
        );
    }

    private CreateGenerationBatchCommand toCommand(
            CreateGenerationBatchRequest request,
            List<MultipartFile> files
    ) {
        List<UploadedDocument> documents = files == null
                ? List.of()
                : files.stream().map(this::toUploadedDocument).toList();
        return new CreateGenerationBatchCommand(
                request.platform(), request.contentType(), request.title(), request.requestedCount(),
                request.includeImage(), request.includeVideo(),
                request.textModel().provider(), request.textModel().model(),
                provider(request.imageModel()), model(request.imageModel()),
                provider(request.videoModel()), model(request.videoModel()),
                request.generationStrategy(),
                request.links(), documents
        );
    }

    private void startProcessing(GenerationBatch batch) {
        try {
            generationBatchProcessingService.start(batch.id());
        } catch (RuntimeException exception) {
            generationBatchService.markDispatchFailed(batch.id());
            throw exception;
        }
    }

    private UploadedDocument toUploadedDocument(MultipartFile file) {
        try {
            return new UploadedDocument(file.getOriginalFilename(), file.getBytes());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read uploaded document", exception);
        }
    }

    private static String provider(AiModelSelectionRequest selection) {
        return selection == null ? null : selection.provider();
    }

    private static String model(AiModelSelectionRequest selection) {
        return selection == null ? null : selection.model();
    }
}
