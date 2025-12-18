package io.mosip.digitalcard.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.biometrics.util.face.FaceDecoder;
import io.mosip.digitalcard.exception.IdentityNotFoundException;
import io.mosip.digitalcard.util.CbeffToBiometricUtil;
import io.mosip.digitalcard.util.TemplateGenerator;
import io.mosip.digitalcard.util.Utility;
import io.mosip.kernel.biometrics.spi.CbeffUtil;
import io.mosip.kernel.core.pdfgenerator.spi.PDFGenerator;
import io.mosip.kernel.core.qrcodegenerator.exception.QrcodeGenerationException;
import io.mosip.kernel.core.qrcodegenerator.spi.QrCodeGenerator;
import io.mosip.kernel.qrcode.generator.zxing.constant.QrVersion;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PDFCardServiceImplTest {

    @InjectMocks
    private PDFCardServiceImpl pdfCardService;

    @Mock
    private Utility utility;

    @Mock
    private CbeffUtil cbeffUtil;

    @Mock
    private QrCodeGenerator<QrVersion> qrCodeGenerator;

    @Mock
    private TemplateGenerator templateGenerator;

    @Mock
    private PDFGenerator pdfGenerator;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(pdfCardService, "supportedLang", "en,fr");
        ReflectionTestUtils.setField(pdfCardService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(pdfCardService, "defaultTemplateTypeCode", "RPR_UIN_CARD_TEMPLATE");
        ReflectionTestUtils.setField(pdfCardService, "cbeffutil", cbeffUtil);
    }

    private JSONObject createMapperIdentity() {
        JSONObject mapperIdentity = new JSONObject();
        mapperIdentity.put("fullName", new LinkedHashMap<>() {{ put("value", "name"); }});
        mapperIdentity.put("gender", new LinkedHashMap<>() {{ put("value", "gender"); }});
        mapperIdentity.put("dateOfBirth", new LinkedHashMap<>() {{ put("value", "dob"); }});
        mapperIdentity.put("address", new LinkedHashMap<>() {{ put("value", "address"); }});
        return mapperIdentity;
    }

    // Tests for setTemplateAttributes from previous request

    @Test
    public void testSetTemplateAttributes_Success() throws Exception {
        // Arrange
        org.json.JSONObject demographicIdentity = new org.json.JSONObject();
        demographicIdentity.put("name", "John Doe");
        demographicIdentity.put("dob", "1990-01-01");
        demographicIdentity.put("gender", new JSONArray() {{
            add(new JSONObject() {{ put("language", "en"); put("value", "Male"); }});
            add(new JSONObject() {{ put("language", "fr"); put("value", "Homme"); }});
            add(new JSONObject() {{ put("language", "es"); put("value", "Masculino"); }});
        }});
        demographicIdentity.put("address", new JSONObject() {{ put("value", "123 Main St"); }});


        JSONObject mapperIdentity = createMapperIdentity();

        when(utility.getIdentityMappingJson(any(), any())).thenReturn("{}");
        when(utility.getDemographicIdentity()).thenReturn("demographicIdentity");
        when(utility.getJSONObject(any(), anyString())).thenReturn(mapperIdentity);

        when(utility.getJSONValue(mapperIdentity, "fullName")).thenReturn(new LinkedHashMap<>() {{ put("value", "name"); }});
        when(utility.getJSONValue(mapperIdentity, "gender")).thenReturn(new LinkedHashMap<>() {{ put("value", "gender"); }});
        when(utility.getJSONValue(mapperIdentity, "dateOfBirth")).thenReturn(new LinkedHashMap<>() {{ put("value", "dob"); }});
        when(utility.getJSONValue(mapperIdentity, "address")).thenReturn(new LinkedHashMap<>() {{ put("value", "address"); }});


        Map<String, Object> attributes = new HashMap<>();

        // Act
        ReflectionTestUtils.invokeMethod(pdfCardService, "setTemplateAttributes", demographicIdentity, attributes);

        // Assert
        assertEquals("John Doe", attributes.get("name"));
        assertEquals("1990-01-01", attributes.get("dob"));
        assertEquals("Male", attributes.get("gender_en"));
        assertEquals("Homme", attributes.get("gender_fr"));
        assertNull(attributes.get("gender_es")); // Unsupported language
        assertEquals("123 Main St", attributes.get("address"));
    }

    @Test
    public void testSetTemplateAttributes_NullIdentity() {
        // Arrange
        Map<String, Object> attributes = new HashMap<>();

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            ReflectionTestUtils.invokeMethod(pdfCardService, "setTemplateAttributes", null, attributes);
        });
        assertTrue(exception.getCause() instanceof IdentityNotFoundException);
    }

    @Test
    public void testSetTemplateAttributes_MissingAttribute() throws Exception {
        // Arrange
        org.json.JSONObject demographicIdentity = new org.json.JSONObject();
        demographicIdentity.put("name", "John Doe");
        // dob is missing

        JSONObject mapperIdentity = createMapperIdentity();

        when(utility.getIdentityMappingJson(any(), any())).thenReturn("{}");
        when(utility.getDemographicIdentity()).thenReturn("demographicIdentity");
        when(utility.getJSONObject(any(), anyString())).thenReturn(mapperIdentity);
        when(utility.getJSONValue(mapperIdentity, "fullName")).thenReturn(new LinkedHashMap<>() {{ put("value", "name"); }});
        when(utility.getJSONValue(mapperIdentity, "dateOfBirth")).thenReturn(new LinkedHashMap<>() {{ put("value", "dob"); }});


        Map<String, Object> attributes = new HashMap<>();

        // Act
        ReflectionTestUtils.invokeMethod(pdfCardService, "setTemplateAttributes", demographicIdentity, attributes);

        // Assert
        assertEquals("John Doe", attributes.get("name"));
        assertFalse(attributes.containsKey("dob"));
    }


    // New tests for setApplicantPhoto and generateCard

    @Test
    public void testSetApplicantPhoto_Success() throws Exception {
        // Arrange
        Map<String, Object> attributes = new HashMap<>();
        String individualBio = "cbeff-data-with-face";
        byte[] photoBytes = "photo-bytes".getBytes();

        // Mocking the static method call in a simplistic way
        try (var mocked = mockStatic(FaceDecoder.class)) {
            mocked.when(() -> FaceDecoder.convertFaceISOToImageBytes(any())).thenReturn("image-bytes".getBytes());

            CbeffToBiometricUtil cbeffToBiometricUtil = mock(CbeffToBiometricUtil.class);
            whenNew(CbeffToBiometricUtil.class).withAnyArguments().thenReturn(cbeffToBiometricUtil);
            when(cbeffToBiometricUtil.getImageBytes(anyString(), eq("Face"), anyList())).thenReturn(photoBytes);

            // Act
            boolean result = ReflectionTestUtils.invokeMethod(pdfCardService, "setApplicantPhoto", individualBio, attributes);

            // Assert
            assertTrue(result);
            assertTrue(attributes.containsKey("ApplicantPhoto"));
        }
    }

    @Test
    public void testGenerateCard_Success_FullCard() throws Exception {
        // Arrange
        org.json.JSONObject credential = new org.json.JSONObject();
        credential.put("UIN", "1234567890");
        credential.put("biometrics", "cbeff-data-with-face");

        byte[] qrCodeBytes = "qr-code".getBytes();
        InputStream templateStream = new ByteArrayInputStream("template".getBytes());
        byte[] pdfBytes = "pdf-bytes".getBytes();

        when(qrCodeGenerator.generateQrCode(anyString(), any(QrVersion.class))).thenReturn(qrCodeBytes);
        when(templateGenerator.getTemplate(anyString(), anyMap(), anyString())).thenReturn(templateStream);

        // Mock internal calls to other private methods
        PDFCardServiceImpl spy = spy(pdfCardService);
        doReturn(true).when(spy).setApplicantPhoto(anyString(), anyMap());
        doNothing().when(spy).setTemplateAttributes(any(org.json.JSONObject.class), anyMap());
        doReturn(pdfBytes).when(spy).generateUinCard(any(InputStream.class), any());

        // Act
        byte[] result = spy.generateCard(credential, "digitalcard", "password", new HashMap<>());

        // Assert
        assertNotNull(result);
        assertEquals(pdfBytes, result);
        verify(spy, times(1)).setApplicantPhoto(anyString(), anyMap());
        verify(spy, times(1)).setTemplateAttributes(any(org.json.JSONObject.class), anyMap());
        verify(qrCodeGenerator, times(1)).generateQrCode(anyString(), any(QrVersion.class));
        verify(templateGenerator, times(1)).getTemplate(anyString(), anyMap(), anyString());
    }

    @Test
    public void testGenerateCard_QrcodeGenerationException() throws Exception {
        // Arrange
        org.json.JSONObject credential = new org.json.JSONObject();
        credential.put("UIN", "1234567890");
        credential.put("biometrics", "cbeff-data-with-face");

        when(qrCodeGenerator.generateQrCode(anyString(), any(QrVersion.class))).thenThrow(new QrcodeGenerationException("QR_ERR", "QR Error"));

        PDFCardServiceImpl spy = spy(pdfCardService);
        doReturn(true).when(spy).setApplicantPhoto(anyString(), anyMap());
        doNothing().when(spy).setTemplateAttributes(any(org.json.JSONObject.class), anyMap());


        // Act & Assert
        assertThrows(QrcodeGenerationException.class, () -> {
            spy.generateCard(credential, "digitalcard", "password", new HashMap<>());
        });
    }
}
