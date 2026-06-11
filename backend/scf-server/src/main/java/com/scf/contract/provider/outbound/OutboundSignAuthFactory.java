package com.scf.contract.provider.outbound;

import com.scf.contract.config.ContractSignProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class OutboundSignAuthFactory {

    private final Map<OutboundSignAuthMode, OutboundSignAuthStrategy> strategies;

    public OutboundSignAuthFactory(List<OutboundSignAuthStrategy> strategyList) {
        this.strategies = new EnumMap<>(OutboundSignAuthMode.class);
        for (OutboundSignAuthStrategy strategy : strategyList) {
            strategies.put(strategy.mode(), strategy);
        }
    }

    public OutboundSignAuthStrategy require(ContractSignProperties.HttpProvider config) {
        OutboundSignAuthMode mode = OutboundSignAuthMode.fromConfig(config.getOutboundAuthMode());
        OutboundSignAuthStrategy strategy = strategies.get(mode);
        if (strategy == null) {
            throw new IllegalStateException("Unsupported outbound auth mode: " + mode);
        }
        return strategy;
    }
}
