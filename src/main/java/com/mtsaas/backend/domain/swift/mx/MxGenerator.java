package com.mtsaas.backend.domain.swift.mx;

import com.mtsaas.backend.domain.swift.mt.MtMessage;

public interface MxGenerator {
    String generate(MtMessage mtMessage);

    boolean supports(String mtType);
}
