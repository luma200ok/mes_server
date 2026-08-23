package com.mes.global.exception;

import com.mes.global.config.SecurityConfig;
import com.mes.global.security.JwtAuthenticationFilter;
import com.mes.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 매핑되지 않은 경로(정적 리소스 없는 API 전용 서버) 요청이
 * 포괄 Exception 핸들러(500)가 아니라 404 + C005 로 응답하는지 고정하는 테스트.
 *
 * 실제 운영에서 GET / 이 NoResourceFoundException 으로 인해 500(C002) 을 반환하던
 * 버그(#16)의 회귀 방지 테스트.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerNotFoundTest.NoopController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class GlobalExceptionHandlerNotFoundTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("permitAll 경로(GET /)가 매핑되지 않았을 때 500이 아니라 404 + C005를 반환한다")
    void root_unmapped_returns404NotFound() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C005"))
                .andExpect(jsonPath("$.message").value("요청한 경로를 찾을 수 없습니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("인증된 사용자가 존재하지 않는 임의 경로를 요청하면 401/403이 아니라 404 + C005를 반환한다")
    void authenticatedArbitraryPath_unmapped_returns404NotFound() throws Exception {
        mockMvc.perform(get("/이런거없음"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C005"));
    }

    @RestController
    static class NoopController {
        @GetMapping("/__noop_probe__")
        String noop() {
            return "ok";
        }
    }
}
