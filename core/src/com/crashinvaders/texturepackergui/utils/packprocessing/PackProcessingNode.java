package com.crashinvaders.texturepackergui.utils.packprocessing;

import com.badlogic.gdx.utils.ObjectMap;
import com.crashinvaders.texturepackergui.services.model.PackModel;
import com.crashinvaders.texturepackergui.services.model.ProjectModel;

public class PackProcessingNode {
    public static final String META_COMPRESSION_RATE = "compressionRate";

    private final ObjectMap<String, Object> metadata = new ObjectMap<>();
    private final ProjectModel project;
    private final PackModel pack;
    private PackModel origPack;
    private String log = "";

    public PackProcessingNode(ProjectModel project, PackModel pack) {
        this.project = project;
        this.pack = pack;
        this.origPack = pack;
    }

    public ProjectModel getProject() {
        return project;
    }

    public PackModel getPack() {
        return pack;
    }

    public PackModel getOrigPack() {
        return origPack;
    }

    public void setOrigPack(PackModel origPack) {
        this.origPack = origPack;
    }

    public String getLog() {
        return log;
    }

    void setLog(String log) {
        this.log = log;
    }

    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        return (T) metadata.get(key, defaultValue);
    }
}
