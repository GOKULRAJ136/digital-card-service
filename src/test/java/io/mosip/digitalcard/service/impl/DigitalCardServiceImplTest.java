package io.mosip.digitalcard.service.impl;

import com.fasterxml.jackson.core.JsonParseException;
import io.mosip.digitalcard.exception.DigitalCardServiceException;
import io.mosip.digitalcard.repositories.DigitalCardTransactionRepository;
import io.mosip.digitalcard.service.CardGeneratorService;
import io.mosip.digitalcard.util.EncryptionUtil;
import io.mosip.kernel.core.pdfgenerator.exception.PDFGeneratorException;
import io.mosip.kernel.core.qrcodegenerator.exception.QrcodeGenerationException;
import io.mosip.vercred.CredentialsVerifier;
import org.json.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject as SimpleJSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class DigitalCardServiceImplTest {

    @InjectMocks
    private DigitalCardServiceImpl digitalCardService;

    @Mock
    private CardGeneratorService pdfCardServiceImpl;

    @Mock
    private EncryptionUtil encryptionUtil;

    @Mock
    private CredentialsVerifier credentialsVerifier;

    @Mock
    private DigitalCardTransactionRepository digitalCardTransactionRepository;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    // Previous tests for getPassword
    @Test
    public void testGetPasswordWithSimpleAttributes() throws Exception {
        ReflectionTestUtils.setField(digitalCardService, "digitalCardPassword", "name|dob");
        ReflectionTestUtils.setField(digitalCardService, "templateLang", "en");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", "John");
        jsonObject.put("dob", "1990-01-01");

        String password = ReflectionTestUtils.invokeMethod(digitalCardService, "getPassword", jsonObject);
        assertEquals("JOHN1990", password);
    }

    // New tests for generateDigitalCard exception paths
    private String getMockDecryptedCredential() {
        JSONObject credentialSubject = new JSONObject();
        credentialSubject.put("id", "12345/credentials/67890");

        JSONObject credential = new JSONObject();
        credential.put("credentialSubject", credentialSubject);
        return credential.toString();
    }

    @Test
    public void testGenerateDigitalCard_QrcodeGenerationException() throws Exception {
        // Arrange
        when(encryptionUtil.decryptData(anyString())).thenReturn(getMockDecryptedCredential());
        when(pdfCardServiceImpl.generateCard(any(), anyString(), any(), any()))
                .thenThrow(new QrcodeGenerationException("QR_CODE_ERROR", "QR Code generation failed"));

        // Act
        digitalCardService.generateDigitalCard("credential", "type", null, "event1", "txn1", new HashMap<>());

        // Assert
        verify(digitalCardTransactionRepository, times(1)).updateErrorTransactionDetails(anyString(), eq("ERROR"), anyString(), any(), any());
    }

    @Test
    public void testGenerateDigitalCard_PDFGeneratorException() throws Exception {
        // Arrange
        when(encryptionUtil.decryptData(anyString())).thenReturn(getMockDecryptedCredential());
        when(pdfCardServiceImpl.generateCard(any(), anyString(), any(), any()))
                .thenThrow(new PDFGeneratorException("PDF_ERROR", "PDF generation failed"));

        // Act
        digitalCardService.generateDigitalCard("credential", "type", null, "event1", "txn1", new HashMap<>());

        // Assert
        verify(digitalCardTransactionRepository, times(1)).updateErrorTransactionDetails(anyString(), eq("ERROR"), anyString(), any(), any());
    }

    @Test
    public void testGenerateDigitalCard_JsonParseException() throws Exception {
        // Arrange
        when(encryptionUtil.decryptData(anyString())).thenReturn(getMockDecryptedCredential());
        when(pdfCardServiceImpl.generateCard(any(), anyString(), any(), any()))
                .thenThrow(new JsonParseException(null, "JSON parsing failed"));

        // Act
        digitalCardService.generateDigitalCard("credential", "type", null, "event1", "txn1", new HashMap<>());

        // Assert
        verify(digital-CTAR, times(1)).updateErrorTransactionDetails(anyString(), eq("ERROR"), anyString(), any(), any());
    }

    @Test
    public void testGenerateDigitalCard_GenericException_VCVerificationFailed() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(digitalCardService, "verifyCredentialsFlag", true);
        when(encryptionUtil.decryptData(anyString())).thenReturn(getMockDecryptedCredential());
        when(credentialsVerifier.verifyCredentials(anyString())).thenReturn(false);

        // Act & Assert
        assertThrows(DigitalCardServiceException.class, () -> {
            digitalCardService.generateDigitalCard("credential", "type", null, "event1", "txn1", new HashMap<>());
        });

        // Also verify the error details are logged
        verify(digitalCardTransactionRepository, times(1)).updateErrorTransactionDetails(anyString(), eq("ERROR"), anyString(), any(), any());
    }
}
