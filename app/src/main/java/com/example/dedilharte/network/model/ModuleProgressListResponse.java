package com.example.dedilharte.network.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class ModuleProgressListResponse {
    public List<Item> items;

    public static final class Item {
        @SerializedName("item_id")
        public String itemId;
        public boolean completed;
    }
}
