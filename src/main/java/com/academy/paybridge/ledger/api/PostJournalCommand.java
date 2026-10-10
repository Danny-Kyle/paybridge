package com.academy.paybridge.ledger.api;

import java.util.List;

public record PostJournalCommand(String reference, JournalType type, String description, List<EntryLine> lines) {}