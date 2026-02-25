package com.mtsaas.backend.domain.swift.mt;

import com.mtsaas.backend.domain.swift.mx.MxMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Generates MT202 (Financial Institution Credit Transfer) from pacs.009 (FIToFICreditTransfer).
 */
@Component
public class Mt202Generator extends BaseMtGenerator {

    @Override
    public boolean supports(String mxType) {
        return mxType != null && mxType.startsWith("pacs.009");
    }

    @Override
    protected void validateInput(MxMessage mxMessage) {
        Map<String, String> fields = mxMessage.getFields();
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("pacs.009 message has no parsed fields");
        }
        if (!fields.containsKey("MsgId") && !fields.containsKey("InstrId")) {
            throw new IllegalArgumentException("pacs.009 must have a MsgId or InstrId for transaction reference");
        }
    }

    @Override
    protected String generateMt(MxMessage mxMessage) {
        Map<String, String> fields = mxMessage.getFields();
        StringBuilder block4 = new StringBuilder();

        // :20: Transaction Reference Number
        String msgId = getField(mxMessage, "MsgId", getField(mxMessage, "InstrId", "UNKNOWN"));
        block4.append(":20:").append(escapeMt(msgId).substring(0, Math.min(16, msgId.length()))).append("\n");

        // :21: Related Reference
        String endToEndId = getField(mxMessage, "EndToEndId");
        if (!endToEndId.isEmpty()) {
            block4.append(":21:").append(escapeMt(endToEndId).substring(0, Math.min(16, endToEndId.length()))).append("\n");
        }

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

        // :52A: Ordering Institution (Debtor Agent)
        String dbtrAgtBic = getField(mxMessage, "DbtrAgtBIC");
        if (!dbtrAgtBic.isEmpty()) {
            block4.append(":52A:").append(formatBic(dbtrAgtBic)).append("\n");
        }

        // :58A: Beneficiary Institution (Creditor Agent)
        String cdtrAgtBic = getField(mxMessage, "CdtrAgtBIC");
        if (!cdtrAgtBic.isEmpty()) {
            block4.append(":58A:").append(formatBic(cdtrAgtBic)).append("\n");
        }

        return buildMtMessage("202", mxMessage.getSenderBic(), mxMessage.getReceiverBic(), block4.toString());
    }

    private String formatAmountForMt(String amount, String currency) {
        if (amount == null || amount.isEmpty()) {
            return currency + "0";
        }
        String cleanAmount = amount.replaceAll("[^0-9.]", "");
        try {
            double amt = Double.parseDouble(cleanAmount);
            long amtLong = Math.round(amt * 100);
            String formatted = String.format("%d", amtLong / 100);
            if (amtLong % 100 != 0) {
                formatted = String.format("%d,%02d", amtLong / 100, amtLong % 100);
            }
            return currency + formatted;
        } catch (NumberFormatException e) {
            return currency + cleanAmount.replace(".", ",");
        }
    }
}
