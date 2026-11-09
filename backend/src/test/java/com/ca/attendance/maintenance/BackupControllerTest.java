package com.ca.attendance.maintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupControllerTest {
    @TempDir
    Path tempDirectory;

    @Test void restoreConsentIsExplicitAndBoundToTheReviewedFile() throws Exception {
        BackupService service = mock(BackupService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new BackupController(service)).build();
        var file = new org.springframework.mock.web.MockMultipartFile("file", "backup.zip", "application/zip", new byte[]{1});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/maintenance/backups/restore").file(file))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(service).restore(file, false, "");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/maintenance/backups/restore").file(file)
                        .param("clearInvalidContacts", "true").param("reviewHash", "reviewed-file-hash"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(service).restore(file, true, "reviewed-file-hash");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/maintenance/backups/restore/preview").file(file))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(service).reviewRestore(file);
    }

    @Test
    void downloadsLargeBackupsAsAFileResourceInsteadOfABufferedByteArray() throws Exception {
        String filename = "backup_20260821_120000_001.zip";
        Path backup = tempDirectory.resolve(filename);
        Files.write(backup, new byte[8 * 1024 * 1024]);
        BackupService service = mock(BackupService.class);
        when(service.download(filename)).thenReturn(
                new BackupService.BackupFile(filename, Files.size(backup), backup)
        );

        ResponseEntity<Resource> response = new BackupController(service).download(filename);

        FileSystemResource resource = assertInstanceOf(FileSystemResource.class, response.getBody());
        assertEquals(Files.size(backup), response.getHeaders().getContentLength());
        assertEquals(Files.size(backup), resource.contentLength());
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        org.junit.jupiter.api.Assertions.assertNotNull(disposition);
        org.junit.jupiter.api.Assertions.assertTrue(disposition.contains("filename=\"" + filename + "\""));
        verify(service).download(filename);
    }
}
