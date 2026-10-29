package com.example.mspedidos.infrastructure.persistence;

import org.hibernate.dialect.Dialect;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

/**
 * Enum como varchar sin CHECK ni ENUM nativo: un valor nuevo en el enum no requiere migrar el esquema
 * (ddl-auto=update nunca altera columnas existentes).
 */
public class PlainVarcharJdbcType extends VarcharJdbcType {

    /** Hace que Hibernate no genere el CHECK ... IN (...) del enum (EnumJavaType solo lo agrega a tipos string). */
    @Override
    public boolean isString() {
        return false;
    }

    @Override
    public String getCheckCondition(String columnName, JavaType<?> javaType,
                                    BasicValueConverter<?, ?> converter, Dialect dialect) {
        return null;
    }
}
