package de.maxhenkel.voicechat.voice.server;

import de.maxhenkel.voicechat.voice.common.ClientGroup;

import javax.annotation.Nullable;
import java.util.UUID;

public class Group {

    private UUID id;
    private String name;
    @Nullable
    private String password;
    private boolean persistent;

    public Group(UUID id, String name, @Nullable String password, boolean persistent) {
        this.id = id;
        this.name = name;
        this.password = password;
        this.persistent = persistent;
    }

    public Group(UUID id, String name, @Nullable String password) {
        this(id, name, password, false);
    }

    public Group(UUID id, String name) {
        this(id, name, null);
    }

    public Group() {

    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public ClientGroup toClientGroup() {
        return new ClientGroup(id, name, password != null, persistent);
    }

}
