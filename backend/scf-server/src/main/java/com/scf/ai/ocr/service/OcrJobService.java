package com.scf.ai.ocr.service;

import com.scf.ai.ocr.dto.OcrConfirmRequest;
import com.scf.ai.ocr.dto.OcrConfirmResponse;
import com.scf.ai.ocr.dto.OcrFieldView;
import com.scf.ai.ocr.dto.OcrJobCreateRequest;
import com.scf.ai.ocr.dto.OcrJobView;
import com.scf.ai.ocr.entity.AiOcrField;
import com.scf.ai.ocr.entity.AiOcrJob;
import com.scf.ai.ocr.repository.AiOcrFieldRepository;
import com.scf.ai.ocr.repository.AiOcrJobRepository;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.file.entity.SysFile;
import com.scf.file.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OcrJobService {

    private static final BigDecimal CONFIDENCE_THRESHOLD = new BigDecimal("0.85");
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String CONFIRM_PENDING = "PENDING";
    private static final String CONFIRM_CONFIRMED = "CONFIRMED";

    private final AiOcrJobRepository jobRepository;
    private final AiOcrFieldRepository fieldRepository;
    private final FileService fileService;
    private final TenantContext tenantContext;

    public OcrJobService(
            AiOcrJobRepository jobRepository,
            AiOcrFieldRepository fieldRepository,
            FileService fileService,
            TenantContext tenantContext) {
        this.jobRepository = jobRepository;
        this.fieldRepository = fieldRepository;
        this.fileService = fileService;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public OcrJobView createJob(OcrJobCreateRequest request) {
        tenantContext.requirePermission("AI_OCR_EXECUTE");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        SysFile file = fileService.requireAccessibleFile(request.fileId());

        String jobId = IdGenerator.nextId();
        UserContext user = SecurityUtils.currentUser();
        AiOcrJob job = new AiOcrJob();
        job.setId(jobId);
        job.setOperatorId(operatorId);
        job.setProjectId(projectId);
        job.setFileId(file.getId());
        job.setBusinessType(request.businessType());
        job.setBusinessId(blankToNull(request.businessId()));
        job.setRecognitionType(request.recognitionType());
        job.setStatus(STATUS_COMPLETED);
        job.setModelVersion("mock-v1");
        job.setCreatedBy(user.userId());
        job.setCreatedAt(Instant.now());
        jobRepository.save(job);

        List<AiOcrField> fields = buildMockFields(jobId, request.businessType());
        fieldRepository.saveAll(fields);

        return toView(job, fields);
    }

    @Transactional(readOnly = true)
    public OcrJobView getJob(String id) {
        tenantContext.requirePermission("AI_OCR_VIEW");
        AiOcrJob job = requireJob(id);
        List<AiOcrField> fields = fieldRepository.findByJobIdOrderByFieldNameAsc(id);
        return toView(job, fields);
    }

    @Transactional
    public OcrConfirmResponse confirm(String id, OcrConfirmRequest request) {
        tenantContext.requirePermission("OCR_RESULT_CONFIRM");
        AiOcrJob job = requireJob(id);
        if (STATUS_CONFIRMED.equals(job.getStatus())) {
            throw new BusinessException("STATE_409", "OCR 任务已确认", 409);
        }

        List<AiOcrField> fields = fieldRepository.findByJobIdOrderByFieldNameAsc(id);
        Map<String, String> confirmed = request.confirmedFields();
        for (AiOcrField field : fields) {
            if (field.getConfidence().compareTo(CONFIDENCE_THRESHOLD) < 0) {
                String value = confirmed.get(field.getFieldName());
                if (value == null || value.isBlank()) {
                    throw new BusinessException(
                            "VALID_400",
                            "字段 " + field.getFieldName() + " 置信度低于 0.85，需人工确认",
                            400);
                }
                field.setConfirmedValue(value.trim());
                field.setConfirmStatus(CONFIRM_CONFIRMED);
            } else if (confirmed.containsKey(field.getFieldName())) {
                field.setConfirmedValue(confirmed.get(field.getFieldName()));
                field.setConfirmStatus(CONFIRM_CONFIRMED);
            }
        }
        fieldRepository.saveAll(fields);

        UserContext user = SecurityUtils.currentUser();
        job.setStatus(STATUS_CONFIRMED);
        job.setConfirmedBy(user.userId());
        job.setConfirmedAt(Instant.now());
        jobRepository.save(job);

        return new OcrConfirmResponse(
                job.getId(),
                true,
                false,
                "mock confirm 完成，未写入客户/订单/价格/融资等业务表");
    }

    private OcrJobView toView(AiOcrJob job, List<AiOcrField> fields) {
        List<OcrFieldView> fieldViews = fields.stream().map(OcrFieldView::from).toList();
        int pending = (int) fieldViews.stream()
                .filter(f -> f.requiresManualConfirm() && !CONFIRM_CONFIRMED.equals(f.confirmStatus()))
                .count();
        return new OcrJobView(
                job.getId(),
                job.getFileId(),
                job.getBusinessType(),
                job.getBusinessId(),
                job.getRecognitionType(),
                job.getStatus(),
                job.getModelVersion(),
                job.getCreatedBy(),
                job.getCreatedAt(),
                job.getConfirmedBy(),
                job.getConfirmedAt(),
                fieldViews,
                pending);
    }

    private AiOcrJob requireJob(String id) {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        return jobRepository.findByIdAndOperatorIdAndProjectId(id, operatorId, projectId)
                .orElseThrow(() -> new BusinessException("DATA_404", "OCR 任务不存在", 404));
    }

    private List<AiOcrField> buildMockFields(String jobId, String businessType) {
        List<MockField> mocks = switch (businessType) {
            case "ENTERPRISE_CERT" -> List.of(
                    mock("enterprise_name", "示例科技有限公司", "0.92", "示例科技有限公司", 1),
                    mock("unified_credit_code", "91310000MA1KXXXXXX", "0.78", "91310000MA1KXXXXXX", 1),
                    mock("legal_person", "张三", "0.81", "法定代表人：张三", 1),
                    mock("registered_address", "上海市浦东新区示例路 100 号", "0.88", "地址：上海市浦东新区示例路 100 号", 1));
            case "TRADE_DOCUMENT" -> List.of(
                    mock("document_no", "INV-2026-001", "0.91", "发票号码 INV-2026-001", 1),
                    mock("amount", "128000.00", "0.76", "合计金额 128000.00", 1),
                    mock("currency", "CNY", "0.95", "币种：CNY", 1),
                    mock("trade_date", "2026-05-20", "0.83", "开票日期 2026-05-20", 1),
                    mock("seller_name", "核心企业A", "0.89", "销售方：核心企业A", 1));
            default -> List.of(
                    mock("field_a", "识别值A", "0.90", "原文片段A", 1),
                    mock("field_b", "识别值B", "0.72", "原文片段B", 1),
                    mock("field_c", "识别值C", "0.84", "原文片段C", 1));
        };

        List<AiOcrField> fields = new ArrayList<>();
        for (MockField mock : mocks) {
            AiOcrField field = new AiOcrField();
            field.setId(IdGenerator.nextId());
            field.setJobId(jobId);
            field.setFieldName(mock.name());
            field.setSuggestedValue(mock.value());
            field.setConfidence(new BigDecimal(mock.confidence()));
            field.setSourceText(mock.sourceText());
            field.setPageNo(mock.pageNo());
            field.setBbox("[120,240,480,280]");
            field.setConfirmStatus(CONFIRM_PENDING);
            fields.add(field);
        }
        return fields;
    }

    private static MockField mock(String name, String value, String confidence, String source, int page) {
        return new MockField(name, value, confidence, source, page);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record MockField(String name, String value, String confidence, String sourceText, int pageNo) {
    }
}
