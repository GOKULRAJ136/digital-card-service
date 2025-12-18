package io.mosip.digitalcard.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.digitalcard.constant.ApiName;
import io.mosip.digitalcard.exception.DataEncryptionFailureException;
import io.mosip.kernel.core.http.RequestWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;

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
    public void testDecryptData_IOException() throws Exception {
        // Arrange
        when(env.getProperty("mosip.digitalcard.service.datetime.pattern")).thenReturn("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        when(restClient.postApi(any(ApiName.class), any(), anyString(), anyString(), any(MediaType.class), any(RequestWrapper.class), eq(String.class)))
                .thenReturn("{ \"response\": { \"data\": \"\" }, \"errors\": null }"); // Malformed JSON

        // Act & Assert
        assertThrows(DataEncryptionFailureException.class, () -> {
            encryptionUtil.decryptData("some-encrypted-data");
        });
    }

    @Test
    public void testDecryptData_DateTimeParseException() {
        // Arrange
        when(env.getProperty("mosip.digitalcard.service.datetime.pattern")).thenReturn("invalid-date-format");

        // Act & Assert
        assertThrows(DataEncryptionFailureException.class, () -> {
            encryptionUtil.decryptData("some-encrypted-data");
        });
    }
}
