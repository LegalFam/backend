package com.legalfam.backend.conversation;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.auth.AdminAccessService;
import com.legalfam.backend.conversation.dto.FileSearchStoreResponse;
import com.legalfam.backend.conversation.dto.FileSearchUploadResponse;
import com.legalfam.backend.conversation.gemini.GeminiFileSearchUploadClient;
import com.legalfam.backend.error.handler.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AdminFileSearchControllerTest {

    @Mock
    private AdminAccessService adminAccessService;

    @Mock
    private GeminiFileSearchUploadClient geminiFileSearchUploadClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminFileSearchController(adminAccessService, geminiFileSearchUploadClient)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadReturnsBadRequestWhenFileMissing() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/v1/admin/file-search/upload")
                        .file(emptyFile)
                        .principal(new UsernamePasswordAuthenticationToken("admin@example.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("File is required")));
    }

    @Test
    void uploadReturnsOperationData() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                "hello".getBytes()
        );
        when(geminiFileSearchUploadClient.uploadDocument(file, "sample-doc"))
                .thenReturn(new FileSearchUploadResponse("operations/123", true, "fileSearchStores/s/documents/d1"));

        mockMvc.perform(multipart("/api/v1/admin/file-search/upload")
                        .file(file)
                        .param("displayName", "sample-doc")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "admin@example.com",
                                null,
                                AuthorityUtils.NO_AUTHORITIES
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationName", is("operations/123")))
                .andExpect(jsonPath("$.done", is(true)))
                .andExpect(jsonPath("$.documentName", is("fileSearchStores/s/documents/d1")));

        verify(adminAccessService).requireAdmin(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void uploadReturnsForbiddenForNonAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", "hello".getBytes());
        doThrow(new AccessDeniedException("forbidden"))
                .when(adminAccessService)
                .requireAdmin(org.mockito.ArgumentMatchers.any());

        mockMvc.perform(multipart("/api/v1/admin/file-search/upload")
                        .file(file)
                        .principal(new UsernamePasswordAuthenticationToken("user@example.com", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", is("authorization_error")))
                .andExpect(jsonPath("$.code", is("forbidden")))
                .andExpect(jsonPath("$.message", is("Access is forbidden")));
    }

    @Test
    void listStoresReturnsStores() throws Exception {
        when(geminiFileSearchUploadClient.listStores()).thenReturn(List.of(
                new FileSearchStoreResponse(
                        "fileSearchStores/legal",
                        "Legal",
                        "2026-04-17T00:00:00Z",
                        "2026-04-17T00:00:00Z"
                )
        ));

        mockMvc.perform(get("/api/v1/admin/file-search/stores")
                        .principal(new UsernamePasswordAuthenticationToken("admin@example.com", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("fileSearchStores/legal")))
                .andExpect(jsonPath("$[0].displayName", is("Legal")));
    }

    @Test
    void listStoresReturnsForbiddenForNonAdmin() throws Exception {
        doThrow(new AccessDeniedException("forbidden"))
                .when(adminAccessService)
                .requireAdmin(org.mockito.ArgumentMatchers.any());

        mockMvc.perform(get("/api/v1/admin/file-search/stores")
                        .principal(new UsernamePasswordAuthenticationToken("user@example.com", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", is("authorization_error")))
                .andExpect(jsonPath("$.code", is("forbidden")))
                .andExpect(jsonPath("$.message", is("Access is forbidden")));
    }
}
