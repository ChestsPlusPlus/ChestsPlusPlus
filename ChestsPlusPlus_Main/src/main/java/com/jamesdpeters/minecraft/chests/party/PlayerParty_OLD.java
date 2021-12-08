package com.jamesdpeters.minecraft.chests.party;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SerializableAs("PlayerParty")
public class PlayerParty_OLD implements ConfigurationSerializable {

    private OfflinePlayer owner;
    private UUID ownerUUID;
    private String partyName;
    private List<OfflinePlayer> members;
    private ArrayList<String> memberUUIDs;

    public PlayerParty_OLD(OfflinePlayer owner, String partyName) {
        this.owner = owner;
        this.ownerUUID = owner.getUniqueId();
        this.partyName = partyName;
        this.members = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public PlayerParty_OLD(Map<String, Object> map){
        ownerUUID = UUID.fromString((String) map.get("owner"));
        owner = Bukkit.getOfflinePlayer(ownerUUID);

        partyName = (String) map.get("partyName");

        Object o = map.get("members");
        if (o != null) {
            memberUUIDs = (ArrayList<String>) o;
            members = new ArrayList<>();
            for (String uuid : memberUUIDs){
                members.add(Bukkit.getOfflinePlayer(UUID.fromString(uuid)));
            }
        }

    }


    @Override
    public Map<String, Object> serialize() {
        HashMap<String, Object> map = new LinkedHashMap<>();
        map.put("owner", owner.getUniqueId().toString());
        map.put("partyName", partyName);

        memberUUIDs = new ArrayList<>();
        members.forEach(player -> memberUUIDs.add(player.getUniqueId().toString()));
        map.put("members", memberUUIDs);
        return map;
    }

    public OfflinePlayer getOwner() {
        return owner;
    }

    public String getPartyName() {
        return partyName;
    }

    public List<OfflinePlayer> getMembers() {
        return members;
    }

    public List<OfflinePlayer> getAllPlayers() {
        List<OfflinePlayer> players = new ArrayList<>(members);
        players.add(0, owner);
        return players;
    }

    public void addMember(OfflinePlayer player) {
        if (members == null){
            members = new ArrayList<>();
        }
        members.add(player);
    }

    public void removeMember(OfflinePlayer player) {
        if (members != null) members.remove(player);
    }

    public boolean isMember(OfflinePlayer player) {
        return getMembers().stream().anyMatch(p -> p.getUniqueId().equals(player.getUniqueId()));
    }

}
