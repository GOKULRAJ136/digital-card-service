package io.mosip.digitalcard.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.digitalcard.constant.ApiName;
import io.mosip.digitalcard.dto.CryptomanagerResponseDto;
import io.mosip.digitalcard.exception.DataEncryptionFailureException;
import io.mosip.kernel.core.http.RequestWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class EncryptionUtilTest {

    @InjectMocks
    private EncryptionUtil encryptionUtil;

    @Mock
    private RestClient restClient;

    @Mock
    private Environment env;

    @Mock
    private ObjectMapper mapper;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testDecryptData_ThrowsDataEncryptionFailureException_For_IOException() throws Exception {
        // Arrange
        String expectedErrorMessage = "Exception while reading packet inputStream";
        when(env.getProperty("mosip.digitalcard.service.datetime.pattern")).thenReturn("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        when(restClient.postApi(any(ApiName.class), any(), anyString(), anyString(), any(MediaType.class), any(RequestWrapper.class), eq(String.class)))
                .thenReturn("a response string"); // The actual content doesn't matter

        // Mock the mapper to throw an IOException, which is the actual source of the catch block
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class))).thenThrow(new IOException("Simulated JSON parsing error"));

        // Act & Assert
        DataEncryptionFailureException exception = assertThrows(DataEncryptionFailureException.class, () -> {
            encryptionUtil.decryptData("some-encrypted-data");
        });

        assertEquals(expectedErrorMessage, exception.getMessage());
    }

    @Test
    public void testDecryptData_ThrowsDataEncryptionFailureException_For_DateTimeParseException() {
        // Arrange
        String expectedErrorMessage = "Error while parsing packet timestamp";
        // This invalid format will cause LocalDateTime.parse to fail
        when(env.getProperty("mosip.digitalcard.service.datetime.pattern")).thenReturn("invalid-date-format");

        // Act & Assert
        DataEncryptionFailureException exception = assertThrows(DataEncryptionFailureException.class, () -> {
            encryptionUtil.decryptData("some-encrypted-data");
        });

        assertEquals(expectedErrorMessage, exception.getMessage());
    }
}
