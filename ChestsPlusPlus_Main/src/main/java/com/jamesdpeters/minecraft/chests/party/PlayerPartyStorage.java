package com.jamesdpeters.minecraft.chests.party;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@SerializableAs("PlayerPartyStorage")
public class PlayerPartyStorage implements ConfigurationSerializable {

    private OfflinePlayer owner;
    private HashMap<String, PlayerParty_OLD> ownedParties;

    public PlayerPartyStorage(OfflinePlayer owner){
        this.owner = owner;
        this.ownedParties = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public PlayerPartyStorage(Map<String, Object> map){
        UUID ownerUUID = UUID.fromString((String) map.get("owner"));
        owner = Bukkit.getOfflinePlayer(ownerUUID);

        ownedParties = (HashMap<String, PlayerParty_OLD>) map.get("ownedParties");
        if (ownedParties == null) ownedParties = new HashMap<>();
    }

    @Override
    public Map<String, Object> serialize() {
        HashMap<String, Object> map = new LinkedHashMap<>();
        map.put("owner", owner.getUniqueId().toString());
        map.put("ownedParties", ownedParties);
        return map;
    }
//
//    /**
//     * Checks the owners parties to see if the given cppPlayer is a member.
//     */
//    public static boolean doPlayersShareParty(OfflinePlayer owner, OfflinePlayer player){
//        PlayerPartyStorage storage = Config.getStore().parties.get(owner.getUniqueId().toString());
//        if (storage != null){
//            for (PlayerParty_OLD ownedParty : storage.ownedParties.values()) {
//                if (ownedParty.getMembers().contains(player)) return true;
//            }
//        }
//        return false;
//    }
//
//    public OfflinePlayer getOwner() {
//        return owner;
//    }
//
//    public HashMap<String, PlayerParty_OLD> getOwnedParties() {
//        return ownedParties;
//    }
//
//    public List<PlayerParty_OLD> getOwnedPartiesList() {
//        return new ArrayList<>(ownedParties.values());
//    }
//
//    public Collection<PlayerParty_OLD> getOwnedPartiesCollection() {
//        return ownedParties.values();
//    }
//
//    public List<String> getOwnedPartiesAsStrings(){
//        List<String> strings = new ArrayList<>();
//        ownedParties.values().forEach(party -> strings.add(party.getPartyName()));
//        return strings;
//    }
//
//    /**
//     * Returns a List of Parties this cppPlayer is a member of, not including their owned parties.
//     * @return
//     */
//    public List<PlayerParty_OLD> getPartiesMemberOf() {
//        // Create list containing all owned parties
//        List<PlayerParty_OLD> parties = new ArrayList<>();
//
//        Config.getStore().parties.values().forEach(playerPartyStorage -> {
//            playerPartyStorage.getOwnedPartiesCollection().forEach(party -> {
//                if (party.isMember(getOwner())) parties.add(party);
//            });
//        });
//
//        return parties;
//    }
//
//    /**
//     * Returns ALL parties this cppPlayer is a member of;.
//     * @return
//     */
//    public List<PlayerParty_OLD> getAllParties() {
//        // Create list containing all owned parties
//        List<PlayerParty_OLD> parties = getPartiesMemberOf();
//        parties.addAll(getOwnedPartiesCollection());
//
//        return parties;
//    }
}
