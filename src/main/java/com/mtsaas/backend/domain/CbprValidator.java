package com.mtsaas.backend.domain;

import com.mtsaas.backend.domain.swift.mt.MtMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CbprValidator {

    public static List<String> validatePacs009Cov(MtMessage mtMessage) {
        List<String> errors = new ArrayList<>();
        Map<String, String> tags = mtMessage.getTags();

        // Transaction Level Mandatory Checks
        checkTag(tags, "20", "Transaction Reference Number (:20:)", errors);
        checkTag(tags, "32A", "Value Date/Currency/Interbank Settled Amount (:32A:)", errors);

        // Instructing/Instructed Agents (implied by mtMessage sender/receiver)
        if (mtMessage.getSender() == null || mtMessage.getSender().isBlank()) {
            errors.add("Missing Sender BIC (Instructing Agent)");
        }
        if (mtMessage.getReceiver() == null || mtMessage.getReceiver().isBlank()) {
            errors.add("Missing Receiver BIC (Instructed Agent)");
        }

        // Sequence B (Underlying) Mandatory Checks
        String indicator = tags.get("119");
        if (indicator == null || !indicator.contains("COV")) {
            errors.add("Missing {119:COV} indicator for pacs.009.COV");
        }

        checkAnyTag(tags, "50", "Underlying Debtor (:50a:)", errors);
        checkAnyTag(tags, "59", "Underlying Creditor (:59a:)", errors);
        checkTag(tags, "33B", "Underlying Instructed Amount (:33B:)", errors);

        return errors;
    }

    public static List<String> validatePacs008(MtMessage mtMessage) {
        List<String> errors = new ArrayList<>();
        Map<String, String> tags = mtMessage.getTags();

        checkTag(tags, "20", "Transaction Reference Number (:20:)", errors);
        checkTag(tags, "32A", "Value Date/Currency/Interbank Settled Amount (:32A:)", errors);

        checkAnyTag(tags, "50", "Ordering Customer (Debtor) (:50a:)", errors);
        checkAnyTag(tags, "59", "Beneficiary Customer (Creditor) (:59a:)", errors);

        // Instructing/Instructed Agents
        if (mtMessage.getSender() == null || mtMessage.getSender().isBlank()) {
            errors.add("Missing Sender BIC (Instructing Agent)");
        }
        if (mtMessage.getReceiver() == null || mtMessage.getReceiver().isBlank()) {
            errors.add("Missing Receiver BIC (Instructed Agent)");
        }

        return errors;
    }

    public static List<String> validatePacs008Bulk(MtMessage mtMessage) {
        List<String> errors = new ArrayList<>();
        Map<String, String> tags = mtMessage.getTags();

        checkTag(tags, "20", "Sender's Reference (:20:)", errors);
        checkTag(tags, "32A", "Total Amount (:32A:)", errors);

        // At least one transaction reference
        if (!tags.containsKey("21")) {
            errors.add("Missing at least one Transaction Reference (:21:)");
        }

        checkAnyTag(tags, "50", "Ordering Customer (Debtor) (:50a:)", errors);

        // For bulk, each transaction should have a beneficiary, but validator checks
        // overall message
        // In a real scenario, we'd check each block, but for MVP we check the main tags
        checkAnyTag(tags, "59", "Beneficiary Customer (Creditor) (:59a:)", errors);

        return errors;
    }

    public static List<String> validatePacs009(MtMessage mtMessage) {
        List<String> errors = new ArrayList<>();
        Map<String, String> tags = mtMessage.getTags();

        checkTag(tags, "20", "Transaction Reference Number (:20:)", errors);
        checkTag(tags, "32A", "Value Date/Currency/Interbank Settled Amount (:32A:)", errors);

        // Instructing/Instructed Agents
        if (mtMessage.getSender() == null || mtMessage.getSender().isBlank()) {
            errors.add("Missing Sender BIC (Instructing Agent)");
        }
        if (mtMessage.getReceiver() == null || mtMessage.getReceiver().isBlank()) {
            errors.add("Missing Receiver BIC (Instructed Agent)");
        }

        return errors;
    }

    private static void checkTag(Map<String, String> tags, String key, String label, List<String> errors) {
        if (!tags.containsKey(key) || tags.get(key).isBlank()) {
            errors.add("Missing mandatory tag: " + label);
        }
    }

    private static void checkAnyTag(Map<String, String> tags, String baseKey, String label, List<String> errors) {
        boolean found = false;
        if (tags.containsKey(baseKey))
            found = true;
        else {
            String[] suffixes = { "A", "B", "D", "F", "K", "C" };
            for (String s : suffixes) {
                if (tags.containsKey(baseKey + s)) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            errors.add("Missing mandatory tag group: " + label);
        }
    }
}
