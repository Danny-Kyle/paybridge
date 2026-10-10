package com.academy.paybridge.compliance.service;

import com.academy.paybridge.compliance.api.Decision;

record Finding(Decision decision, String reason) {}