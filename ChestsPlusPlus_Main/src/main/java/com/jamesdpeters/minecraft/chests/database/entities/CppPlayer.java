package com.jamesdpeters.minecraft.chests.database.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;

@Entity
@Getter
@ToString
@NoArgsConstructor
@Table(name = "Player")
public class CppPlayer {

    public CppPlayer(UUID uuid) {
        playerUUID = uuid;
    }

    @Id
    private UUID playerUUID;

    @JoinColumn(name = "cpp_player_player_uuid")
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<PlayerParty> ownedParties = new HashSet<>();

    @ManyToMany(mappedBy = "members", cascade = CascadeType.ALL)
    @ToString.Exclude
    private Set<PlayerParty> playerParties = new HashSet<>();

    public OfflinePlayer getOfflinePlayer() {
        return Bukkit.getOfflinePlayer(playerUUID);
    }

    public String getName() {
        return getOfflinePlayer().getName();
    }

    public Optional<PlayerParty> getParty(String partyName) {
        return nonNull(playerParties)
                ? playerParties.stream().filter(playerParty -> playerParty.getName().equals(partyName)).findFirst()
                : Optional.empty();
    }

    public Set<PlayerParty> getAllParties() {
        return Stream.concat(ownedParties.stream(), playerParties.stream()).collect(Collectors.toSet());
    }

    public boolean isMemberOfParty(OfflinePlayer player) {
        return playerParties.stream().anyMatch(playerParty -> playerParty.isMember(player));
    }

    public List<String> getOwnedPartyStrings() {
        return ownedParties.stream().map(PlayerParty::getName).collect(Collectors.toList());
    }
}
