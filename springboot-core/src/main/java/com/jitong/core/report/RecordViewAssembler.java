package com.jitong.core.report;

import com.jitong.core.common.TimeFormat;
import org.springframework.stereotype.Component;

@Component
public class RecordViewAssembler {

    public RecordView toView(PieceRecord record) {
        String photo = record.getPhotoPath() == null || record.getPhotoPath().isBlank()
                ? null
                : "/api/photos/" + record.getPhotoPath();
        return new RecordView(
                record.getId(),
                record.getJobId(),
                record.getStepNo(),
                record.getEmployeeId(),
                record.getQty(),
                record.getUnit(),
                TimeFormat.iso(record.getCreatedAt()),
                record.getStatus().wireValue(),
                record.getNote() == null ? "" : record.getNote(),
                record.getSource() == null ? "scan" : record.getSource(),
                true,
                photo,
                record.getApprovedBy(),
                TimeFormat.iso(record.getApprovedAt())
        );
    }

    public CorrectionView toView(Correction correction) {
        return new CorrectionView(
                correction.getId(),
                correction.getRecordId(),
                correction.getRequestedBy(),
                correction.getReason(),
                correction.getNote() == null ? "" : correction.getNote(),
                correction.getStatus().wireValue(),
                correction.getApprovedBy(),
                TimeFormat.iso(correction.getCreatedAt())
        );
    }
}
