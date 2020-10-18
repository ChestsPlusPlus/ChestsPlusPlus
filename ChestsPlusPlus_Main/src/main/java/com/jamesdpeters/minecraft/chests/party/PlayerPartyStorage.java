package com.jamesdpeters.minecraft.chests.party;

import com.jamesdpeters.minecraft.chests.serialize.Config;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SerializableAs("PlayerPartyStorage")
public class PlayerPartyStorage implements ConfigurationSerializable {

    private OfflinePlayer owner;
    private HashMap<String, PlayerParty> ownedParties;

    public PlayerPartyStorage(OfflinePlayer owner){
        this.owner = owner;
        this.ownedParties = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public PlayerPartyStorage(Map<String, Object> map){
        UUID ownerUUID = UUID.fromString((String) map.get("owner"));
        owner = Bukkit.getOfflinePlayer(ownerUUID);

        ownedParties = (HashMap<String, PlayerParty>) map.get("ownedParties");
        if (ownedParties == null) ownedParties = new HashMap<>();
    }

    @Override
    public Map<String, Object> serialize() {
        HashMap<String, Object> map = new LinkedHashMap<>();
        map.put("owner", owner.getUniqueId().toString());
        map.put("ownedParties", ownedParties);
        return map;
    }

    /**
     * Checks the owners parties to see if the given player is a member.
     */
    public static boolean doPlayersShareParty(OfflinePlayer owner, OfflinePlayer player){
        PlayerPartyStorage storage = Config.getStore().parties.get(owner.getUniqueId().toString());
        if (storage != null){
            for (PlayerParty ownedParty : storage.ownedParties.values()) {
                if (ownedParty.getMembers().contains(player)) return true;
            }
        }
        return false;
    }

    public OfflinePlayer getOwner() {
        return owner;
    }

    public HashMap<String, PlayerParty> getOwnedParties() {
        return ownedParties;
    }

    public Collection<PlayerParty> getOwnedPartiesCollection() {
        return ownedParties.values();
    }

    public List<String> getOwnedPartiesAsStrings(){
        List<String> strings = new ArrayList<>();
        ownedParties.values().forEach(party -> strings.add(party.getPartyName()));
        return strings;
    }
}
