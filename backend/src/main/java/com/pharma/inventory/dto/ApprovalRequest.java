package com.pharma.inventory.dto;

public class ApprovalRequest {
    private boolean approved;
    private String rejectionReason;
    private Integer newPrice;

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
    public Integer getNewPrice() { return newPrice; }
    public void setNewPrice(Integer newPrice) { this.newPrice = newPrice; }
}
