package net.beaconcontroller.core;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;

import net.beaconcontroller.core.io.OFMessageSafeOutStream;

import org.openflow.io.OFMessageInStream;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;

/**
 *
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface IOFSwitch {
    /**
     *
     * @return
     */
    public OFMessageInStream getInputStream();

    /**
     *
     * @return
     */
    public OFMessageSafeOutStream getOutputStream();

    /**
     *
     * @return
     */
    public SocketChannel getSocketChannel();

    /**
     * Returns the cached OFFeaturesReply message returned by the switch during
     * the initial handshake.
     * @return
     */
    public OFFeaturesReply getFeaturesReply();

    /**
     * Set the OFFeaturesReply message returned by the switch during initial
     * handshake.
     * @param featuresReply
     */
    public void setFeaturesReply(OFFeaturesReply featuresReply);

    /**
     * Get list of all enabled ports. This will typically be different from
     * the list of ports in the OFFeaturesReply, since that one is a static
     * snapshot of the ports at the time the switch connected to the controller
     * whereas this port list also reflects the port status messages that have
     * been received.
     * @return Unmodifiable list of ports
     */
    public List<OFPhysicalPort> getEnabledPorts();

    /**
     * Add or modify a switch port. This is called by the core controller
     * code in response to a OFPortStatus message. It should not typically be
     * called by other beacon applications.
     * @param port
     */
    public void setPort(OFPhysicalPort port);

    /**
     * Delete a port for the switch. This is called by the core controller
     * code in response to a OFPortStatus message. It should not typically be
     * called by other beacon applications.
     * @param portNumber
     */
    public void deletePort(short portNumber);

    /**
     * @param portNumber
     * @return Whether a port is enabled per latest port status message
     * (not configured down nor link down nor in spanning tree blocking state)
     */
    public boolean portEnabled(short portNumber);

    /**
     * @param port
     * @return Whether a port is enabled per latest port status message
     * (not configured down nor link down nor in spanning tree blocking state)
     */
    public boolean portEnabled(OFPhysicalPort port);

    /**
     * Get the datapathId of the switch
     * @return
     */
    public long getId();

    /**
     * Retrieves attributes of this switch
     * @return
     */
    public ConcurrentMap<Object, Object> getAttributes();

    /**
     * Retrieves the date the switch connected to this controller
     * @return the date
     */
    public Date getConnectedSince();

    /**
     * Returns the next available transaction id
     * @return
     */
    public int getNextTransactionId();

    /**
     * Returns a Future object that can be used to retrieve the asynchronous
     * OFStatisticsReply when it is available.
     *
     * @param request statistics request
     * @return Future object wrapping OFStatisticsReply
     * @throws IOException 
     */
    public Future<List<OFStatistics>> getStatistics(OFStatisticsRequest request)
            throws IOException;
}
