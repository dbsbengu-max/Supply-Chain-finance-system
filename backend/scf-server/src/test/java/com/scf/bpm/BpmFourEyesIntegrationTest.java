package com.scf.bpm;

import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BpmFourEyesIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetFixtures() {
        jdbcTemplate.update("DELETE FROM scf.bpm_task WHERE id LIKE 'BPM4E_%'");
        jdbcTemplate.update("DELETE FROM scf.bpm_process_instance WHERE id LIKE 'BPM4E_%'");
        insertProcess("BPM4E_PROC_SELF_APPROVE", "U001");
        insertTask("BPM4E_TASK_SELF_APPROVE", "BPM4E_PROC_SELF_APPROVE", "U001");
        insertProcess("BPM4E_PROC_SELF_REJECT", "U001");
        insertTask("BPM4E_TASK_SELF_REJECT", "BPM4E_PROC_SELF_REJECT", "U001");
        insertProcess("BPM4E_PROC_OTHER_APPROVE", "U003");
        insertTask("BPM4E_TASK_OTHER_APPROVE", "BPM4E_PROC_OTHER_APPROVE", "U001");
    }

    @Test
    void starterCannotApproveOwnProcess() throws Exception {
        mvc.perform(post("/bpm/tasks/BPM4E_TASK_SELF_APPROVE/approve")
                        .headers(headers(platformAdminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"self approve should fail\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BPM_FOUR_EYES_409"));
    }

    @Test
    void starterCannotRejectOwnProcess() throws Exception {
        mvc.perform(post("/bpm/tasks/BPM4E_TASK_SELF_REJECT/reject")
                        .headers(headers(platformAdminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"self reject should fail\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BPM_FOUR_EYES_409"));
    }

    @Test
    void differentAssigneeCanApproveProcess() throws Exception {
        mvc.perform(post("/bpm/tasks/BPM4E_TASK_OTHER_APPROVE/approve")
                        .headers(headers(platformAdminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"approved by another user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));
    }

    private void insertProcess(String id, String startedBy) {
        jdbcTemplate.update("""
                INSERT INTO scf.bpm_process_instance (
                  id, process_code, business_type, business_id, process_status, started_by, started_at
                ) VALUES (?, 'BPM_FOUR_EYES_TEST', 'BPM_FOUR_EYES_TEST', ?, 'RUNNING', ?, now())
                """, id, id, startedBy);
    }

    private void insertTask(String id, String processId, String assigneeId) {
        jdbcTemplate.update("""
                INSERT INTO scf.bpm_task (
                  id, process_instance_id, business_type, business_id, node_code, assignee_id, approval_status, submitted_at
                ) VALUES (?, ?, 'BPM_FOUR_EYES_TEST', ?, 'FIRST_APPROVAL', ?, 'PENDING', now())
                """, id, processId, processId, assigneeId);
    }

    private org.springframework.http.HttpHeaders headers(String token) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", "REQ-BPM-FOUR-EYES");
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        return headers;
    }

    private String platformAdminToken() {
        return jwtService.generateToken(new UserContext(
                "U001", "platform_admin", OPERATOR_ID, PROJECT_ID, "ENT_CORE_001", "ROLE_PLATFORM_ADMIN", "ID001"));
    }
}
