package pl.stojak.miniiam.auth.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void keepsValidCorrelationIdFromRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "abc-123_XYZ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("abc-123_XYZ");
        assertThat(mdcDuringRequest.get()).isEqualTo("abc-123_XYZ");
    }

    @Test
    void generatesUuidWhenHeaderMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/test"), response, new MockFilterChain());

        String generated = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(generated).isNotBlank();
        assertThatNoException().isThrownBy(() -> UUID.fromString(generated));
    }

    @Test
    void replacesInvalidHeaderValue() {
        assertThat(CorrelationIdFilter.resolveCorrelationId("bad value; drop table"))
                .isNotEqualTo("bad value; drop table");
        assertThat(CorrelationIdFilter.resolveCorrelationId("x".repeat(101))).hasSize(36);
        assertThat(CorrelationIdFilter.resolveCorrelationId("")).hasSize(36);
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/test"), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
