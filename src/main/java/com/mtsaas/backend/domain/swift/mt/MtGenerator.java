package com.mtsaas.backend.domain.swift.mt;

import com.mtsaas.backend.domain.swift.mx.MxMessage;

/**
 * Interface for generating MT messages from MX messages.
 */
public interface MtGenerator {
    /**
     * Generate an MT message from an MX message.
     * @param mxMessage The parsed MX message
     * @return The MT message string
     */
    String generate(MxMessage mxMessage);

    /**
     * Check if this generator supports the given MX message type.
     * @param mxType The MX message type (e.g., "pacs.008.001.08")
     * @return true if supported
     */
    boolean supports(String mxType);
}
