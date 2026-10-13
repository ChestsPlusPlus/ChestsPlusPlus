package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.persistence.GroupStore;
import com.jamesdpeters.chestsplusplus.persistence.Persistence;

/** The open database and the group store; built once the data has loaded, so neither is ever missing. */
public record Storage(Persistence persistence, GroupStore groupStore) {}
