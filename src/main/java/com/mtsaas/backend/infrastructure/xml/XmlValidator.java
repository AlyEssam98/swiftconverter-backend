package com.mtsaas.backend.infrastructure.xml;

import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;

@Component
public class XmlValidator {

    public void validate(String xmlContent, String xsdPath) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
            URL xsdUrl = Thread.currentThread().getContextClassLoader().getResource(xsdPath);
            if (xsdUrl == null) {
                xsdUrl = getClass().getClassLoader().getResource(xsdPath);
            }
            if (xsdUrl == null) {
                throw new IllegalStateException("XSD file not found in classpath: " + xsdPath);
            }
            Schema schema = factory.newSchema(xsdUrl);
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xmlContent)));
        } catch (SAXException | IOException e) {
            throw new RuntimeException("XML Validation failed against " + xsdPath + ": " + e.getMessage(), e);
        }
    }
}
