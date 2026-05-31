package com.scf.agencypurchase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.agencypurchase.AgencyPurchaseModeCatalog;

import java.util.List;

public record AgencyPurchaseMetaView(
        @JsonProperty("order_modes") List<AgencyPurchaseModeCatalog.DictItem> orderModes,
        @JsonProperty("fund_sources") List<AgencyPurchaseModeCatalog.DictItem> fundSources,
        @JsonProperty("pickup_types") List<AgencyPurchaseModeCatalog.DictItem> pickupTypes,
        @JsonProperty("application_statuses") List<AgencyPurchaseModeCatalog.DictItem> applicationStatuses,
        @JsonProperty("saga_statuses") List<AgencyPurchaseModeCatalog.DictItem> sagaStatuses,
        @JsonProperty("valid_modes") List<AgencyPurchaseModeCatalog.ModeDefinition> validModes,
        @JsonProperty("cross_domain_actions") List<CrossDomainAction> crossDomainActions
) {
    public record CrossDomainAction(String code, String label, String hint) {
    }

    public static AgencyPurchaseMetaView defaults() {
        return new AgencyPurchaseMetaView(
                AgencyPurchaseModeCatalog.orderModes(),
                AgencyPurchaseModeCatalog.fundSources(),
                AgencyPurchaseModeCatalog.pickupTypes(),
                AgencyPurchaseModeCatalog.applicationStatuses(),
                AgencyPurchaseModeCatalog.sagaStatuses(),
                AgencyPurchaseModeCatalog.allModes(),
                List.of(
                        new CrossDomainAction("PAY", "登记/发起付款", "待 Saga/资金模块接入"),
                        new CrossDomainAction("REDEEM", "打款赎单", "待 Saga/资金模块接入"),
                        new CrossDomainAction("PICKUP", "提货申请", "待 Saga/仓储模块接入"),
                        new CrossDomainAction("FINANCE", "发起融资", "待 Saga/融资模块接入"),
                        new CrossDomainAction("CLEARING", "清分执行", "待 Saga/清分模块接入"),
                        new CrossDomainAction("RELEASE", "货权释放", "待 Saga/仓储模块接入")));
    }
}
