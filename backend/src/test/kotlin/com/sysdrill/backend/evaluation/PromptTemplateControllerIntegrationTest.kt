package com.sysdrill.backend.evaluation

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class PromptTemplateControllerIntegrationTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `creating a second version and activating it deactivates the first`() {
        val purpose = "test-purpose-${UUID.randomUUID()}"

        val v1Response = mockMvc.perform(
            post("/admin/prompt-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purpose":"$purpose","templateBody":"v1 body"}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.active").value(false))
            .andReturn().response.contentAsString
        val v1Id = JsonPath.read<String>(v1Response, "$.id")

        val v2Response = mockMvc.perform(
            post("/admin/prompt-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purpose":"$purpose","templateBody":"v2 body"}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.version").value(2))
            .andReturn().response.contentAsString
        val v2Id = JsonPath.read<String>(v2Response, "$.id")

        mockMvc.perform(post("/admin/prompt-templates/$v1Id/activate"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.active").value(true))

        mockMvc.perform(post("/admin/prompt-templates/$v2Id/activate"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.active").value(true))

        mockMvc.perform(get("/admin/prompt-templates").param("purpose", purpose))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$v1Id')].active").value(false))
            .andExpect(jsonPath("$[?(@.id == '$v2Id')].active").value(true))
    }
}
