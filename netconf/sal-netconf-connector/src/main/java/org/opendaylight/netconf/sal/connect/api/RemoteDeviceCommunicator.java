/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import com.google.common.util.concurrent.FluentFuture;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;

public interface RemoteDeviceCommunicator<M> extends AutoCloseable {

    FluentFuture<RpcResult<M>> sendRequest(M message, QName rpc);

    @Override
    void close();
}
