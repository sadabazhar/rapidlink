package com.rapidlink.services;

import java.util.Map;

public interface ClickTrackingService {

    public void increment(String shortCode);

    public Map<String, Long> fetchAndReset();
}
