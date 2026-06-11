package com.scf.saga.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.agencypurchase.entity.ApAgencyPurchaseApplication;
import com.scf.agencypurchase.repository.ApAgencyPurchaseApplicationRepository;
import com.scf.contract.entity.TrContractSignTask;
import com.scf.contract.repository.TrContractSignTaskRepository;
import com.scf.saga.dto.SagaOpsDtos.CompensationImpactView;
import com.scf.saga.entity.BizCompensationTask;
import com.scf.trade.repository.TrOrderRepository;
import org.springframework.stereotype.Component;

@Component
public class CompensationImpactResolver {

    private final ApAgencyPurchaseApplicationRepository applicationRepository;
    private final TrOrderRepository orderRepository;
    private final TrContractSignTaskRepository contractSignTaskRepository;
    private final ObjectMapper objectMapper;

    public CompensationImpactResolver(
            ApAgencyPurchaseApplicationRepository applicationRepository,
            TrOrderRepository orderRepository,
            TrContractSignTaskRepository contractSignTaskRepository,
            ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.orderRepository = orderRepository;
        this.contractSignTaskRepository = contractSignTaskRepository;
        this.objectMapper = objectMapper;
    }

    public CompensationImpactView resolve(BizCompensationTask task) {
        if ("CONTRACT_SIGN_CALLBACK".equals(task.getBusinessType())) {
            return buildContractSignImpact(task);
        }
        if (!"AGENCY_PURCHASE".equals(task.getBusinessType())) {
            return emptyImpact(suggestedAction(task.getCompensationType()));
        }
        return applicationRepository.findById(task.getBusinessId())
                .map(app -> buildAgencyImpact(task, app))
                .orElse(emptyImpact(suggestedAction(task.getCompensationType())));
    }

    private CompensationImpactView buildContractSignImpact(BizCompensationTask task) {
        String externalSignRef = task.getBusinessId();
        String providerCode = null;
        String reasonCode = null;
        try {
            JsonNode payload = objectMapper.readTree(task.getActionJson());
            if (payload.hasNonNull("external_sign_ref")) {
                externalSignRef = payload.get("external_sign_ref").asText();
            }
            if (payload.hasNonNull("provider_code")) {
                providerCode = payload.get("provider_code").asText();
            }
            if (payload.hasNonNull("reason_code")) {
                reasonCode = payload.get("reason_code").asText();
            }
        } catch (Exception ignored) {
            // keep defaults from task
        }
        TrContractSignTask signTask = contractSignTaskRepository
                .findFirstByExternalSignRefOrderByCreatedAtDesc(externalSignRef)
                .orElse(null);
        if (signTask != null) {
            providerCode = signTask.getProviderCode();
        }
        return new CompensationImpactView(
                null,
                null,
                null,
                null,
                null,
                signTask != null ? signTask.getDocumentId() : null,
                externalSignRef,
                providerCode,
                signTask != null ? signTask.getTaskStatus() : null,
                suggestedContractSignAction(reasonCode));
    }

    private CompensationImpactView buildAgencyImpact(BizCompensationTask task, ApAgencyPurchaseApplication app) {
        String orderStatus = null;
        if (app.getOrderId() != null) {
            orderStatus = orderRepository.findById(app.getOrderId())
                    .map(o -> o.getOrderStatus())
                    .orElse(null);
        }
        String inventoryId = app.getInventoryId();
        String marginAccountId = app.getMarginAccountId();
        String financeId = app.getFinanceApplicationId();
        try {
            JsonNode payload = objectMapper.readTree(task.getActionJson());
            if (payload.hasNonNull("order_id")) {
                orderStatus = orderRepository.findById(payload.get("order_id").asText())
                        .map(o -> o.getOrderStatus())
                        .orElse(orderStatus);
            }
            if (payload.hasNonNull("inventory_id")) {
                inventoryId = payload.get("inventory_id").asText();
            }
            if (payload.hasNonNull("account_id")) {
                marginAccountId = payload.get("account_id").asText();
            }
        } catch (Exception ignored) {
            // keep defaults from application
        }
        return new CompensationImpactView(
                app.getOrderId(),
                orderStatus,
                financeId,
                inventoryId,
                marginAccountId,
                null,
                null,
                null,
                null,
                suggestedAction(task.getCompensationType()));
    }

    private static CompensationImpactView emptyImpact(String suggestedAction) {
        return new CompensationImpactView(null, null, null, null, null, null, null, null, null, suggestedAction);
    }

    private static String suggestedAction(String compensationType) {
        return switch (compensationType) {
            case CompensationTypes.ORDER_ROLLBACK -> "将已确认订单回退至 SUBMITTED，需四眼审批后执行";
            case CompensationTypes.MARGIN_UNFREEZE -> "解冻代采冻结保证金，确认账户余额与冻结金额一致";
            case CompensationTypes.INVENTORY_UNFREEZE -> "释放代采冻结库存，核对可用/冻结数量";
            case CompensationTypes.CONTRACT_SIGN_CALLBACK_REVIEW ->
                    "核对签章回调与单证状态，必要时在单证中心重试或忽略无效回调";
            default -> "联系平台运营确认补偿策略";
        };
    }

    private static String suggestedContractSignAction(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return suggestedAction(CompensationTypes.CONTRACT_SIGN_CALLBACK_REVIEW);
        }
        return switch (reasonCode) {
            case "DATA_404" -> "外部签章单号未匹配本地任务：核对供应商单号或在单证中心重新发起签署";
            case "STATE_409" -> "回调与本地签署状态冲突：核对乱序/重复回调，必要时忽略或人工推进状态";
            case "VALID_400" -> "回调字段不合法：联系供应商修正 payload 后重放或忽略";
            default -> suggestedAction(CompensationTypes.CONTRACT_SIGN_CALLBACK_REVIEW);
        };
    }
}
