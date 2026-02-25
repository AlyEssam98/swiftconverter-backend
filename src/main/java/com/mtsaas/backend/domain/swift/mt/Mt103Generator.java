package com.mtsaas.backend.domain.swift.mt;

import com.mtsaas.backend.domain.swift.mx.MxMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Generates MT103 (Customer Credit Transfer) from pacs.008 (FIToFICustomerCreditTransfer).
 */
@Component
public class Mt103Generator extends BaseMtGenerator {

    @Override
    public boolean supports(String mxType) {
        return mxType != null && mxType.startsWith("pacs.008");
    }

    @Override
    protected void validateInput(MxMessage mxMessage) {
        Map<String, String> fields = mxMessage.getFields();
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("pacs.008 message has no parsed fields");
        }
        if (!fields.containsKey("MsgId") && !fields.containsKey("EndToEndId")) {
            throw new IllegalArgumentException("pacs.008 must have a MsgId or EndToEndId for transaction reference");
        }
    }

    @Override
    protected String generateMt(MxMessage mxMessage) {
        Map<String, String> fields = mxMessage.getFields();
        StringBuilder block4 = new StringBuilder();

        // :20: Transaction Reference Number
        String msgId = getField(mxMessage, "MsgId", getField(mxMessage, "EndToEndId", "UNKNOWN"));
        block4.append(":20:").append(escapeMt(msgId).substring(0, Math.min(16, msgId.length()))).append("\n");

        // :23B: Bank Transaction Code
        block4.append(":23B:CRED\n");

        // :32A: Value Date, Currency, Amount
        String date = getField(mxMessage, "IntrBkSttlmDt");
        String amount = getField(mxMessage, "Amount");
        String currency = getField(mxMessage, "Currency", "USD");
        if (date.isEmpty()) {
            date = getField(mxMessage, "CreDtTm", "2023-01-01");
        }
        String formattedDate = formatDate(date);
        String formattedAmount = formatAmountForMt(amount, currency);
        block4.append(":32A:").append(formattedDate).append(formattedAmount).append("\n");

        // :50K: Ordering Customer (Debtor)
        String dbtrName = getField(mxMessage, "DbtrName");
        String dbtrAcct = getField(mxMessage, "DbtrAcct");
        String dbtrCtry = getField(mxMessage, "DbtrCtry");
        if (!dbtrName.isEmpty() || !dbtrAcct.isEmpty()) {
            block4.append(":50K:");
            if (!dbtrAcct.isEmpty()) {
                block4.append("/").append(escapeMt(dbtrAcct)).append("\n");
            } else {
                block4.append("\n");
            }
            if (!dbtrName.isEmpty()) {
                block4.append(escapeMt(dbtrName)).append("\n");
            }
            if (!dbtrCtry.isEmpty()) {
                block4.append(escapeMt(dbtrCtry)).append("\n");
            }
        }

        // :52A: Ordering Institution (Debtor Agent)
        String dbtrAgtBic = getField(mxMessage, "DbtrAgtBIC");
        if (!dbtrAgtBic.isEmpty()) {
            block4.append(":52A:").append(formatBic(dbtrAgtBic)).append("\n");
        }

        // :57A: Account With Institution (Creditor Agent)
        String cdtrAgtBic = getField(mxMessage, "CdtrAgtBIC");
        if (!cdtrAgtBic.isEmpty()) {
            block4.append(":57A:").append(formatBic(cdtrAgtBic)).append("\n");
        }

        // :59: Beneficiary Customer (Creditor)
        String cdtrName = getField(mxMessage, "CdtrName");
        String cdtrAcct = getField(mxMessage, "CdtrAcct");
        String cdtrCtry = getField(mxMessage, "CdtrCtry");
        if (!cdtrName.isEmpty() || !cdtrAcct.isEmpty()) {
            block4.append(":59:");
            if (!cdtrAcct.isEmpty()) {
                block4.append("/").append(escapeMt(cdtrAcct)).append("\n");
            } else {
                block4.append("\n");
            }
            if (!cdtrName.isEmpty()) {
                block4.append(escapeMt(cdtrName)).append("\n");
            }
            if (!cdtrCtry.isEmpty()) {
                block4.append(escapeMt(cdtrCtry)).append("\n");
            }
        }

        // :70: Remittance Information
        String rmtInfo = getField(mxMessage, "RemittanceInfo");
        if (!rmtInfo.isEmpty()) {
            block4.append(":70:").append(escapeMt(rmtInfo)).append("\n");
        }

        // :71A: Details of Charges
        String chrgBr = getField(mxMessage, "ChrgBr", "SHAR");
        String chrgCode = mapChargeBearer(chrgBr);
        block4.append(":71A:").append(chrgCode).append("\n");

        return buildMtMessage("103", mxMessage.getSenderBic(), mxMessage.getReceiverBic(), block4.toString());
    }

    private String formatAmountForMt(String amount, String currency) {
        if (amount == null || amount.isEmpty()) {
            return currency + "0";
        }
        // Remove any non-numeric characters except decimal point
        String cleanAmount = amount.replaceAll("[^0-9.]", "");
        try {
            double amt = Double.parseDouble(cleanAmount);
            // Format: currency + amount with commas (no decimal, implied 2 decimals)
            long amtLong = Math.round(amt * 100);
            String formatted = String.format("%d", amtLong / 100);
            // Add commas for thousands
            formatted = String.format("%,d", amtLong / 100).replace(",", "");
            if (amtLong % 100 != 0) {
                // Has decimal portion - MT uses comma for decimal
                formatted = String.format("%,d,%02d", amtLong / 100, amtLong % 100).replace(",", "");
            }
            return currency + formatted;
        } catch (NumberFormatException e) {
            return currency + cleanAmount.replace(".", ",");
        }
    }

    private String mapChargeBearer(String chrgBr) {
        if (chrgBr == null) return "SHAR";
        switch (chrgBr.toUpperCase()) {
            case "DEBT": return "OUR";
            case "CRED": return "BEN";
            case "SHAR": return "SHAR";
            default: return "SHAR";
        }
    }
}
