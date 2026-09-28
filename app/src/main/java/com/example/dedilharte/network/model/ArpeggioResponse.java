package com.example.dedilharte.network.model;

import java.util.ArrayList;
import java.util.List;

public final class ArpeggioResponse {
    public String id;
    public String title;
    public String chord;
    public List<ArpeggioNote> notes = new ArrayList<>();
    public String difficulty = "easy";
    public boolean active = true;
    public int sortOrder;
    public String created_at;
    public String updated_at;
}
