package com.scf.clearing.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

public final class ClearingRuleParser {

    private ClearingRuleParser() {
    }

    public static List<String> parsePriorityOrder(ObjectMapper objectMapper, String priorityJson) {
        try {
            JsonNode root = objectMapper.readTree(priorityJson);
            JsonNode priority = root.get("priority");
            if (priority == null || !priority.isArray()) {
                throw new BusinessException("VALID_400", "清分规则 priority_json 格式非法", 400);
            }
            List<String> order = new ArrayList<>();
            priority.forEach(node -> order.add(node.asText()));
            if (order.isEmpty()) {
                throw new BusinessException("VALID_400", "清分规则 priority_json 为空", 400);
            }
            return order;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("VALID_400", "清分规则 priority_json 解析失败", 400);
        }
    }
}
