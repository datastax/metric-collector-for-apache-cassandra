package com.datastax.mcac.utils;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Returns the build version of the agent, which is looked up from the classpath at most once
 */
public class AgentVersionSupplier
{
    private static final Logger logger = LoggerFactory.getLogger(AgentVersionSupplier.class);

    public static String getAgentVersion()
    {
        return AGENT_VERSION_SUPPLIER.get();
    }

    private static final Supplier<String> AGENT_VERSION_SUPPLIER = Suppliers.memoize(() -> {

        try (InputStream inputStream = AgentVersionSupplier.class.getResourceAsStream("/build_version");
                InputStreamReader isr = new InputStreamReader(inputStream);
                BufferedReader reader = new BufferedReader(isr))
        {
            return reader.readLine();
        }
        catch (Exception ex)
        {
            logger.warn(
                    "Could not find the agent build version on the class path.  Falling back on version information "
                            + "from the package");

            String version = AgentVersionSupplier.class.getPackage().getSpecificationVersion();
            if (version == null || version.isEmpty())
            {
                logger.error("Could not successfully locate version information to associate with this agent");
                return "";
            }
            return version;
        }
    });
}
