/*
 * Copyright (c) 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.api;

import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.common.OperationFailedException;

/**
 * Provides restconf CRUD operations via code with input/output data in JSON format.
 *
 * @author Thomas Pantelis.
 */
public interface JSONRestconfService {
    /**
     * The data tree root path.
     */
    String ROOT_PATH = null;

    /**
     * Issues a restconf PUT request to the configuration data store.
     *
     * @param uriPath the yang instance identifier path, eg "opendaylight-inventory:nodes/node/device-id".
     *       To specify the root, use {@link #ROOT_PATH}.
     * @param payload the payload data in JSON format.
     * @throws OperationFailedException if the request fails.
     */
    void put(String uriPath, @NonNull String payload) throws OperationFailedException;

    /**
     * Issues a restconf POST request to the configuration data store.
     *
     * @param uriPath the yang instance identifier path, eg "opendaylight-inventory:nodes/node/device-id".
     *       To specify the root, use {@link #ROOT_PATH}.
     * @param payload the payload data in JSON format.
     * @throws OperationFailedException if the request fails.
     */
    void post(String uriPath, @NonNull String payload) throws OperationFailedException;

    /**
     * Issues a restconf DELETE request to the configuration data store.
     *
     * @param uriPath the yang instance identifier path, eg "opendaylight-inventory:nodes/node/device-id".
     *       To specify the root, use {@link #ROOT_PATH}.
     * @throws OperationFailedException if the request fails.
     */
    void delete(String uriPath) throws OperationFailedException;

    /**
     * Issues a restconf GET request to the given data store.
     *
     * @param uriPath the yang instance identifier path, eg "opendaylight-inventory:nodes/node/device-id".
     *       To specify the root, use {@link #ROOT_PATH}.
     * @param datastoreType the data store type to read from.
     * @return an Optional containing the data in JSON format if present.
     * @throws OperationFailedException if the request fails.
     */
    Optional<String> get(String uriPath, LogicalDatastoreType datastoreType)
            throws OperationFailedException;

    /**
     * Invokes a yang-defined RPC.
     *
     * @param uriPath the path representing the RPC to invoke, eg "toaster:make-toast".
     * @param input the input in JSON format if the RPC takes input.
     * @return an Optional containing the output in JSON format if the RPC returns output.
     * @throws OperationFailedException if the request fails.
     */
    Optional<String> invokeRpc(@NonNull String uriPath, Optional<String> input) throws OperationFailedException;

    /**
     * Issues a restconf PATCH request to the configuration data store.
     *
     * @param uriPath the yang instance identifier path, eg "opendaylight-inventory:nodes/node/device-id".
     *       To specify the root, use {@link #ROOT_PATH}.
     * @param payload the payload data in JSON format.
     * @return an Optional containing the patch response data in JSON format.
     * @throws OperationFailedException if the request fails.
     */
    Optional<String> patch(@NonNull String uriPath, @NonNull String payload) throws OperationFailedException;
}
