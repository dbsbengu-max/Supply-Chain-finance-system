package com.scf.common.security;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DataScopeHelper {

    private static final Set<String> PLATFORM_ROLES = Set.of(
            "ROLE_PLATFORM_ADMIN",
            "ROLE_PLATFORM_OPERATOR"
    );

    private static final Set<String> ENTERPRISE_ROLES = Set.of(
            "ROLE_MEMBER",
            "ROLE_CORE_ENTERPRISE",
            "ROLE_TRADE_COMPANY"
    );

    private static final Set<String> FUNDING_ROLES = Set.of(
            "ROLE_FUNDING",
            "ROLE_GUARANTEE_PARTY"
    );

    private static final Set<String> OPERATOR_READ_ROLES = Set.of(
            "ROLE_AUDIT_VIEWER"
    );

    public boolean isPlatformRole(UserContext user) {
        return user != null && PLATFORM_ROLES.contains(user.roleId());
    }

    public boolean isEnterpriseRole(UserContext user) {
        return user != null && ENTERPRISE_ROLES.contains(user.roleId());
    }

    public boolean isFundingRole(UserContext user) {
        return user != null && FUNDING_ROLES.contains(user.roleId());
    }

    public boolean canReadOperatorData(UserContext user) {
        return isPlatformRole(user) || (user != null && OPERATOR_READ_ROLES.contains(user.roleId()));
    }

    public boolean canAccessEnterprise(UserContext user, String enterpriseId) {
        if (canReadOperatorData(user) || isFundingRole(user)) {
            return true;
        }
        return isSameEnterprise(user, enterpriseId);
    }

    public boolean canAccessTradeOrder(
            UserContext user,
            String buyerId,
            String sellerId,
            String tradeCompanyId) {
        if (canReadOperatorData(user) || isFundingRole(user) || isWarehouseRole(user)) {
            return true;
        }
        return isSameEnterprise(user, buyerId)
                || isSameEnterprise(user, sellerId)
                || isSameEnterprise(user, tradeCompanyId);
    }

    public boolean canAccessFinance(UserContext user, String customerId, String fundingPartyId) {
        if (canReadOperatorData(user)) {
            return true;
        }
        if (isFundingRole(user)) {
            return isSameEnterprise(user, fundingPartyId);
        }
        if (isEnterpriseRole(user)) {
            return isSameEnterprise(user, customerId);
        }
        return false;
    }

    public ScopeType financeScope(UserContext user) {
        if (canReadOperatorData(user)) {
            return ScopeType.OPERATOR_PROJECT;
        }
        if (isFundingRole(user)) {
            return ScopeType.FUNDING_PARTY;
        }
        if (isEnterpriseRole(user)) {
            return ScopeType.ENTERPRISE;
        }
        return ScopeType.NONE;
    }

    public ScopeType tradeOrderScope(UserContext user) {
        if (canReadOperatorData(user) || isFundingRole(user) || isWarehouseRole(user)) {
            return ScopeType.OPERATOR_PROJECT;
        }
        if (isEnterpriseRole(user)) {
            return ScopeType.ENTERPRISE;
        }
        return ScopeType.NONE;
    }

    public ScopeType customerScope(UserContext user) {
        if (canReadOperatorData(user) || isFundingRole(user)) {
            return ScopeType.OPERATOR;
        }
        if (isEnterpriseRole(user) || isWarehouseRole(user)) {
            return ScopeType.ENTERPRISE;
        }
        return ScopeType.NONE;
    }

    public boolean isWarehouseRole(UserContext user) {
        return user != null && "ROLE_WAREHOUSE".equals(user.roleId());
    }

    public ScopeType warehouseInventoryScope(UserContext user) {
        if (canReadOperatorData(user) || isFundingRole(user)) {
            return ScopeType.OPERATOR_PROJECT;
        }
        if (isWarehouseRole(user)) {
            return ScopeType.WAREHOUSE_COMPANY;
        }
        if (isEnterpriseRole(user)) {
            return ScopeType.ENTERPRISE;
        }
        return ScopeType.NONE;
    }

    private boolean isSameEnterprise(UserContext user, String enterpriseId) {
        return user != null
                && user.enterpriseId() != null
                && enterpriseId != null
                && user.enterpriseId().equals(enterpriseId);
    }

    public enum ScopeType {
        OPERATOR,
        OPERATOR_PROJECT,
        ENTERPRISE,
        FUNDING_PARTY,
        WAREHOUSE_COMPANY,
        NONE
    }
}
