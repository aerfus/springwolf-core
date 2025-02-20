// SPDX-License-Identifier: Apache-2.0
package io.github.stavshamir.springwolf.asyncapi.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.stavshamir.springwolf.asyncapi.scanners.channels.payload.PayloadClassExtractor;
import io.github.stavshamir.springwolf.configuration.properties.SpringwolfConfigProperties;
import io.github.stavshamir.springwolf.producer.SpringwolfJmsProducer;
import io.github.stavshamir.springwolf.schemas.DefaultSchemasService;
import io.github.stavshamir.springwolf.schemas.SchemasService;
import io.github.stavshamir.springwolf.schemas.example.ExampleJsonGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpringwolfJmsController.class)
@ContextConfiguration(
        classes = {
            SpringwolfJmsController.class,
            PublishingPayloadCreator.class,
            SpringwolfJmsProducer.class,
            PayloadClassExtractor.class,
            DefaultSchemasService.class,
            ExampleJsonGenerator.class,
            SpringwolfConfigProperties.class,
        })
@TestPropertySource(
        properties = {
            "springwolf.docket.base-package=io.github.stavshamir.springwolf.asyncapi",
            "springwolf.docket.info.title=Title",
            "springwolf.docket.info.version=1.0",
            "springwolf.docket.servers.jms.protocol=jms",
            "springwolf.docket.servers.jms.url=127.0.0.1",
            "springwolf.plugin.jms.publishing.enabled=true",
            "springwolf.use-fqn=true"
        })
class SpringwolfJmsControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SchemasService schemasService;

    @MockBean
    private SpringwolfJmsProducer springwolfJmsProducer;

    @Captor
    private ArgumentCaptor<PayloadDto> payloadCaptor;

    @Captor
    private ArgumentCaptor<Map<String, String>> headerCaptor;

    @BeforeEach
    void setup() {
        when(springwolfJmsProducer.isEnabled()).thenReturn(true);

        schemasService.register(PayloadDto.class);
    }

    @Test
    void testControllerShouldReturnBadRequestIfPayloadIsEmpty() {
        try {
            String content =
                    """
            {
              "bindings": null,
              "headers": null,
              "payload": ""
            }""";
            mvc.perform(post("/springwolf/jms/publish?topic=test-topic")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(content))
                    .andExpect(status().isBadRequest());
        } catch (Exception e) {
            verifyNoInteractions(springwolfJmsProducer);
        }
    }

    @Test
    void testControllerShouldReturnBadRequestIfPayloadIsNotSet() {
        try {
            String content =
                    """
            {
              "bindings": null,
              "headers": null
            }""";
            mvc.perform(post("/springwolf/jms/publish?topic=test-topic")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(content))
                    .andExpect(status().isBadRequest());
        } catch (Exception e) {
            verifyNoInteractions(springwolfJmsProducer);
        }
    }

    @Test
    void testControllerShouldReturnNotFoundIfNoJmsProducerIsEnabled() throws Exception {
        when(springwolfJmsProducer.isEnabled()).thenReturn(false);

        String content =
                """
                {
                  "bindings": null,
                  "headers": null,
                  "payload": "{ \\"some-payload-key\\" : \\"some-payload-value\\" }"
                }""";
        mvc.perform(post("/springwolf/jms/publish?topic=test-topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isNotFound());
    }

    @Test
    void testControllerShouldCallJmsProducerIfOnlyPayloadIsSend() throws Exception {
        when(springwolfJmsProducer.isEnabled()).thenReturn(true);

        String content =
                """
                {
                  "bindings": null,
                  "headers": null,
                  "payload": "{ \\"some-payload-key\\" : \\"some-payload-value\\" }",
                  "payloadType": "io.github.stavshamir.springwolf.asyncapi.controller.SpringwolfJmsControllerIntegrationTest$PayloadDto"
                }""";

        mvc.perform(post("/springwolf/jms/publish")
                        .param("topic", "test-topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk());

        verify(springwolfJmsProducer).send(eq("test-topic"), isNull(), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).isEqualTo(new PayloadDto("some-payload-value"));
    }

    @Test
    void testControllerShouldCallJmsProducerIfPayloadAndHeadersAreSend() throws Exception {
        when(springwolfJmsProducer.isEnabled()).thenReturn(true);

        String content =
                """
                {
                  "bindings": null,
                  "headers": {
                    "some-header-key": "some-header-value"
                  },
                  "payload": "{ \\"some-payload-key\\" : \\"some-payload-value\\" }",
                  "payloadType": "io.github.stavshamir.springwolf.asyncapi.controller.SpringwolfJmsControllerIntegrationTest$PayloadDto"
                }
                """;

        mvc.perform(post("/springwolf/jms/publish?topic=test-topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk());

        verify(springwolfJmsProducer).send(eq("test-topic"), headerCaptor.capture(), payloadCaptor.capture());

        assertThat(headerCaptor.getValue()).isEqualTo(singletonMap("some-header-key", "some-header-value"));
        assertThat(payloadCaptor.getValue()).isEqualTo(new PayloadDto("some-payload-value"));
    }

    @Data
    @AllArgsConstructor
    @Jacksonized
    @Builder
    public static class PayloadDto {
        @JsonProperty("some-payload-key")
        private String field;
    }
}
