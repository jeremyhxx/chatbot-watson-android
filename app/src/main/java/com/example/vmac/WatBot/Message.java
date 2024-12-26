package com.example.vmac.WatBot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.ibm.watson.assistant.v2.model.RuntimeResponseGeneric;
import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 2L; // Increment version
    
    @NonNull
    private String id = "";
    @NonNull
    private String message = "";
    @Nullable
    private String url;
    @Nullable
    private String title;
    @Nullable
    private String description;
    @NonNull
    private Type type = Type.TEXT;

    public Message() {
        // Default constructor with default values already set
    }

    public Message(RuntimeResponseGeneric r) {
        if (r == null) {
            throw new IllegalArgumentException("RuntimeResponseGeneric cannot be null");
        }
        this.title = r.title();
        this.description = r.description();
        this.url = r.source();
        this.id = "2";
        this.type = Type.IMAGE;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id != null ? id : "";
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    public void setMessage(@Nullable String message) {
        this.message = message != null ? message : "";
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    public void setType(@NonNull Type type) {
        this.type = type != null ? type : Type.TEXT;
    }

    public enum Type {
        TEXT,
        IMAGE
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", message='" + message + '\'' +
                ", type=" + type +
                (url != null ? ", url='" + url + '\'' : "") +
                (title != null ? ", title='" + title + '\'' : "") +
                (description != null ? ", description='" + description + '\'' : "") +
                '}';
    }
}

