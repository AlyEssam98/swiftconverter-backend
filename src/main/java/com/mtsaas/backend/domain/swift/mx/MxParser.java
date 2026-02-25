package com.mtsaas.backend.domain.swift.mx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ISO 20022 MX XML messages into MxMessage objects.
 */
@Component
public class MxParser {

    private static final Logger log = LoggerFactory.getLogger(MxParser.class);

    // Pattern to extract message type from namespace or MsgDefIdr
    private static final Pattern MSG_DEF_PATTERN = Pattern.compile("(pacs|camt|head)\\.(\\d{3})\\.\\d{3}\\.\\d{2}");

    public MxMessage parse(String xmlContent) {
        MxMessage mxMessage = new MxMessage();
        mxMessage.setRawXml(xmlContent);
        mxMessage.setFields(new HashMap<>());

        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            mxMessage.setMessageType("Unknown");
            return mxMessage;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes("UTF-8")));

            // Extract message type from Document element namespace
            Element documentElement = doc.getDocumentElement();
            String namespace = documentElement.getAttribute("xmlns");
            if (namespace == null || namespace.isEmpty()) {
                // Try to find xmlns on Document element
                namespace = documentElement.getAttributeNS(null, "xmlns");
            }

            if (namespace != null && !namespace.isEmpty()) {
                Matcher matcher = Pattern.compile("xmlns=\"urn:iso:std:iso:20022:tech:xsd:([^\"]+)\"").matcher(xmlContent);
                if (matcher.find()) {
                    mxMessage.setMessageType(matcher.group(1));
                } else {
                    // Extract from namespace URI
                    String[] parts = namespace.split(":");
                    if (parts.length > 0) {
                        mxMessage.setMessageType(parts[parts.length - 1]);
                    }
                }
            }

            // Parse AppHdr if present
            parseAppHdr(doc, mxMessage);

            // Parse based on message type
            String msgType = mxMessage.getMessageType();
            if (msgType != null) {
                if (msgType.startsWith("pacs.008")) {
                    parsePacs008(doc, mxMessage);
                } else if (msgType.startsWith("pacs.009")) {
                    parsePacs009(doc, mxMessage);
                } else if (msgType.startsWith("camt.053")) {
                    parseCamt053(doc, mxMessage);
                }
            }

            // Fallback: try to detect message type from MsgDefIdr
            if (mxMessage.getMessageType() == null || mxMessage.getMessageType().isEmpty()) {
                NodeList msgDefIdrNodes = doc.getElementsByTagNameNS("*", "MsgDefIdr");
                if (msgDefIdrNodes.getLength() > 0) {
                    mxMessage.setMessageType(msgDefIdrNodes.item(0).getTextContent());
                }
            }

        } catch (Exception e) {
            log.error("Error parsing MX message: {}", e.getMessage(), e);
            mxMessage.setMessageType("Unknown");
        }

        return mxMessage;
    }

    private void parseAppHdr(Document doc, MxMessage mxMessage) {
        Map<String, String> fields = mxMessage.getFields();

        // Try with namespace
        NodeList appHdrNodes = doc.getElementsByTagNameNS("*", "AppHdr");
        if (appHdrNodes.getLength() == 0) {
            appHdrNodes = doc.getElementsByTagName("AppHdr");
        }

        if (appHdrNodes.getLength() > 0) {
            Element appHdr = (Element) appHdrNodes.item(0);

            // Business Message ID
            NodeList bizMsgIdrNodes = appHdr.getElementsByTagNameNS("*", "BizMsgIdr");
            if (bizMsgIdrNodes.getLength() == 0) {
                bizMsgIdrNodes = appHdr.getElementsByTagName("BizMsgIdr");
            }
            if (bizMsgIdrNodes.getLength() > 0) {
                mxMessage.setBusinessMessageId(bizMsgIdrNodes.item(0).getTextContent());
                fields.put("BizMsgIdr", mxMessage.getBusinessMessageId());
            }

            // Message Definition ID
            NodeList msgDefIdrNodes = appHdr.getElementsByTagNameNS("*", "MsgDefIdr");
            if (msgDefIdrNodes.getLength() == 0) {
                msgDefIdrNodes = appHdr.getElementsByTagName("MsgDefIdr");
            }
            if (msgDefIdrNodes.getLength() > 0) {
                mxMessage.setMessageDefinitionId(msgDefIdrNodes.item(0).getTextContent());
                fields.put("MsgDefIdr", mxMessage.getMessageDefinitionId());
            }

            // Creation Date
            NodeList creDtNodes = appHdr.getElementsByTagNameNS("*", "CreDt");
            if (creDtNodes.getLength() == 0) {
                creDtNodes = appHdr.getElementsByTagName("CreDt");
            }
            if (creDtNodes.getLength() > 0) {
                mxMessage.setCreationDateTime(creDtNodes.item(0).getTextContent());
                fields.put("CreDt", mxMessage.getCreationDateTime());
            }

            // Sender BIC
            NodeList frNodes = appHdr.getElementsByTagNameNS("*", "Fr");
            if (frNodes.getLength() == 0) {
                frNodes = appHdr.getElementsByTagName("Fr");
            }
            if (frNodes.getLength() > 0) {
                Element fr = (Element) frNodes.item(0);
                String senderBic = extractBicFromFiId(fr);
                if (senderBic != null) {
                    mxMessage.setSenderBic(senderBic);
                    fields.put("SenderBIC", senderBic);
                }
            }

            // Receiver BIC
            NodeList toNodes = appHdr.getElementsByTagNameNS("*", "To");
            if (toNodes.getLength() == 0) {
                toNodes = appHdr.getElementsByTagName("To");
            }
            if (toNodes.getLength() > 0) {
                Element to = (Element) toNodes.item(0);
                String receiverBic = extractBicFromFiId(to);
                if (receiverBic != null) {
                    mxMessage.setReceiverBic(receiverBic);
                    fields.put("ReceiverBIC", receiverBic);
                }
            }
        }
    }

    private String extractBicFromFiId(Element parent) {
        NodeList bicNodes = parent.getElementsByTagNameNS("*", "BICFI");
        if (bicNodes.getLength() == 0) {
            bicNodes = parent.getElementsByTagName("BICFI");
        }
        if (bicNodes.getLength() > 0) {
            return bicNodes.item(0).getTextContent();
        }
        return null;
    }

    private void parsePacs008(Document doc, MxMessage mxMessage) {
        Map<String, String> fields = mxMessage.getFields();

        // Group Header
        NodeList grpHdrNodes = doc.getElementsByTagNameNS("*", "GrpHdr");
        if (grpHdrNodes.getLength() == 0) {
            grpHdrNodes = doc.getElementsByTagName("GrpHdr");
        }
        if (grpHdrNodes.getLength() > 0) {
            Element grpHdr = (Element) grpHdrNodes.item(0);
            extractElementText(grpHdr, "MsgId", fields, "MsgId");
            extractElementText(grpHdr, "CreDtTm", fields, "CreDtTm");
        }

        // Credit Transfer Transaction
        NodeList cdtTrfTxNodes = doc.getElementsByTagNameNS("*", "CdtTrfTxInf");
        if (cdtTrfTxNodes.getLength() == 0) {
            cdtTrfTxNodes = doc.getElementsByTagName("CdtTrfTxInf");
        }
        if (cdtTrfTxNodes.getLength() > 0) {
            Element tx = (Element) cdtTrfTxNodes.item(0);

            // Payment Identification
            NodeList pmtIdNodes = tx.getElementsByTagNameNS("*", "PmtId");
            if (pmtIdNodes.getLength() == 0) {
                pmtIdNodes = tx.getElementsByTagName("PmtId");
            }
            if (pmtIdNodes.getLength() > 0) {
                Element pmtId = (Element) pmtIdNodes.item(0);
                extractElementText(pmtId, "InstrId", fields, "InstrId");
                extractElementText(pmtId, "EndToEndId", fields, "EndToEndId");
            }

            // Amount
            NodeList intrBkSttlmAmtNodes = tx.getElementsByTagNameNS("*", "IntrBkSttlmAmt");
            if (intrBkSttlmAmtNodes.getLength() == 0) {
                intrBkSttlmAmtNodes = tx.getElementsByTagName("IntrBkSttlmAmt");
            }
            if (intrBkSttlmAmtNodes.getLength() > 0) {
                Element amt = (Element) intrBkSttlmAmtNodes.item(0);
                fields.put("Amount", amt.getTextContent());
                String ccy = amt.getAttribute("Ccy");
                if (ccy != null && !ccy.isEmpty()) {
                    fields.put("Currency", ccy);
                }
            }

            // Interbank Settlement Date
            extractElementText(tx, "IntrBkSttlmDt", fields, "IntrBkSttlmDt");

            // Debtor (Ordering Customer)
            NodeList dbtrNodes = tx.getElementsByTagNameNS("*", "Dbtr");
            if (dbtrNodes.getLength() == 0) {
                dbtrNodes = tx.getElementsByTagName("Dbtr");
            }
            if (dbtrNodes.getLength() > 0) {
                Element dbtr = (Element) dbtrNodes.item(0);
                extractPartyInfo(dbtr, fields, "Dbtr");
            }

            // Debtor Agent (Ordering Institution)
            NodeList dbtrAgtNodes = tx.getElementsByTagNameNS("*", "DbtrAgt");
            if (dbtrAgtNodes.getLength() == 0) {
                dbtrAgtNodes = tx.getElementsByTagName("DbtrAgt");
            }
            if (dbtrAgtNodes.getLength() > 0) {
                Element dbtrAgt = (Element) dbtrAgtNodes.item(0);
                extractAgentInfo(dbtrAgt, fields, "DbtrAgt");
            }

            // Creditor (Beneficiary Customer)
            NodeList cdtrNodes = tx.getElementsByTagNameNS("*", "Cdtr");
            if (cdtrNodes.getLength() == 0) {
                cdtrNodes = tx.getElementsByTagName("Cdtr");
            }
            if (cdtrNodes.getLength() > 0) {
                Element cdtr = (Element) cdtrNodes.item(0);
                extractPartyInfo(cdtr, fields, "Cdtr");
            }

            // Creditor Agent (Beneficiary Institution)
            NodeList cdtrAgtNodes = tx.getElementsByTagNameNS("*", "CdtrAgt");
            if (cdtrAgtNodes.getLength() == 0) {
                cdtrAgtNodes = tx.getElementsByTagName("CdtrAgt");
            }
            if (cdtrAgtNodes.getLength() > 0) {
                Element cdtrAgt = (Element) cdtrAgtNodes.item(0);
                extractAgentInfo(cdtrAgt, fields, "CdtrAgt");
            }

            // Remittance Info
            NodeList rmtInfNodes = tx.getElementsByTagNameNS("*", "RmtInf");
            if (rmtInfNodes.getLength() == 0) {
                rmtInfNodes = tx.getElementsByTagName("RmtInf");
            }
            if (rmtInfNodes.getLength() > 0) {
                Element rmtInf = (Element) rmtInfNodes.item(0);
                extractElementText(rmtInf, "Ustrd", fields, "RemittanceInfo");
            }

            // Charges
            NodeList chrgBrNodes = tx.getElementsByTagNameNS("*", "ChrgBr");
            if (chrgBrNodes.getLength() == 0) {
                chrgBrNodes = tx.getElementsByTagName("ChrgBr");
            }
            if (chrgBrNodes.getLength() > 0) {
                fields.put("ChrgBr", chrgBrNodes.item(0).getTextContent());
            }
        }
    }

    private void parsePacs009(Document doc, MxMessage mxMessage) {
        Map<String, String> fields = mxMessage.getFields();

        // Group Header
        NodeList grpHdrNodes = doc.getElementsByTagNameNS("*", "GrpHdr");
        if (grpHdrNodes.getLength() == 0) {
            grpHdrNodes = doc.getElementsByTagName("GrpHdr");
        }
        if (grpHdrNodes.getLength() > 0) {
            Element grpHdr = (Element) grpHdrNodes.item(0);
            extractElementText(grpHdr, "MsgId", fields, "MsgId");
            extractElementText(grpHdr, "CreDtTm", fields, "CreDtTm");
        }

        // Credit Transfer Transaction
        NodeList cdtTrfTxNodes = doc.getElementsByTagNameNS("*", "CdtTrfTxInf");
        if (cdtTrfTxNodes.getLength() == 0) {
            cdtTrfTxNodes = doc.getElementsByTagName("CdtTrfTxInf");
        }
        if (cdtTrfTxNodes.getLength() > 0) {
            Element tx = (Element) cdtTrfTxNodes.item(0);

            // Payment Identification
            NodeList pmtIdNodes = tx.getElementsByTagNameNS("*", "PmtId");
            if (pmtIdNodes.getLength() == 0) {
                pmtIdNodes = tx.getElementsByTagName("PmtId");
            }
            if (pmtIdNodes.getLength() > 0) {
                Element pmtId = (Element) pmtIdNodes.item(0);
                extractElementText(pmtId, "InstrId", fields, "InstrId");
                extractElementText(pmtId, "EndToEndId", fields, "EndToEndId");
            }

            // Amount
            NodeList intrBkSttlmAmtNodes = tx.getElementsByTagNameNS("*", "IntrBkSttlmAmt");
            if (intrBkSttlmAmtNodes.getLength() == 0) {
                intrBkSttlmAmtNodes = tx.getElementsByTagName("IntrBkSttlmAmt");
            }
            if (intrBkSttlmAmtNodes.getLength() > 0) {
                Element amt = (Element) intrBkSttlmAmtNodes.item(0);
                fields.put("Amount", amt.getTextContent());
                String ccy = amt.getAttribute("Ccy");
                if (ccy != null && !ccy.isEmpty()) {
                    fields.put("Currency", ccy);
                }
            }

            // Interbank Settlement Date
            extractElementText(tx, "IntrBkSttlmDt", fields, "IntrBkSttlmDt");

            // Debtor Agent
            NodeList dbtrAgtNodes = tx.getElementsByTagNameNS("*", "DbtrAgt");
            if (dbtrAgtNodes.getLength() == 0) {
                dbtrAgtNodes = tx.getElementsByTagName("DbtrAgt");
            }
            if (dbtrAgtNodes.getLength() > 0) {
                Element dbtrAgt = (Element) dbtrAgtNodes.item(0);
                extractAgentInfo(dbtrAgt, fields, "DbtrAgt");
            }

            // Creditor Agent
            NodeList cdtrAgtNodes = tx.getElementsByTagNameNS("*", "CdtrAgt");
            if (cdtrAgtNodes.getLength() == 0) {
                cdtrAgtNodes = tx.getElementsByTagName("CdtrAgt");
            }
            if (cdtrAgtNodes.getLength() > 0) {
                Element cdtrAgt = (Element) cdtrAgtNodes.item(0);
                extractAgentInfo(cdtrAgt, fields, "CdtrAgt");
            }
        }
    }

    private void parseCamt053(Document doc, MxMessage mxMessage) {
        Map<String, String> fields = mxMessage.getFields();

        // Group Header
        NodeList grpHdrNodes = doc.getElementsByTagNameNS("*", "GrpHdr");
        if (grpHdrNodes.getLength() == 0) {
            grpHdrNodes = doc.getElementsByTagName("GrpHdr");
        }
        if (grpHdrNodes.getLength() > 0) {
            Element grpHdr = (Element) grpHdrNodes.item(0);
            extractElementText(grpHdr, "MsgId", fields, "MsgId");
            extractElementText(grpHdr, "CreDtTm", fields, "CreDtTm");
        }

        // Statement
        NodeList stmtNodes = doc.getElementsByTagNameNS("*", "Stmt");
        if (stmtNodes.getLength() == 0) {
            stmtNodes = doc.getElementsByTagName("Stmt");
        }
        if (stmtNodes.getLength() > 0) {
            Element stmt = (Element) stmtNodes.item(0);

            // Account
            NodeList acctNodes = stmt.getElementsByTagNameNS("*", "Acct");
            if (acctNodes.getLength() == 0) {
                acctNodes = stmt.getElementsByTagName("Acct");
            }
            if (acctNodes.getLength() > 0) {
                Element acct = (Element) acctNodes.item(0);
                NodeList idNodes = acct.getElementsByTagNameNS("*", "Id");
                if (idNodes.getLength() == 0) {
                    idNodes = acct.getElementsByTagName("Id");
                }
                if (idNodes.getLength() > 0) {
                    Element id = (Element) idNodes.item(0);
                    NodeList othrNodes = id.getElementsByTagNameNS("*", "Othr");
                    if (othrNodes.getLength() == 0) {
                        othrNodes = id.getElementsByTagName("Othr");
                    }
                    if (othrNodes.getLength() > 0) {
                        Element othr = (Element) othrNodes.item(0);
                        extractElementText(othr, "Id", fields, "AccountId");
                    }
                }
            }

            // Statement Number
            extractElementText(stmt, "ElctrncSeqNb", fields, "StmtSeqNb");

            // Opening Balance
            NodeList balOpnNodes = stmt.getElementsByTagNameNS("*", "BalOpn");
            if (balOpnNodes.getLength() == 0) {
                balOpnNodes = stmt.getElementsByTagName("BalOpn");
            }
            if (balOpnNodes.getLength() > 0) {
                Element balOpn = (Element) balOpnNodes.item(0);
                extractBalanceInfo(balOpn, fields, "Opening");
            }

            // Closing Balance
            NodeList balClsgNodes = stmt.getElementsByTagNameNS("*", "BalClsg");
            if (balClsgNodes.getLength() == 0) {
                balClsgNodes = stmt.getElementsByTagName("BalClsg");
            }
            if (balClsgNodes.getLength() > 0) {
                Element balClsg = (Element) balClsgNodes.item(0);
                extractBalanceInfo(balClsg, fields, "Closing");
            }

            // Transactions
            NodeList ntryNodes = stmt.getElementsByTagNameNS("*", "Ntry");
            if (ntryNodes.getLength() == 0) {
                ntryNodes = doc.getElementsByTagName("Ntry");
            }
            fields.put("EntryCount", String.valueOf(ntryNodes.getLength()));
        }
    }

    private void extractElementText(Element parent, String tagName, Map<String, String> fields, String fieldName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", tagName);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagName(tagName);
        }
        if (nodes.getLength() > 0) {
            fields.put(fieldName, nodes.item(0).getTextContent());
        }
    }

    private void extractPartyInfo(Element party, Map<String, String> fields, String prefix) {
        // Name
        NodeList nmNodes = party.getElementsByTagNameNS("*", "Nm");
        if (nmNodes.getLength() == 0) {
            nmNodes = party.getElementsByTagName("Nm");
        }
        if (nmNodes.getLength() > 0) {
            fields.put(prefix + "Name", nmNodes.item(0).getTextContent());
        }

        // Account
        NodeList acctNodes = party.getElementsByTagNameNS("*", "Acct");
        if (acctNodes.getLength() == 0) {
            acctNodes = party.getElementsByTagName("Acct");
        }
        if (acctNodes.getLength() > 0) {
            Element acct = (Element) acctNodes.item(0);
            NodeList idNodes = acct.getElementsByTagNameNS("*", "Id");
            if (idNodes.getLength() == 0) {
                idNodes = acct.getElementsByTagName("Id");
            }
            if (idNodes.getLength() > 0) {
                Element id = (Element) idNodes.item(0);
                NodeList ibanNodes = id.getElementsByTagNameNS("*", "IBAN");
                if (ibanNodes.getLength() == 0) {
                    ibanNodes = id.getElementsByTagName("IBAN");
                }
                if (ibanNodes.getLength() > 0) {
                    fields.put(prefix + "Acct", ibanNodes.item(0).getTextContent());
                } else {
                    NodeList othrNodes = id.getElementsByTagNameNS("*", "Othr");
                    if (othrNodes.getLength() == 0) {
                        othrNodes = id.getElementsByTagName("Othr");
                    }
                    if (othrNodes.getLength() > 0) {
                        Element othr = (Element) othrNodes.item(0);
                        extractElementText(othr, "Id", fields, prefix + "Acct");
                    }
                }
            }
        }

        // Address
        NodeList pstlAdrNodes = party.getElementsByTagNameNS("*", "PstlAdr");
        if (pstlAdrNodes.getLength() == 0) {
            pstlAdrNodes = party.getElementsByTagName("PstlAdr");
        }
        if (pstlAdrNodes.getLength() > 0) {
            Element pstlAdr = (Element) pstlAdrNodes.item(0);
            extractElementText(pstlAdr, "Ctry", fields, prefix + "Ctry");
        }
    }

    private void extractAgentInfo(Element agent, Map<String, String> fields, String prefix) {
        NodeList finInstnIdNodes = agent.getElementsByTagNameNS("*", "FinInstnId");
        if (finInstnIdNodes.getLength() == 0) {
            finInstnIdNodes = agent.getElementsByTagName("FinInstnId");
        }
        if (finInstnIdNodes.getLength() > 0) {
            Element finInstnId = (Element) finInstnIdNodes.item(0);
            NodeList bicNodes = finInstnId.getElementsByTagNameNS("*", "BICFI");
            if (bicNodes.getLength() == 0) {
                bicNodes = finInstnId.getElementsByTagName("BICFI");
            }
            if (bicNodes.getLength() > 0) {
                fields.put(prefix + "BIC", bicNodes.item(0).getTextContent());
            }
        }
    }

    private void extractBalanceInfo(Element balance, Map<String, String> fields, String prefix) {
        // Amount
        NodeList amtNodes = balance.getElementsByTagNameNS("*", "Amt");
        if (amtNodes.getLength() == 0) {
            amtNodes = balance.getElementsByTagName("Amt");
        }
        if (amtNodes.getLength() > 0) {
            Element amt = (Element) amtNodes.item(0);
            fields.put(prefix + "Balance", amt.getTextContent());
            String ccy = amt.getAttribute("Ccy");
            if (ccy != null && !ccy.isEmpty()) {
                fields.put(prefix + "Currency", ccy);
            }
        }

        // Credit/Debit indicator
        NodeList cdtDbtIndNodes = balance.getElementsByTagNameNS("*", "CdtDbtInd");
        if (cdtDbtIndNodes.getLength() == 0) {
            cdtDbtIndNodes = balance.getElementsByTagName("CdtDbtInd");
        }
        if (cdtDbtIndNodes.getLength() > 0) {
            fields.put(prefix + "Indicator", cdtDbtIndNodes.item(0).getTextContent());
        }

        // Date
        NodeList dtNodes = balance.getElementsByTagNameNS("*", "Dt");
        if (dtNodes.getLength() == 0) {
            dtNodes = balance.getElementsByTagName("Dt");
        }
        if (dtNodes.getLength() > 0) {
            fields.put(prefix + "Date", dtNodes.item(0).getTextContent());
        }
    }
}
