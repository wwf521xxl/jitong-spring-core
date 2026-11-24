package com.jitong.core.report;

import com.jitong.core.bootstrap.BootstrapResponse;
import com.jitong.core.bootstrap.BootstrapService;
import com.jitong.core.photo.PhotoService;
import com.jitong.core.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RecordController {

    private final ReportingService reportingService;
    private final BootstrapService bootstrapService;
    private final PhotoService photoService;

    @PostMapping("/api/records")
    public BootstrapResponse createRecord(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody RecordCreateRequest body
    ) {
        RecordView record = reportingService.createRecord(user, new RecordCreateCommand(
                body.jobId(),
                body.stepNo(),
                body.qty(),
                body.unit(),
                body.note(),
                body.idempotencyKey(),
                body.source(),
                body.deviceId(),
                body.photo(),
                body.forceDuplicate()
        ), false);
        return bootstrapService.build(user).toBuilder().record(record).build();
    }

    @PostMapping("/api/records/{recordId}/corrections")
    public BootstrapResponse requestCorrection(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String recordId,
            @RequestBody(required = false) CorrectionRequest body
    ) {
        reportingService.requestCorrection(user, recordId, new CorrectionCommand(
                body == null ? null : body.reason(),
                body == null ? null : body.note()));
        return bootstrapService.build(user);
    }

    @PostMapping("/api/admin/records/{recordId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public BootstrapResponse approveRecord(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String recordId
    ) {
        reportingService.approveRecord(user, recordId);
        return bootstrapService.build(user);
    }

    @PostMapping("/api/admin/records/{recordId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public BootstrapResponse rejectRecord(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String recordId,
            @RequestBody(required = false) RejectRecordRequest body
    ) {
        reportingService.rejectRecord(user, recordId, body == null ? null : body.reason());
        return bootstrapService.build(user);
    }

    @PostMapping("/api/admin/corrections/{correctionId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public BootstrapResponse approveCorrection(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String correctionId
    ) {
        reportingService.decideCorrection(user, correctionId, true);
        return bootstrapService.build(user);
    }

    @PostMapping("/api/admin/corrections/{correctionId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public BootstrapResponse rejectCorrection(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String correctionId
    ) {
        reportingService.decideCorrection(user, correctionId, false);
        return bootstrapService.build(user);
    }

    @GetMapping("/api/photos/{filename:.+}")
    public ResponseEntity<Resource> photo(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String filename
    ) {
        Resource resource = photoService.load(filename);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }
}
