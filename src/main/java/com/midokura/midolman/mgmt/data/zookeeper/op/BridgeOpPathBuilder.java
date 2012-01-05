/*
 * @(#)BridgeOpPathBuilder        1.6 12/1/6
 *
 * Copyright 2012 Midokura KK
 */
package com.midokura.midolman.mgmt.data.zookeeper.op;

import java.util.List;
import java.util.UUID;

import org.apache.zookeeper.Op;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.midokura.midolman.mgmt.data.dto.config.BridgeMgmtConfig;
import com.midokura.midolman.mgmt.data.dto.config.BridgeNameMgmtConfig;
import com.midokura.midolman.mgmt.data.zookeeper.io.BridgeSerializer;
import com.midokura.midolman.mgmt.data.zookeeper.path.PathBuilder;
import com.midokura.midolman.state.BridgeZkManager;
import com.midokura.midolman.state.BridgeZkManager.BridgeConfig;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.midolman.state.ZkNodeEntry;
import com.midokura.midolman.state.ZkStateSerializationException;

/**
 * Class to build Op for the bridge paths.
 *
 * @version 1.6 6 Jan 2011
 * @author Ryu Ishimoto
 */
public class BridgeOpPathBuilder {

    private final static Logger log = LoggerFactory
            .getLogger(BridgeOpPathBuilder.class);
    private final BridgeSerializer serializer;
    private final PathBuilder pathBuilder;
    private final BridgeZkManager zkDao;

    /**
     * Constructor
     *
     * @param zkDao
     *            ZkManager object to access ZK data.
     * @param pathBuilder
     *            PathBuilder object to get path data.
     * @param serializer
     *            BridgeSerializer object.
     */
    public BridgeOpPathBuilder(BridgeZkManager zkDao, PathBuilder pathBuilder,
            BridgeSerializer serializer) {
        this.zkDao = zkDao;
        this.pathBuilder = pathBuilder;
        this.serializer = serializer;
    }

    /**
     * Get the bridge create Op object.
     *
     * @param id
     *            ID of the bridge.
     * @param config
     *            BridgeMgmtConfig object to create.
     * @return Op for bridge create.
     */
    public Op getBridgeCreateOp(UUID id, BridgeMgmtConfig config)
            throws ZkStateSerializationException {
        log.debug("BridgeOpPathBuilder.getBridgeCreateOp entered: id=" + id
                + " config=" + config);

        String path = pathBuilder.getBridgePath(id);
        byte[] data = serializer.serialize(config);
        Op op = zkDao.getPersistentCreateOp(path, data);

        log.debug("BridgeOpPathBuilder.getBridgeCreateOp exiting.");
        return op;
    }

    /**
     * Gets a list of Op objects to create a Bridge in Midolman side.
     *
     * @param id
     *            ID of the bridge
     * @param bridge
     *            BridgeConfig object
     * @return List of Op objects.
     * @throws StateAccessException
     *             Data access error.
     */
    public List<Op> getBridgeCreateOps(UUID id, BridgeConfig bridge)
            throws StateAccessException {
        log.debug("BridgeOpPathBuilder.getBridgeCreateOps entered: id=" + id);
        List<Op> ops = zkDao.prepareBridgeCreate(id, bridge);
        log.debug("BridgeOpPathBuilder.getBridgeCreateOps exiting: ops count="
                + ops.size());
        return ops;
    }

    /**
     * Get the bridge delete Op object.
     *
     * @param id
     *            ID of the bridge.
     * @return Op for bridge delete.
     */
    public Op getBridgeDeleteOp(UUID id) {
        log.debug("BridgeOpPathBuilder.getBridgeDeleteOp entered: id={}", id);

        String path = pathBuilder.getBridgePath(id);
        Op op = zkDao.getDeleteOp(path);

        log.debug("BridgeOpPathBuilder.getBridgeDeleteOp exiting.");
        return op;
    }

    /**
     * Gets a list of Op objects to delete a Bridge in Midolman side.
     *
     * @param id
     *            ID of the bridge
     * @return List of Op objects.
     * @throws StateAccessException
     *             Data access error.
     */
    public List<Op> getBridgeDeleteOps(UUID id) throws StateAccessException {
        log.debug("BridgeOpPathBuilder.getBridgeDeleteOps entered: id={}", id);

        BridgeZkManager bridgeZkDao = zkDao;
        ZkNodeEntry<UUID, BridgeConfig> bridgeNode = bridgeZkDao.get(id);
        List<Op> ops = bridgeZkDao.prepareBridgeDelete(bridgeNode);

        log.debug(
                "BridgeOpPathBuilder.getBridgeDeleteOps exiting: ops count={}",
                ops.size());
        return ops;
    }

    /**
     * Get the bridge update Op object.
     *
     * @param id
     *            ID of the bridge.
     * @param config
     *            BridgeMgmtConfig object to set.
     * @return Op for bridge update.
     * @throws ZkStateSerializationException
     *             Serialization error.
     */
    public Op getBridgeSetDataOp(UUID id, BridgeMgmtConfig config)
            throws ZkStateSerializationException {
        log.debug("BridgeOpPathBuilder.getBridgeSetDataOp entered: id=" + id
                + " config=" + config);

        String path = pathBuilder.getBridgePath(id);
        byte[] data = serializer.serialize(config);
        Op op = zkDao.getSetDataOp(path, data);

        log.debug("BridgeOpPathBuilder.getBridgeSetDataOp exiting.");
        return op;
    }

    /**
     * Get the tenant bridge create Op object.
     *
     * @param tenantId
     *            ID of the tenant
     * @param id
     *            ID of the bridge.
     * @return Op for tenant bridge create.
     */
    public Op getTenantBridgeCreateOp(String tenantId, UUID id) {
        log.debug("BridgeOpPathBuilder.getTenantBridgeCreateOp entered: tenantId="
                + tenantId + ", id=" + id);

        String path = pathBuilder.getTenantBridgePath(tenantId, id);
        Op op = zkDao.getPersistentCreateOp(path, null);

        log.debug("BridgeOpPathBuilder.getTenantBridgeCreateOp exiting.");
        return op;
    }

    /**
     * Get the tenant bridge delete Op object.
     *
     * @param tenantId
     *            ID of the tenant
     * @param id
     *            ID of the bridge.
     * @return Op for tenant bridge delete.
     */
    public Op getTenantBridgeDeleteOp(String tenantId, UUID id) {
        log.debug("BridgeOpPathBuilder.getTenantBridgeDeleteOp entered: tenantId="
                + tenantId + ", id=" + id);

        String path = pathBuilder.getTenantBridgePath(tenantId, id);
        Op op = zkDao.getDeleteOp(path);

        log.debug("BridgeOpPathBuilder.getTenantBridgeDeleteOp exiting.");
        return op;
    }

    /**
     * Get the tenant bridge name create Op object.
     *
     * @param tenantId
     *            ID of the tenant
     * @param name
     *            name of the bridge
     * @param config
     *            BridgeMgmtConfig object to set.
     * @return Op for tenant bridge name create.
     */
    public Op getTenantBridgeNameCreateOp(String tenantId, String name,
            BridgeNameMgmtConfig config) throws ZkStateSerializationException {
        log.debug("BridgeOpPathBuilder.getTenantBridgeNameCreateOp entered: tenantId="
                + tenantId + ", name=" + name + ", config=" + config);

        String path = pathBuilder.getTenantBridgeNamePath(tenantId, name);
        byte[] data = serializer.serialize(config);
        Op op = zkDao.getPersistentCreateOp(path, data);

        log.debug("BridgeOpPathBuilder.getTenantBridgeNameCreateOp exiting.");
        return op;
    }

    /**
     * Get the tenant bridge name delete Op object.
     *
     * @param tenantId
     *            ID of the tenant
     * @param name
     *            name of the bridge
     * @return Op for tenant bridge name delete.
     */
    public Op getTenantBridgeNameDeleteOp(String tenantId, String name) {
        log.debug("BridgeOpPathBuilder.getTenantBridgeNameDeleteOp entered: tenantId="
                + tenantId + ", name=" + name);

        String path = pathBuilder.getTenantBridgeNamePath(tenantId, name);
        Op op = zkDao.getDeleteOp(path);

        log.debug("BridgeOpPathBuilder.getTenantBridgeNameDeleteOp exiting.");
        return op;
    }

}
