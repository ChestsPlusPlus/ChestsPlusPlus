package com.jamesdpeters.minecraft.chests.database.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.bukkit.OfflinePlayer;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"NAME", "OWNER_PLAYERUUID"})
})
public class PlayerParty {

    @Id
    @GeneratedValue
    private Long partyId;

    @Column(nullable = false)
    private String name;

    @JoinColumn(nullable = false)
    @ManyToOne(cascade = CascadeType.ALL, optional = false)
    private CppPlayer owner;

    @JoinTable(name = "PLAYER_PARTY_CPP_PLAYER",
            joinColumns = @JoinColumn(name = "PLAYER_PARTY_ID"),
            inverseJoinColumns = @JoinColumn(name = "CPP_PLAYER_ID"))
    @ManyToMany(cascade = {CascadeType.ALL})
    @ToString.Exclude
    private Set<CppPlayer> members = new HashSet<>();

    public boolean removeMember(OfflinePlayer player) {
        return members.removeIf(p -> p.getPlayerUUID().equals(player.getUniqueId()));
    }

    public boolean addMember(CppPlayer player) {
        return members.add(player);
    }

    public Set<OfflinePlayer> getBukkitMembers() {
        return members.stream().map(CppPlayer::getOfflinePlayer).collect(Collectors.toSet());
    }

    public boolean isMember(OfflinePlayer player) {
        return owner.getOfflinePlayer() == player || members.stream().anyMatch(cppPlayer -> cppPlayer.getOfflinePlayer() == player);
    }
}
