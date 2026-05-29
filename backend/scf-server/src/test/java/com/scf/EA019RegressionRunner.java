package com.scf;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class EA019RegressionRunner {
    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                        DiscoverySelectors.selectClass("com.scf.bi.BiDashboardIntegrationTest"),
                        DiscoverySelectors.selectClass("com.scf.bpm.BpmFourEyesIntegrationTest"),
                        DiscoverySelectors.selectClass("com.scf.account.ClearingRuleIntegrationTest"),
                        DiscoverySelectors.selectClass("com.scf.finance.FinanceDisburseIntegrationTest"),
                        DiscoverySelectors.selectClass("com.scf.risk.RiskAlertCenterIntegrationTest"),
                        DiscoverySelectors.selectClass("com.scf.inbox.InboxCenterIntegrationTest"),
                        DiscoverySelectors.selectClass("com.scf.audit.AuditCenterIntegrationTest"),
                        DiscoverySelectors.selectClass("com.scf.voucher.VoucherIntegrationTest"),
                        DiscoverySelectors.selectClass("com.scf.voucher.VoucherRepaymentReleaseIntegrationTest"),
                        DiscoverySelectors.selectClass("com.scf.security.PermissionPenetrationTest"))
                .build();
        Launcher launcher = LauncherFactory.create();
        CountingListener listener = new CountingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        System.out.printf("RESULT tests=%d succeeded=%d failed=%d skipped=%d%n",
                listener.tests.get(), listener.succeeded.get(), listener.failed.get(), listener.skipped.get());
        if (listener.failed.get() > 0) {
            System.exit(1);
        }
        System.exit(0);
    }

    private static final class CountingListener implements TestExecutionListener {
        private final AtomicInteger tests = new AtomicInteger();
        private final AtomicInteger succeeded = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private final AtomicInteger skipped = new AtomicInteger();

        @Override
        public void testPlanExecutionStarted(TestPlan testPlan) {
            System.out.printf("DISCOVERED tests=%d%n", testPlan.countTestIdentifiers(TestIdentifier::isTest));
        }

        @Override
        public void executionStarted(TestIdentifier testIdentifier) {
            if (testIdentifier.isTest()) {
                tests.incrementAndGet();
                System.out.println("START " + testIdentifier.getDisplayName());
            }
        }

        @Override
        public void executionSkipped(TestIdentifier testIdentifier, String reason) {
            if (testIdentifier.isTest()) {
                skipped.incrementAndGet();
                System.out.println("SKIP " + testIdentifier.getDisplayName() + " " + reason);
            }
        }

        @Override
        public void executionFinished(TestIdentifier testIdentifier, org.junit.platform.engine.TestExecutionResult result) {
            if (!testIdentifier.isTest()) {
                return;
            }
            switch (result.getStatus()) {
                case SUCCESSFUL -> {
                    succeeded.incrementAndGet();
                    System.out.println("PASS " + testIdentifier.getDisplayName());
                }
                case FAILED, ABORTED -> {
                    failed.incrementAndGet();
                    System.out.println("FAIL " + testIdentifier.getDisplayName());
                    result.getThrowable().ifPresent(t -> t.printStackTrace(System.out));
                }
            }
        }
    }
}
