package com.datastax.mcac.utils;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalDcSupplier
{
    private static final Logger logger = LoggerFactory.getLogger(LocalDcSupplier.class);

    private static final Supplier<String> LOCAL_DC_SUPPLIER = Suppliers.memoize(DatabaseDescriptor::getLocalDataCenter);
    public static String getLocalDc()
    {
        return LOCAL_DC_SUPPLIER.get();
    }
}
