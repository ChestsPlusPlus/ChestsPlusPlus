package com.jamesdpeters.chestsplusplus.persistence;

import java.nio.ByteBuffer;
import java.sql.Types;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.qualifier.QualifiedType;

/** Stores UUIDs as 16-byte BLOBs. */
final class UuidBlob {

    private UuidBlob() {}

    /** Overrides JDBI's built-in UUID handling, which would store them as text. */
    static void register(Jdbi jdbi) {
        jdbi.registerArgument(new AbstractArgumentFactory<UUID>(Types.BLOB) {
            @Override
            protected Argument build(UUID value, ConfigRegistry config) {
                return (position, statement, context) -> statement.setBytes(position, bytes(value));
            }
        });
        jdbi.registerColumnMapper(QualifiedType.of(UUID.class), (row, column, context) -> uuid(row.getBytes(column)));
    }

    static byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }

    static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
