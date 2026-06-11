package com.scf.contract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public final class ContractSignDtos {

    private ContractSignDtos() {
    }

    public record ContractSignInitiateRequest(
            @JsonProperty("provider_code") String providerCode,
            @JsonProperty("signers") List<ContractSignerRequest> signers,
            @JsonProperty("simulate_failure") Boolean simulateFailure) {
    }

    public record ContractSignerRequest(
            @NotBlank @JsonProperty("enterprise_id") String enterpriseId,
            @JsonProperty("signer_name") String signerName,
            @JsonProperty("signer_role") String signerRole) {
    }

    public record ContractSignTaskView(
            @JsonProperty("id") String id,
            @JsonProperty("document_id") String documentId,
            @JsonProperty("provider_code") String providerCode,
            @JsonProperty("external_sign_ref") String externalSignRef,
            @JsonProperty("task_status") String taskStatus,
            @JsonProperty("callback_status") String callbackStatus,
            @JsonProperty("failure_reason") String failureReason,
            @JsonProperty("retry_count") int retryCount,
            @JsonProperty("last_retry_at") Instant lastRetryAt,
            @JsonProperty("signed_at") Instant signedAt,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("platform_trace_id") String platformTraceId,
            @JsonProperty("provider_request_id") String providerRequestId,
            @JsonProperty("provider_trace_id") String providerTraceId) {
    }

    public record ContractSignInitiateResponse(
            @JsonProperty("document_id") String documentId,
            @JsonProperty("sign_status") String signStatus,
            @JsonProperty("contract_status") String contractStatus,
            @JsonProperty("sign_provider") String signProvider,
            @JsonProperty("external_sign_ref") String externalSignRef,
            @JsonProperty("task") ContractSignTaskView task) {
    }

    public record ContractSignCallbackRequest(
            @NotBlank @JsonProperty("external_sign_ref") String externalSignRef,
            @NotBlank @JsonProperty("callback_status") String callbackStatus,
            @JsonProperty("signed_at") Instant signedAt,
            @JsonProperty("failure_reason") String failureReason,
            @JsonProperty("provider_code") String providerCode) {
    }

    public record ContractSignCallbackResponse(
            @JsonProperty("document_id") String documentId,
            @JsonProperty("sign_status") String signStatus,
            @JsonProperty("contract_status") String contractStatus,
            @JsonProperty("external_sign_ref") String externalSignRef,
            @JsonProperty("task_status") String taskStatus,
            @JsonProperty("idempotent_replay") Boolean idempotentReplay) {

        public ContractSignCallbackResponse withIdempotentReplay(boolean replay) {
            return new ContractSignCallbackResponse(
                    documentId, signStatus, contractStatus, externalSignRef, taskStatus, replay);
        }
    }

    public record ContractSignProviderView(
            @JsonProperty("provider_code") String providerCode,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("description") String description,
            @JsonProperty("supports_status_query") boolean supportsStatusQuery) {
    }

    public record ContractSignProviderConnectionView(
            @JsonProperty("provider_code") String providerCode,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("configured") boolean configured,
            @JsonProperty("outbound_auth_mode") String outboundAuthMode,
            @JsonProperty("platform_trace_header") String platformTraceHeader,
            @JsonProperty("base_url") String baseUrl,
            @JsonProperty("app_id") String appId,
            @JsonProperty("app_secret_masked") String appSecretMasked) {
    }

    public record ContractSignConfigView(
            @JsonProperty("default_provider") String defaultProvider,
            @JsonProperty("max_retry_count") int maxRetryCount,
            @JsonProperty("callback_verification_mode") String callbackVerificationMode,
            @JsonProperty("callback_signature_window_seconds") int callbackSignatureWindowSeconds,
            @JsonProperty("callback_token_masked") String callbackTokenMasked,
            @JsonProperty("callback_path") String callbackPath,
            @JsonProperty("callback_headers") List<String> callbackHeaders,
            @JsonProperty("planned_callback_headers") List<String> plannedCallbackHeaders,
            @JsonProperty("compensation_pool_enabled") boolean compensationPoolEnabled,
            @JsonProperty("provider_connections") List<ContractSignProviderConnectionView> providerConnections,
            @JsonProperty("production_rollout") ContractSignRolloutView productionRollout) {
    }

    public record ContractSignRolloutView(
            @JsonProperty("mode") String mode,
            @JsonProperty("production_provider") String productionProvider,
            @JsonProperty("fallback_provider") String fallbackProvider,
            @JsonProperty("project_allowlist") String projectAllowlist,
            @JsonProperty("operator_allowlist") String operatorAllowlist,
            @JsonProperty("project_hash_percent") int projectHashPercent,
            @JsonProperty("require_http_configured") boolean requireHttpConfigured,
            @JsonProperty("block_when_misconfigured") boolean blockWhenMisconfigured,
            @JsonProperty("effective_provider_for_context") String effectiveProviderForContext,
            @JsonProperty("routed_to_production") boolean routedToProduction) {
    }

    public record ContractSignDocumentSummary(
            @JsonProperty("document_id") String documentId,
            @JsonProperty("document_no") String documentNo,
            @JsonProperty("sign_status") String signStatus,
            @JsonProperty("contract_status") String contractStatus,
            @JsonProperty("review_status") String reviewStatus) {
    }

    public record ContractSignLookupView(
            @JsonProperty("external_sign_ref") String externalSignRef,
            @JsonProperty("task") ContractSignTaskView task,
            @JsonProperty("document") ContractSignDocumentSummary document) {
    }

    public record ContractSignProviderStatusView(
            @JsonProperty("external_sign_ref") String externalSignRef,
            @JsonProperty("provider_code") String providerCode,
            @JsonProperty("provider_status") String providerStatus,
            @JsonProperty("signed_at") Instant signedAt,
            @JsonProperty("failure_reason") String failureReason,
            @JsonProperty("supports_status_query") boolean supportsStatusQuery) {
    }

    public record ContractSignStatusQueryView(
            @JsonProperty("external_sign_ref") String externalSignRef,
            @JsonProperty("provider") ContractSignProviderStatusView provider,
            @JsonProperty("local_task") ContractSignTaskView localTask,
            @JsonProperty("document") ContractSignDocumentSummary document,
            @JsonProperty("reconciled") boolean reconciled,
            @JsonProperty("reconcile_action") String reconcileAction,
            @JsonProperty("message") String message) {
    }

    public record ContractSignStatusQueryRequest(
            @JsonProperty("reconcile") Boolean reconcile,
            @JsonProperty("reason") String reason) {
    }
}
