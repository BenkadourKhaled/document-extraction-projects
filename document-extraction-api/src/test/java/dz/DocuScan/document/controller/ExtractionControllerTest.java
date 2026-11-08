package dz.DocuScan.document.controller;

import dz.DocuScan.document.config.ExtractionProperties;
import dz.DocuScan.document.exception.DocumentException;
import dz.DocuScan.document.exception.GlobalExceptionHandler;
import dz.DocuScan.document.service.ExtractionExportService;
import dz.DocuScan.document.service.ExtractionService;
import dz.DocuScan.document.security.JwtAuthenticationFilter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ExtractionController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ExtractionControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ExtractionService service;
    @MockBean ExtractionExportService exports;
    @MockBean ExtractionProperties properties;

    @Test
    void rejectsEmptyUpload() throws Exception {
        var file = new MockMultipartFile("file", "empty.pdf",
                MediaType.APPLICATION_PDF_VALUE, new byte[0]);
        mvc.perform(multipart("/api/v1/extractions").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTooLargeUpload() throws Exception {
        when(properties.maxFileSizeBytes()).thenReturn(4L);
        var file = new MockMultipartFile("file", "big.pdf",
                MediaType.APPLICATION_PDF_VALUE, new byte[10]);

        mvc.perform(multipart("/api/v1/extractions").file(file))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void rejectsUnsupportedMimeType() throws Exception {
        when(properties.maxFileSizeBytes()).thenReturn(1000L);
        when(service.create(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DocumentException(415, "Type MIME non pris en charge."));
        var file = new MockMultipartFile("file", "notes.txt",
                MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());

        mvc.perform(multipart("/api/v1/extractions").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("Type MIME non pris en charge."));
    }

    @Test
    void returnsProblemDetailWhenExtractionIsMissing() throws Exception {
        when(service.get(any(), any()))
                .thenThrow(new DocumentException(404, "Extraction introuvable ou expiree."));

        mvc.perform(get("/api/v1/extractions/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Extraction introuvable ou expiree."));
    }
}
