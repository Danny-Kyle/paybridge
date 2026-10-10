package com.academy.paybridge.compliance.api;
import java.util.List;
public record ScreeningResult(Decision decision, List<String> reasons) {}