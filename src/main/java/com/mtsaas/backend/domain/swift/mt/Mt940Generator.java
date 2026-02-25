package com.mtsaas.backend.domain.swift.mt;

import com.mtsaas.backend.domain.swift.mx.MxMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Generates MT940 (Customer Statement) from camt.053 (BankToCustomerStatement).
 */
@Component
public class Mt940Generator extends BaseMtGenerator {

    @Override
    public boolean supports(String mxType) {
        return mxType != null && mxType.startsWith("camt.053");
    }

    @Override
    protected void validateInput(MxMessage mxMessage) {
        Map<String, String> fields = mxMessage.getFields();
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("camt.053 message has no parsed fields");
        }
        if (!fields.containsKey("MsgId")) {
            throw new IllegalArgumentException("camt.053 must have a MsgId");
        }
        if (!fields.containsKey("AccountId") && !fields.containsKey("OpeningBalance")) {
            throw new IllegalArgumentException("camt.053 must have account identification or balance information");
        }
    }

    @Override
    protected String generateMt(MxMessage mxMessage) {
        StringBuilder block4 = new StringBuilder();

        // :20: Transaction Reference Number
        String msgId = getField(mxMessage, "MsgId", "UNKNOWN");
        block4.append(":20:").append(escapeMt(msgId).substring(0, Math.min(16, msgId.length()))).append("\n");

        // :25: Account Identification
        String accountId = getField(mxMessage, "AccountId");
        if (!accountId.isEmpty()) {
            block4.append(":25:").append(escapeMt(accountId)).append("\n");
        }

        // :28C: Statement Number/Sequence
        String seqNb = getField(mxMessage, "StmtSeqNb", "1");
        block4.append(":28C:").append(seqNb).append("/1\n");

        // :60F: Opening Balance
        String opnBal = getField(mxMessage, "OpeningBalance");
        String opnCcy = getField(mxMessage, "OpeningCurrency", "USD");
        String opnDate = getField(mxMessage, "OpeningDate");
        String opnInd = getField(mxMessage, "OpeningIndicator", "CRDT");
        
        if (!opnBal.isEmpty()) {
            String balanceType = "CRDT".equals(opnInd) ? "F" : "D";
            String formattedDate = !opnDate.isEmpty() ? formatDate(opnDate) : "230101";
            String formattedAmount = formatBalanceAmount(opnBal, opnCcy);
            block4.append(":60").append(balanceType).append(":").append(formattedDate)
                  .append(opnCcy).append(formattedAmount).append("\n");
        }

        // :61: Statement Line (for each entry - simplified for MVP)
        String entryCount = getField(mxMessage, "EntryCount", "0");
        if (!entryCount.equals("0")) {
            // Generate a placeholder statement line
            String stmtDate = !opnDate.isEmpty() ? formatDate(opnDate) : "230101";
            block4.append(":61:").append(stmtDate).append(stmtDate)
                  .append("RC").append(opnCcy).append("0,\n");
            block4.append(":86:Statement entries available\n");
        }

        // :62F: Closing Balance
        String clsBal = getField(mxMessage, "ClosingBalance");
        String clsCcy = getField(mxMessage, "ClosingCurrency", opnCcy);
        String clsDate = getField(mxMessage, "ClosingDate");
        String clsInd = getField(mxMessage, "ClosingIndicator", "CRDT");
        
        if (!clsBal.isEmpty()) {
            String balanceType = "CRDT".equals(clsInd) ? "F" : "D";
            String formattedDate = !clsDate.isEmpty() ? formatDate(clsDate) : "230101";
            String formattedAmount = formatBalanceAmount(clsBal, clsCcy);
            block4.append(":62").append(balanceType).append(":").append(formattedDate)
                  .append(clsCcy).append(formattedAmount).append("\n");
        } else if (!opnBal.isEmpty()) {
            // Use opening balance as closing if not provided
            String balanceType = "CRDT".equals(opnInd) ? "F" : "D";
            String formattedDate = !opnDate.isEmpty() ? formatDate(opnDate) : "230101";
            String formattedAmount = formatBalanceAmount(opnBal, opnCcy);
            block4.append(":62").append(balanceType).append(":").append(formattedDate)
                  .append(opnCcy).append(formattedAmount).append("\n");
        }

        return buildMtMessage("940", mxMessage.getSenderBic(), mxMessage.getReceiverBic(), block4.toString());
    }

    private String formatBalanceAmount(String amount, String currency) {
        if (amount == null || amount.isEmpty()) {
            return "0,";
        }
        String cleanAmount = amount.replaceAll("[^0-9.]", "");
        try {
            double amt = Double.parseDouble(cleanAmount);
            long amtLong = Math.round(amt * 100);
            if (amtLong % 100 != 0) {
                return String.format("%d,%02d", amtLong / 100, amtLong % 100);
            }
            return String.format("%d,", amtLong / 100);
        } catch (NumberFormatException e) {
            return cleanAmount.replace(".", ",") + ",";
        }
    }
}
