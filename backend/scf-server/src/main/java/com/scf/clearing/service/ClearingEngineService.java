package com.scf.clearing.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClearingEngineService {

    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("360");

    public record OutstandingBuckets(
            BigDecimal penalty,
            BigDecimal fee,
            BigDecimal interest,
            BigDecimal principal,
            BigDecimal platformFee) {
    }

    public record AllocationResult(
            Map<String, BigDecimal> buckets,
            List<String> warnings) {
    }

    /**
     * 按规则优先级分配还款金额到罚息、费用、利息、本金、平台服务费、剩余金额。
     */
    public AllocationResult allocate(
            BigDecimal repaymentAmount,
            List<String> priorityOrder,
            OutstandingBuckets outstanding) {
        Map<String, BigDecimal> allocation = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        BigDecimal remaining = scale(repaymentAmount);

        for (String bucket : priorityOrder) {
            allocation.put(bucket, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        if (!allocation.containsKey("residual")) {
            allocation.put("residual", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        for (String bucket : priorityOrder) {
            if ("residual".equals(bucket)) {
                continue;
            }
            BigDecimal owed = outstandingFor(bucket, outstanding);
            BigDecimal paid = remaining.min(owed);
            allocation.put(bucket, paid);
            remaining = remaining.subtract(paid);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                remaining = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                break;
            }
        }

        allocation.put("residual", remaining);
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            warnings.add("还款金额超出当前应还总额，剩余 " + remaining.toPlainString());
        }
        return new AllocationResult(allocation, warnings);
    }

    public BigDecimal estimateInterest(BigDecimal principal, BigDecimal annualRate, int termDays) {
        if (principal.compareTo(BigDecimal.ZERO) <= 0 || termDays <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return principal
                .multiply(annualRate)
                .multiply(new BigDecimal(termDays))
                .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal outstandingFor(String bucket, OutstandingBuckets outstanding) {
        return switch (bucket) {
            case "penalty" -> scale(outstanding.penalty());
            case "fee" -> scale(outstanding.fee());
            case "interest" -> scale(outstanding.interest());
            case "principal" -> scale(outstanding.principal());
            case "platform_fee" -> scale(outstanding.platformFee());
            default -> BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        };
    }

    private static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** @deprecated 保留兼容，请使用 {@link #allocate} */
    @Deprecated
    public Map<String, BigDecimal> calculateTrial(BigDecimal repaymentAmount, List<String> priorityOrder) {
        OutstandingBuckets empty = new OutstandingBuckets(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        return allocate(repaymentAmount, priorityOrder, empty).buckets();
    }
}
