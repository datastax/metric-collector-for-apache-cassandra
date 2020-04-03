package com.datastax.mcac.utils;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.cassandra.service.StorageService;

/**
 * Supplies the host_id associated with the node, and memoizes the response
 * so we only do this once as it never changes
 */
public class LocalHostIdSupplier
{
    public static String getHostId()
    {
        return HOST_ID_SUPPLIER.get();
    }

    private static final Supplier<String> HOST_ID_SUPPLIER = Suppliers.memoize(() -> {
        try
        {
            return StorageService.instance.getLocalHostId();
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    });
}
