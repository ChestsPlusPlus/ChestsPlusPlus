package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * CompleteableFuture that runs the {@link #thenAccept(Consumer)} method on the Bukkit main thread.
 * @param <T>
 */
public class BukkitFuture<T> extends CompletableFuture<T> {

    @Override
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return super.thenAccept(t -> Bukkit.getScheduler().runTask(ChestsPlusPlus.PLUGIN, () -> action.accept(t)));
    }
}
