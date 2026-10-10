package ru.selteck.lineup;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Block {
    public long rowId;
    public String contentId = UUID.randomUUID().toString();
    public String sourcePackId = "";
    public String title = "";
    public String map = "";
    public String category = "Раскидка";
    public String side = "Атака";
    public String plant = "";
    public String grenadeType = "";
    public String description = "";
    public long updatedAt = System.currentTimeMillis();
    public List<String> photos = new ArrayList<>();
    public List<String> videos = new ArrayList<>();

    public Block copy() {
        Block b = new Block();
        b.rowId = rowId; b.contentId = contentId; b.sourcePackId = sourcePackId;
        b.title = title; b.map = map; b.category = category; b.side = side;
        b.plant = plant; b.grenadeType = grenadeType; b.description = description;
        b.updatedAt = updatedAt; b.photos = new ArrayList<>(photos); b.videos = new ArrayList<>(videos);
        return b;
    }
}
