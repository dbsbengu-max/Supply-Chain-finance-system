package com.scf.contract.provider.outbound;

import org.springframework.http.HttpHeaders;

public interface OutboundSignAuthStrategy {

    OutboundSignAuthMode mode();

    void apply(HttpHeaders headers, OutboundSignAuthContext context, String requestBody);
}
