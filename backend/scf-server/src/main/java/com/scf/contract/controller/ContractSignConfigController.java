package com.scf.contract.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.contract.dto.ContractSignDtos.ContractSignConfigView;
import com.scf.contract.dto.ContractSignDtos.ContractSignProviderView;
import com.scf.contract.service.ContractSignConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/integrations/contracts/sign")
public class ContractSignConfigController {

    private final ContractSignConfigService configService;

    public ContractSignConfigController(ContractSignConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/config")
    public ApiResponse<ContractSignConfigView> getConfig(HttpServletRequest request) {
        return ApiResponse.ok(configService.getConfig(), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/providers")
    public ApiResponse<List<ContractSignProviderView>> listProviders(HttpServletRequest request) {
        return ApiResponse.ok(configService.listProviders(), request.getHeader("X-Request-Id"));
    }
}
