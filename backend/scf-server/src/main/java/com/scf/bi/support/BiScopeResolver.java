package com.scf.bi.support;

import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import org.springframework.stereotype.Component;

@Component
public class BiScopeResolver {

    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;

    public BiScopeResolver(TenantContext tenantContext, DataScopeHelper dataScopeHelper) {
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
    }

    public BiQueryScope requireScope() {
        tenantContext.requirePermission("BI_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        UserContext user = SecurityUtils.currentUser();
        if (user == null) {
            throw new BusinessException("AUTH_401", "未登录", 401);
        }

        if (dataScopeHelper.canReadOperatorData(user) || dataScopeHelper.isFundingRole(user)) {
            if (dataScopeHelper.isFundingRole(user)) {
                return new BiQueryScope(
                        operatorId,
                        projectId,
                        null,
                        null,
                        user.enterpriseId(),
                        null,
                        null);
            }
            return BiQueryScope.operatorProject(operatorId, projectId);
        }

        if (dataScopeHelper.isEnterpriseRole(user)) {
            return new BiQueryScope(
                    operatorId,
                    projectId,
                    user.enterpriseId(),
                    user.enterpriseId(),
                    null,
                    user.enterpriseId(),
                    null);
        }

        if (dataScopeHelper.isWarehouseRole(user)) {
            return new BiQueryScope(
                    operatorId,
                    projectId,
                    null,
                    null,
                    null,
                    null,
                    user.enterpriseId());
        }

        throw new BusinessException("AUTH_403", "无 BI 数据范围", 403);
    }
}
