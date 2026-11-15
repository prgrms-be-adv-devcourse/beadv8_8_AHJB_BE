package com.jjinmak.notification.in;

import com.jjinmak.notification.out.NotificationSettingRepository;
import com.jjinmak.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class NotificationSettingApiTest extends NotificationIntegrationTestSupport {

    private static final Long ME = 7L;

    @Autowired MockMvc mockMvc;
    @Autowired NotificationSettingRepository notificationSettingRepository;

    @AfterEach
    void tearDown() {
        notificationSettingRepository.deleteAllInBatch();
    }

    private MockHttpServletRequestBuilder 로그인(MockHttpServletRequestBuilder request) {
        return request.header("X-Member-Id", ME).header("X-Member-Roles", "ROLE_BUYER");
    }

    private MockHttpServletRequestBuilder 설정_변경(String json) {
        return 로그인(put("/api/v1/notifications/settings"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    @Test
    void 설정한_적이_없으면_계정을_뺀_네_묶음이_모두_켜져_있다() throws Exception {
        mockMvc.perform(로그인(get("/api/v1/notifications/settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings[*].category", contains("BID", "BUY", "SELL", "INTEREST")))
                .andExpect(jsonPath("$.data.settings[*].pushEnabled", everyItem(is(true))));
    }

    @Test
    void 바꾼_묶음만_꺼지고_응답은_네_묶음_전체다() throws Exception {
        mockMvc.perform(설정_변경("""
                        {"settings":[{"category":"BID","pushEnabled":false}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings[*].category", contains("BID", "BUY", "SELL", "INTEREST")))
                .andExpect(jsonPath("$.data.settings[*].pushEnabled", contains(false, true, true, true)));
    }

    @Test
    void 계정_묶음이_섞여_있으면_400이고_아무것도_저장하지_않는다() throws Exception {
        mockMvc.perform(설정_변경("""
                        {"settings":[{"category":"BID","pushEnabled":false},{"category":"ACCOUNT","pushEnabled":false}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION002"));

        assertThat(notificationSettingRepository.count()).isZero();
    }
}
