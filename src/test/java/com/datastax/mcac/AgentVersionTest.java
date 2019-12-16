package com.datastax.mcac;

import com.datastax.driver.core.VersionNumber;
import com.datastax.mcac.utils.AgentVersionSupplier;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class AgentVersionTest
{
    @Test
    public void testAgentVersionIsAvailable()
    {
        String agentVersion = AgentVersionSupplier.getAgentVersion();
        assertNotNull(agentVersion);
        assertFalse(agentVersion.isEmpty());
        assertFalse(agentVersion.contains("SNAPSHOT"));
        /*
         * Will fail if not a valid version number
         */
        VersionNumber.parse(agentVersion);
    }
}
