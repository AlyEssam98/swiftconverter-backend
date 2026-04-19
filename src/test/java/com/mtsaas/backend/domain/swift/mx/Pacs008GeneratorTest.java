package com.mtsaas.backend.domain.swift.mx;

import com.mtsaas.backend.domain.swift.mt.MtMessage;
import com.mtsaas.backend.domain.swift.mt.MtParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class Pacs008GeneratorTest {

    @Test
    public void testMt103ToPacs008Conversion() {
        String mt103 = "{1:F01CIBCCATTFXXX0000000000}\n" +
                "{2:1103PNBPUS3NXNYCN}{3:{111:001}{121:509c1365-20db-4a6f-8fd9-230d4ec55c53}}{4:\n" +
                ":20:603519206047\n" +
                ":23B:CRED\n" +
                ":32A:260206USD20000,\n" +
                ":50K:/040078216436\n" +
                "DEANNA GRENKOW\n" +
                "1601 BRYN MAWR AVE\n" +
                "LAS VEGAS NV US\n" +
                "89102-4450\n" +
                ":52D:CIBC\n" +
                "TORONTO ON CA\n" +
                ":53B:/2000293950570\n" +
                ":57D://FW322484265\n" +
                "SILVER STATE SCHOOLS CREDIT UNION\n" +
                ":59:/1800000139840\n" +
                "RICHARD GRENKOW\n" +
                "1601 BRYN MAWR AVE\n" +
                "US LAS VEGAS\n" +
                ":70:..ISCASH..\n" +
                ":71A:OUR\n" +
                "{5:{CHK:D9BEBCE8F872}}";

        MtParser parser = new MtParser();
        MtMessage mtMessage = parser.parse(mt103);

        Pacs008Generator generator = new Pacs008Generator();
        // Since we don't have XmlValidator in this context, we skip the validation stage if it's null
        String xml = generator.generateXml(mtMessage);

        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/test_output.xml"), xml);
        } catch (Exception e) {}

        System.out.println("Generated XML:\n" + xml);

        // Verify key requirements
        assertTrue(xml.contains("<To>"), "Missing AppHdr/To");
        assertTrue(xml.contains("<BICFI>PNBPUS3NNYC</BICFI>") || xml.contains("<BICFI>PNBPUS3NXXX</BICFI>"), 
                   "Receiver BIC should be PNBPUS3NNYC or PNBPUS3NXXX");
        assertTrue(xml.contains("<ChrgBr>DEBT</ChrgBr>"), "Charge Bearer should be DEBT for OUR");
        assertTrue(xml.contains("<Cd>FW</Cd>"), "Should contain Fedwire code");
        assertTrue(xml.contains("<MmbId>322484265</MmbId>"), "Should contain Fedwire Member ID");
        assertTrue(xml.contains("<Ctry>US</Ctry>"), "Creditor country should be US");
        assertFalse(xml.contains("<CharSet>"), "Should not contain CharSet");
        
        // Verify no American Samoa false positive
        assertFalse(xml.contains("<Ctry>AS</Ctry>"), "Should not have American Samoa as country");
    }
}
