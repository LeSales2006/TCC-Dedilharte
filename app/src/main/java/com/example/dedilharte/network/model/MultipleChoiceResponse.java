package com.example.dedilharte.network.model;

import java.util.ArrayList;
import java.util.List;

public final class MultipleChoiceResponse {
    public String question;
    public List<String> options = new ArrayList<>();
    public int correctIndex;
    public String explanation;
}
