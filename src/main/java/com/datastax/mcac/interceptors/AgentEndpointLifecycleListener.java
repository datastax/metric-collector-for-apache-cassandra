package com.datastax.mcac.interceptors;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.GossipChangeInformation;
import org.apache.cassandra.service.IEndpointLifecycleSubscriber;

import static com.datastax.mcac.insights.events.GossipChangeInformation.GossipEventType.*;

public class AgentEndpointLifecycleListener extends AbstractInterceptor implements IEndpointLifecycleSubscriber
{
    private static final Logger logger = LoggerFactory.getLogger(AgentEndpointLifecycleListener.class);

    private void sendInsight(GossipChangeInformation info)
    {
        try
        {
            client.get().report(info);
        }
        catch (Exception e)
        {
            logger.info("Problem processing Gossip message", e);
        }
    }

    @Override
    public void onJoinCluster(InetAddress endpoint)
    {
        sendInsight(new GossipChangeInformation(JOINED, endpoint));
    }

    @Override
    public void onLeaveCluster(InetAddress endpoint)
    {
        sendInsight(new GossipChangeInformation(REMOVED, endpoint));
    }

    @Override
    public void onUp(InetAddress endpoint)
    {
        sendInsight(new GossipChangeInformation(ALIVE, endpoint));
    }

    @Override
    public void onDown(InetAddress endpoint)
    {
        sendInsight(new GossipChangeInformation(DEAD, endpoint));
    }

    @Override
    public void onMove(InetAddress endpoint)
    {
        //Ignore
    }
}
