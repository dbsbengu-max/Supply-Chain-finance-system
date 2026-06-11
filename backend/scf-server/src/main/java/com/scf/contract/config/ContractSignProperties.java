package com.scf.contract.config;

import com.scf.contract.provider.outbound.OutboundSignAuthMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "scf.contract.sign")
public class ContractSignProperties {

    private String defaultProvider = "MOCK";
    private String callbackToken = "mock-contract-sign-callback-token";
    private int maxRetryCount = 3;
    /** TOKEN (current) or TIMESTAMP_NONCE_SIGNATURE (EA-041 planned). */
    private String callbackVerificationMode = "TOKEN";
    private int callbackSignatureWindowSeconds = 300;
    private boolean compensationPoolEnabled = true;
    private HttpProvider httpProvider = new HttpProvider();
    private ProductionRollout productionRollout = new ProductionRollout();

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public String getCallbackToken() {
        return callbackToken;
    }

    public void setCallbackToken(String callbackToken) {
        this.callbackToken = callbackToken;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public String getCallbackVerificationMode() {
        return callbackVerificationMode;
    }

    public void setCallbackVerificationMode(String callbackVerificationMode) {
        this.callbackVerificationMode = callbackVerificationMode;
    }

    public int getCallbackSignatureWindowSeconds() {
        return callbackSignatureWindowSeconds;
    }

    public void setCallbackSignatureWindowSeconds(int callbackSignatureWindowSeconds) {
        this.callbackSignatureWindowSeconds = callbackSignatureWindowSeconds;
    }

    public boolean isCompensationPoolEnabled() {
        return compensationPoolEnabled;
    }

    public void setCompensationPoolEnabled(boolean compensationPoolEnabled) {
        this.compensationPoolEnabled = compensationPoolEnabled;
    }

    public ProductionRollout getProductionRollout() {
        return productionRollout;
    }

    public void setProductionRollout(ProductionRollout productionRollout) {
        this.productionRollout = productionRollout;
    }

    public HttpProvider getHttpProvider() {
        return httpProvider;
    }

    public void setHttpProvider(HttpProvider httpProvider) {
        this.httpProvider = httpProvider;
    }

    public static class ProductionRollout {
        /** OFF | ALLOWLIST | PERCENT | FULL */
        private String mode = "OFF";
        private String productionProvider = "ESIGN_HTTP";
        private String fallbackProvider = "MOCK";
        private String projectAllowlist = "";
        private String operatorAllowlist = "";
        private int projectHashPercent = 0;
        private boolean requireHttpConfigured = true;
        private boolean blockWhenMisconfigured = true;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getProductionProvider() {
            return productionProvider;
        }

        public void setProductionProvider(String productionProvider) {
            this.productionProvider = productionProvider;
        }

        public String getFallbackProvider() {
            return fallbackProvider;
        }

        public void setFallbackProvider(String fallbackProvider) {
            this.fallbackProvider = fallbackProvider;
        }

        public String getProjectAllowlist() {
            return projectAllowlist;
        }

        public void setProjectAllowlist(String projectAllowlist) {
            this.projectAllowlist = projectAllowlist;
        }

        public String getOperatorAllowlist() {
            return operatorAllowlist;
        }

        public void setOperatorAllowlist(String operatorAllowlist) {
            this.operatorAllowlist = operatorAllowlist;
        }

        public int getProjectHashPercent() {
            return projectHashPercent;
        }

        public void setProjectHashPercent(int projectHashPercent) {
            this.projectHashPercent = projectHashPercent;
        }

        public boolean isRequireHttpConfigured() {
            return requireHttpConfigured;
        }

        public void setRequireHttpConfigured(boolean requireHttpConfigured) {
            this.requireHttpConfigured = requireHttpConfigured;
        }

        public boolean isBlockWhenMisconfigured() {
            return blockWhenMisconfigured;
        }

        public void setBlockWhenMisconfigured(boolean blockWhenMisconfigured) {
            this.blockWhenMisconfigured = blockWhenMisconfigured;
        }
    }

    public static class HttpProvider {
        private boolean enabled = false;
        private String providerCode = "ESIGN_HTTP";
        private String displayName = "HTTP 电子签章供应商";
        private String baseUrl = "";
        private String createPath = "/sign/create";
        private String statusPath = "/sign/status/{externalSignRef}";
        private String appId = "";
        private String appSecret = "";
        private String outboundAuthMode = "HMAC_SHA256";
        private String privateKeyPem = "";
        private String publicKeyPem = "";
        private String platformTraceHeader = "X-Request-Id";
        private String vendorRequestIdHeader = "X-Vendor-Request-Id";
        private String vendorTraceIdHeader = "X-Vendor-Trace-Id";
        private String responseExternalRefField = "external_sign_ref";
        private String responseStatusField = "status";
        private String responseProviderStatusField = "provider_status";
        private String responseProviderMessageField = "provider_message";
        private String responseMessageField = "message";
        private String responseSignedAtField = "signed_at";
        private String responseFailureReasonField = "failure_reason";
        private String responseRequestIdField = "request_id";
        private String responseTraceIdField = "trace_id";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 10000;
        private HttpProviderFieldMapping fieldMapping = new HttpProviderFieldMapping();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProviderCode() {
            return providerCode;
        }

        public void setProviderCode(String providerCode) {
            this.providerCode = providerCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCreatePath() {
            return createPath;
        }

        public void setCreatePath(String createPath) {
            this.createPath = createPath;
        }

        public String getStatusPath() {
            return statusPath;
        }

        public void setStatusPath(String statusPath) {
            this.statusPath = statusPath;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getOutboundAuthMode() {
            return outboundAuthMode;
        }

        public void setOutboundAuthMode(String outboundAuthMode) {
            this.outboundAuthMode = outboundAuthMode;
        }

        public String getPrivateKeyPem() {
            return privateKeyPem;
        }

        public void setPrivateKeyPem(String privateKeyPem) {
            this.privateKeyPem = privateKeyPem;
        }

        public String getPublicKeyPem() {
            return publicKeyPem;
        }

        public void setPublicKeyPem(String publicKeyPem) {
            this.publicKeyPem = publicKeyPem;
        }

        public String getPlatformTraceHeader() {
            return platformTraceHeader;
        }

        public void setPlatformTraceHeader(String platformTraceHeader) {
            this.platformTraceHeader = platformTraceHeader;
        }

        public String getVendorRequestIdHeader() {
            return vendorRequestIdHeader;
        }

        public void setVendorRequestIdHeader(String vendorRequestIdHeader) {
            this.vendorRequestIdHeader = vendorRequestIdHeader;
        }

        public String getVendorTraceIdHeader() {
            return vendorTraceIdHeader;
        }

        public void setVendorTraceIdHeader(String vendorTraceIdHeader) {
            this.vendorTraceIdHeader = vendorTraceIdHeader;
        }

        public String getResponseExternalRefField() {
            return responseExternalRefField;
        }

        public void setResponseExternalRefField(String responseExternalRefField) {
            this.responseExternalRefField = responseExternalRefField;
        }

        public String getResponseStatusField() {
            return responseStatusField;
        }

        public void setResponseStatusField(String responseStatusField) {
            this.responseStatusField = responseStatusField;
        }

        public String getResponseProviderStatusField() {
            return responseProviderStatusField;
        }

        public void setResponseProviderStatusField(String responseProviderStatusField) {
            this.responseProviderStatusField = responseProviderStatusField;
        }

        public String getResponseProviderMessageField() {
            return responseProviderMessageField;
        }

        public void setResponseProviderMessageField(String responseProviderMessageField) {
            this.responseProviderMessageField = responseProviderMessageField;
        }

        public String getResponseMessageField() {
            return responseMessageField;
        }

        public void setResponseMessageField(String responseMessageField) {
            this.responseMessageField = responseMessageField;
        }

        public String getResponseSignedAtField() {
            return responseSignedAtField;
        }

        public void setResponseSignedAtField(String responseSignedAtField) {
            this.responseSignedAtField = responseSignedAtField;
        }

        public String getResponseFailureReasonField() {
            return responseFailureReasonField;
        }

        public void setResponseFailureReasonField(String responseFailureReasonField) {
            this.responseFailureReasonField = responseFailureReasonField;
        }

        public String getResponseRequestIdField() {
            return responseRequestIdField;
        }

        public void setResponseRequestIdField(String responseRequestIdField) {
            this.responseRequestIdField = responseRequestIdField;
        }

        public String getResponseTraceIdField() {
            return responseTraceIdField;
        }

        public void setResponseTraceIdField(String responseTraceIdField) {
            this.responseTraceIdField = responseTraceIdField;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public HttpProviderFieldMapping getFieldMapping() {
            return fieldMapping;
        }

        public void setFieldMapping(HttpProviderFieldMapping fieldMapping) {
            this.fieldMapping = fieldMapping;
        }

        public boolean isConfigured() {
            if (baseUrl == null || baseUrl.isBlank() || appId == null || appId.isBlank()) {
                return false;
            }
            OutboundSignAuthMode mode = OutboundSignAuthMode.fromConfig(outboundAuthMode);
            return switch (mode) {
                case HMAC_SHA256 -> appSecret != null && !appSecret.isBlank();
                case RSA_SHA256, SM2 -> privateKeyPem != null && !privateKeyPem.isBlank();
            };
        }
    }

    public static class HttpProviderFieldMapping {
        private String taskId = "task_id";
        private String documentId = "document_id";
        private String fileId = "file_id";
        private String documentNo = "document_no";
        private String businessType = "business_type";
        private String businessId = "business_id";
        private String signers = "signers";
        private String signerEnterpriseId = "enterprise_id";
        private String signerName = "signer_name";
        private String signerRole = "signer_role";

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }

        public String getDocumentNo() {
            return documentNo;
        }

        public void setDocumentNo(String documentNo) {
            this.documentNo = documentNo;
        }

        public String getBusinessType() {
            return businessType;
        }

        public void setBusinessType(String businessType) {
            this.businessType = businessType;
        }

        public String getBusinessId() {
            return businessId;
        }

        public void setBusinessId(String businessId) {
            this.businessId = businessId;
        }

        public String getSigners() {
            return signers;
        }

        public void setSigners(String signers) {
            this.signers = signers;
        }

        public String getSignerEnterpriseId() {
            return signerEnterpriseId;
        }

        public void setSignerEnterpriseId(String signerEnterpriseId) {
            this.signerEnterpriseId = signerEnterpriseId;
        }

        public String getSignerName() {
            return signerName;
        }

        public void setSignerName(String signerName) {
            this.signerName = signerName;
        }

        public String getSignerRole() {
            return signerRole;
        }

        public void setSignerRole(String signerRole) {
            this.signerRole = signerRole;
        }
    }
}
