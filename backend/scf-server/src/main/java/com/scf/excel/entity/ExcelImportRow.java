package com.scf.excel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "excel_import_row", schema = "scf")
public class ExcelImportRow {

    @Id
    private String id;

    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @Column(name = "row_status", nullable = false)
    private String rowStatus;

    @Column(name = "row_data", nullable = false)
    private String rowData;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "warning_message")
    private String warningMessage;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public int getRowNo() { return rowNo; }
    public void setRowNo(int rowNo) { this.rowNo = rowNo; }
    public String getRowStatus() { return rowStatus; }
    public void setRowStatus(String rowStatus) { this.rowStatus = rowStatus; }
    public String getRowData() { return rowData; }
    public void setRowData(String rowData) { this.rowData = rowData; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getWarningMessage() { return warningMessage; }
    public void setWarningMessage(String warningMessage) { this.warningMessage = warningMessage; }
}
