package com.jitong.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "jitong.seed.demo=false",
        "spring.datasource.url=jdbc:h2:mem:jitong-bootstrap;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductionBootstrapTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("生产模式空库首次启动只创建管理员，不写入演示订单")
    void productionModeCreatesAdminOnly() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("account", "admin", "pin", "Admin@123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("A001"))
                .andExpect(jsonPath("$.user.isAdmin").value(true))
                .andReturn();

        Cookie cookie = login.getResponse().getCookie("jitong_session");
        assertThat(cookie).isNotNull();
        mockMvc.perform(get("/api/bootstrap").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employees.length()").value(1))
                .andExpect(jsonPath("$.jobs.length()").value(0))
                .andExpect(jsonPath("$.records.length()").value(0));
    }
}
