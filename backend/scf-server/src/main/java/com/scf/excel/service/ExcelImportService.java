package com.scf.excel.service;

import com.scf.common.exception.BusinessException;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.excel.dto.ExcelImportConfirmRequest;
import com.scf.excel.dto.ExcelImportConfirmResponse;
import com.scf.excel.dto.ExcelImportCreateRequest;
import com.scf.excel.dto.ExcelImportJobView;
import com.scf.excel.dto.ExcelImportRowView;
import com.scf.excel.entity.ExcelImportJob;
import com.scf.excel.entity.ExcelImportRow;
import com.scf.excel.repository.ExcelImportJobRepository;
import com.scf.excel.repository.ExcelImportRowRepository;
import com.scf.file.entity.SysFile;
import com.scf.file.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ExcelImportService {

    private static final String STATUS_PREVIEW = "PREVIEW";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String ROW_OK = "OK";
    private static final String ROW_ERROR = "ERROR";
    private static final String ROW_WARNING = "WARNING";
    private static final Set<String> EXCEL_EXT = Set.of("xlsx", "xls");

    private final ExcelImportJobRepository jobRepository;
    private final ExcelImportRowRepository rowRepository;
    private final FileService fileService;
    private final TenantContext tenantContext;

    public ExcelImportService(
            ExcelImportJobRepository jobRepository,
            ExcelImportRowRepository rowRepository,
            FileService fileService,
            TenantContext tenantContext) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.fileService = fileService;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public ExcelImportJobView createJob(ExcelImportCreateRequest request) {
        tenantContext.requirePermission("EXCEL_IMPORT");
        if ("PRICE_RECORD".equalsIgnoreCase(request.importType())) {
            tenantContext.requirePermission("PRICE_IMPORT");
        }

        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        SysFile file = fileService.requireAccessibleFile(request.fileId());
        validateExcelFile(file);

        String jobId = IdGenerator.nextId();
        String batchId = "BATCH-" + jobId.substring(0, 8).toUpperCase(Locale.ROOT);
        UserContext user = SecurityUtils.currentUser();

        ExcelImportJob job = new ExcelImportJob();
        job.setId(jobId);
        job.setOperatorId(operatorId);
        job.setProjectId(projectId);
        job.setFileId(file.getId());
        job.setImportType(request.importType());
        job.setBatchId(batchId);
        job.setDryRun(request.dryRun() == null || request.dryRun());
        job.setStatus(STATUS_PREVIEW);
        job.setCreatedBy(user.userId());
        job.setCreatedAt(Instant.now());

        List<ExcelImportRow> rows = buildMockRows(jobId, request.importType());
        job.setTotalRows(rows.size());
        job.setOkRows((int) rows.stream().filter(r -> ROW_OK.equals(r.getRowStatus())).count());
        job.setErrorRows((int) rows.stream().filter(r -> ROW_ERROR.equals(r.getRowStatus())).count());
        job.setWarningRows((int) rows.stream().filter(r -> ROW_WARNING.equals(r.getRowStatus())).count());
        jobRepository.save(job);
        rowRepository.saveAll(rows);

        return toView(job, rows);
    }

    @Transactional(readOnly = true)
    public ExcelImportJobView getJob(String id) {
        tenantContext.requirePermission("EXCEL_IMPORT");
        ExcelImportJob job = requireJob(id);
        List<ExcelImportRow> rows = rowRepository.findByJobIdOrderByRowNoAsc(id);
        return toView(job, rows);
    }

    @Transactional
    public ExcelImportConfirmResponse confirm(String id, ExcelImportConfirmRequest request) {
        tenantContext.requirePermission("EXCEL_IMPORT_CONFIRM");
        ExcelImportJob job = requireJob(id);
        if ("PRICE_RECORD".equalsIgnoreCase(job.getImportType())) {
            tenantContext.requirePermission("PRICE_IMPORT");
        }
        if (STATUS_CONFIRMED.equals(job.getStatus())) {
            throw new BusinessException("STATE_409", "导入任务已确认", 409);
        }
        if (!job.getBatchId().equals(request.batchId())) {
            throw new BusinessException("VALID_400", "batch_id 不匹配", 400);
        }
        if (job.getErrorRows() > 0) {
            throw new BusinessException("VALID_400", "存在错误行，请先修正后重试", 400);
        }
        if (job.getWarningRows() > 0 && !Boolean.TRUE.equals(request.ignoreWarning())) {
            throw new BusinessException("VALID_400", "存在警告行，需确认 ignore_warning=true", 400);
        }

        UserContext user = SecurityUtils.currentUser();
        job.setStatus(STATUS_CONFIRMED);
        job.setConfirmedBy(user.userId());
        job.setConfirmedAt(Instant.now());
        jobRepository.save(job);

        return new ExcelImportConfirmResponse(
                job.getId(),
                job.getBatchId(),
                0,
                false,
                "mock confirm 完成，未写入价格/订单/客户等业务表");
    }

    private ExcelImportJobView toView(ExcelImportJob job, List<ExcelImportRow> rows) {
        List<ExcelImportRowView> rowViews = rows.stream().map(ExcelImportRowView::from).toList();
        return new ExcelImportJobView(
                job.getId(),
                job.getFileId(),
                job.getImportType(),
                job.getBatchId(),
                job.isDryRun(),
                job.getStatus(),
                job.getTotalRows(),
                job.getOkRows(),
                job.getErrorRows(),
                job.getWarningRows(),
                job.getCreatedBy(),
                job.getCreatedAt(),
                job.getConfirmedBy(),
                job.getConfirmedAt(),
                rowViews);
    }

    private ExcelImportJob requireJob(String id) {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        return jobRepository.findByIdAndOperatorIdAndProjectId(id, operatorId, projectId)
                .orElseThrow(() -> new BusinessException("DATA_404", "导入任务不存在", 404));
    }

    private void validateExcelFile(SysFile file) {
        String ext = file.getFileExt() == null ? "" : file.getFileExt().toLowerCase(Locale.ROOT);
        if (!EXCEL_EXT.contains(ext)) {
            throw new BusinessException("FILE_400", "仅支持 xlsx/xls 文件导入", 400);
        }
    }

    private List<ExcelImportRow> buildMockRows(String jobId, String importType) {
        List<MockRow> mocks = "PRICE_RECORD".equalsIgnoreCase(importType)
                ? List.of(
                mock(2, ROW_OK, "{\"sku_code\":\"SKU001\",\"price_date\":\"2026-05-20\",\"price\":\"1250.00\",\"currency\":\"CNY\"}", null, null),
                mock(3, ROW_OK, "{\"sku_code\":\"SKU002\",\"price_date\":\"2026-05-20\",\"price\":\"980.50\",\"currency\":\"CNY\"}", null, null),
                mock(4, ROW_ERROR, "{\"sku_code\":\"\",\"price_date\":\"2026-05-20\",\"price\":\"100\",\"currency\":\"CNY\"}", "SKU 编码不能为空", null),
                mock(5, ROW_WARNING, "{\"sku_code\":\"SKU003\",\"price_date\":\"2026-05-20\",\"price\":\"2100.00\",\"currency\":\"USD\"}", null, "币种与项目默认币种不一致"),
                mock(6, ROW_WARNING, "{\"sku_code\":\"SKU004\",\"price_date\":\"2026-05-19\",\"price\":\"500.00\",\"currency\":\"CNY\"}", null, "价格日期早于最近审批价"))
                : List.of(
                mock(2, ROW_OK, "{\"col_a\":\"value1\",\"col_b\":\"value2\"}", null, null),
                mock(3, ROW_ERROR, "{\"col_a\":\"\",\"col_b\":\"value2\"}", "col_a 不能为空", null),
                mock(4, ROW_WARNING, "{\"col_a\":\"value3\",\"col_b\":\"value4\"}", null, "字段格式疑似异常"));

        List<ExcelImportRow> rows = new ArrayList<>();
        for (MockRow mock : mocks) {
            ExcelImportRow row = new ExcelImportRow();
            row.setId(IdGenerator.nextId());
            row.setJobId(jobId);
            row.setRowNo(mock.rowNo());
            row.setRowStatus(mock.status());
            row.setRowData(mock.data());
            row.setErrorMessage(mock.error());
            row.setWarningMessage(mock.warning());
            rows.add(row);
        }
        return rows;
    }

    private static MockRow mock(int rowNo, String status, String data, String error, String warning) {
        return new MockRow(rowNo, status, data, error, warning);
    }

    private record MockRow(int rowNo, String status, String data, String error, String warning) {
    }
}
