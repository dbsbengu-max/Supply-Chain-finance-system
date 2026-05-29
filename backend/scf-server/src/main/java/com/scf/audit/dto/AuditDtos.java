package com.scf.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditLogView(
            String id,
            String action,
            String action_label,
            String object_type,
            String object_type_label,
            String object_id,
            String user_id,
            String user_name,
            String enterprise_id,
            String project_id,
            String ip_address,
            Instant operation_at,
            String related_route) {
    }

    public record AuditLogDetailView(
            String id,
            String action,
            String action_label,
            String object_type,
            String object_type_label,
            String object_id,
            String user_id,
            String user_name,
            String enterprise_id,
            String project_id,
            String ip_address,
            Instant operation_at,
            String before_value,
            String after_value,
            String related_route) {
    }

    public record AuditSummaryItemView(String object_type, String object_type_label, long count) {
    }

    public record AuditSummaryView(long total, List<AuditSummaryItemView> by_object_type) {
    }

    public record AuditFilterMetaView(List<String> actions, List<String> object_types) {
    }

    public record AuditObjectTypeCount(String object_type, long count) {
    }

    public static Map<String, String> actionLabels() {
        return Map.ofEntries(
                Map.entry("ORDER_CREATE", "创建订单"),
                Map.entry("ORDER_UPDATE", "更新订单"),
                Map.entry("ORDER_SUBMIT", "提交订单"),
                Map.entry("ORDER_CONFIRM", "确认订单"),
                Map.entry("FINANCE_CREATE", "创建融资"),
                Map.entry("FINANCE_SUBMIT", "提交融资"),
                Map.entry("FINANCE_APPROVE", "审批融资"),
                Map.entry("FINANCE_DISBURSE", "发起放款"),
                Map.entry("FINANCE_DISBURSE_CALLBACK", "放款回调成功"),
                Map.entry("FINANCE_DISBURSE_CALLBACK_FAILED", "放款回调失败"),
                Map.entry("CLEARING_RULE_CREATE", "创建清分规则"),
                Map.entry("CLEARING_RULE_UPDATE", "更新清分规则"),
                Map.entry("CLEARING_RULE_SUBMIT", "提交清分规则"),
                Map.entry("CLEARING_RULE_APPROVE", "审批清分规则"),
                Map.entry("CLEARING_EXECUTE", "执行清分"),
                Map.entry("BANK_FLOW_MATCH", "流水匹配"),
                Map.entry("BANK_FLOW_UNMATCH", "流水取消匹配"),
                Map.entry("CUSTOMER_CREATE", "创建客户"),
                Map.entry("KYC_SUBMIT", "提交 KYC"),
                Map.entry("KYC_APPROVE", "KYC 通过"),
                Map.entry("KYC_REJECT", "KYC 驳回"),
                Map.entry("PROJECT_CREATE", "创建项目"),
                Map.entry("PRICE_CREATE", "创建价格"),
                Map.entry("PRICE_APPROVE", "审批价格"),
                Map.entry("WAREHOUSE_INBOUND", "仓储入库"),
                Map.entry("WAREHOUSE_FREEZE", "库存冻结"),
                Map.entry("WAREHOUSE_PLEDGE", "库存质押"),
                Map.entry("WAREHOUSE_RELEASE_APPLY", "解押申请"),
                Map.entry("WAREHOUSE_RELEASE_APPROVE", "解押审批"),
                Map.entry("WAREHOUSE_OUTBOUND_APPLY", "出库申请"),
                Map.entry("WAREHOUSE_OUTBOUND_CONFIRM", "出库确认"),
                Map.entry("AGENCY_PURCHASE_CREATE", "创建代采"),
                Map.entry("AGENCY_PURCHASE_UPDATE", "更新代采"),
                Map.entry("AGENCY_PURCHASE_SUBMIT", "提交代采"),
                Map.entry("AGENCY_PURCHASE_CANCEL", "取消代采"),
                Map.entry("BPM_START", "启动流程"),
                Map.entry("BPM_APPROVE", "审批通过"),
                Map.entry("BPM_REJECT", "审批驳回"),
                Map.entry("HANDLE", "风险处理"),
                Map.entry("RISK_ALERT_CLAIM", "风险认领"),
                Map.entry("VOUCHER_CREATE", "凭证开立"),
                Map.entry("VOUCHER_ISSUE", "凭证签发/质押"),
                Map.entry("VOUCHER_SPLIT", "凭证拆分"),
                Map.entry("TRANSFER", "凭证流转"),
                Map.entry("REDEEM_APPLY", "兑付申请"),
                Map.entry("VOUCHER_RELEASE", "凭证融资释放"),
                Map.entry("SAGA_COMPENSATE", "Saga 补偿"));
    }

    public static Map<String, String> objectTypeLabels() {
        return Map.ofEntries(
                Map.entry("TRADE_ORDER", "贸易订单"),
                Map.entry("FINANCE_APPLICATION", "融资申请"),
                Map.entry("CLEARING_RULE", "清分规则"),
                Map.entry("CLEARING_EXECUTION", "清分执行"),
                Map.entry("BANK_FLOW", "银行流水"),
                Map.entry("ENTERPRISE", "企业/KYC"),
                Map.entry("PROJECT", "项目"),
                Map.entry("PRICE", "价格"),
                Map.entry("INVENTORY", "库存货权"),
                Map.entry("AGENCY_PURCHASE", "贸易代采"),
                Map.entry("BPM_PROCESS", "BPM 流程"),
                Map.entry("BPM_TASK", "BPM 任务"),
                Map.entry("RISK_ALERT", "风险预警"),
                Map.entry("VOUCHER", "数字债权凭证"));
    }
}
