package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.core.Services;

/** Everything enable built. Only {@link ChestsPlusPlus} and the commands see it; everything else gets its collaborators by constructor. */
public record Components(Services services, Features features, Storage storage, Migration migration) {}
