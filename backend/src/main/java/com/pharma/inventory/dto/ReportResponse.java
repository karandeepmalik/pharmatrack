package com.pharma.inventory.dto;

public class ReportResponse {
    private String reportType;
    private String generatedAt;
    private String content;

    public ReportResponse(String reportType, String generatedAt, String content) {
        this.reportType = reportType;
        this.generatedAt = generatedAt;
        this.content = content;
    }

    public String getReportType() { return reportType; }
    public String getGeneratedAt() { return generatedAt; }
    public String getContent() { return content; }
}
