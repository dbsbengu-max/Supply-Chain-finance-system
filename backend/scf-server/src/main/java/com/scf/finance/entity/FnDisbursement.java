package com.scf.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fn_disbursement", schema = "scf")
public class FnDisbursement {

    @Id
    private String id;

    @Column(name = "finance_id", nullable = false)
    private String financeId;

    @Column(name = "disbursement_no", nullable = false, unique = true)
    private String disbursementNo;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "pay_account_id", nullable = false)
    private String payAccountId;

    @Column(name = "receive_account_id", nullable = false)
    private String receiveAccountId;

    @Column(nullable = false)
    private String channel;

    @Column(name = "channel_request_id")
    private String channelRequestId;

    @Column(name = "channel_response_id")
    private String channelResponseId;

    @Column(name = "disbursement_status", nullable = false)
    private String disbursementStatus;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFinanceId() {
        return financeId;
    }

    public void setFinanceId(String financeId) {
        this.financeId = financeId;
    }

    public String getDisbursementNo() {
        return disbursementNo;
    }

    public void setDisbursementNo(String disbursementNo) {
        this.disbursementNo = disbursementNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPayAccountId() {
        return payAccountId;
    }

    public void setPayAccountId(String payAccountId) {
        this.payAccountId = payAccountId;
    }

    public String getReceiveAccountId() {
        return receiveAccountId;
    }

    public void setReceiveAccountId(String receiveAccountId) {
        this.receiveAccountId = receiveAccountId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getChannelRequestId() {
        return channelRequestId;
    }

    public void setChannelRequestId(String channelRequestId) {
        this.channelRequestId = channelRequestId;
    }

    public String getChannelResponseId() {
        return channelResponseId;
    }

    public void setChannelResponseId(String channelResponseId) {
        this.channelResponseId = channelResponseId;
    }

    public String getDisbursementStatus() {
        return disbursementStatus;
    }

    public void setDisbursementStatus(String disbursementStatus) {
        this.disbursementStatus = disbursementStatus;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
