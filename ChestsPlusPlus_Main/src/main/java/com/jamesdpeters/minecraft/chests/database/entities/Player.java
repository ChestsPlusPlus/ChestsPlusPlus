package com.jamesdpeters.minecraft.chests.database.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@ToString
@NoArgsConstructor
public class Player {

    public Player(UUID uuid) {
        playerUUID = uuid;
    }

    @Id
    private UUID playerUUID;

    @OneToMany(mappedBy = "owner")
    @ToString.Exclude
    private List<PlayerParty> ownedParties;

    @ManyToMany(mappedBy = "members")
    @ToString.Exclude
    private List<PlayerParty> playerParties;

    public OfflinePlayer getOfflinePlayer() {
        return Bukkit.getOfflinePlayer(playerUUID);
    }
}
