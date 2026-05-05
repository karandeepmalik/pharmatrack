package com.pharma.inventory.dto;

public class ScreenshotDto {

    private String data;
    private String mimeType;

    public ScreenshotDto() {}

    public ScreenshotDto(String data, String mimeType) {
        this.data = data;
        this.mimeType = mimeType;
    }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
}
