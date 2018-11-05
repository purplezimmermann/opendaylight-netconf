/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.util;

import com.google.common.util.concurrent.FutureCallback;
import javax.annotation.Nonnull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple Netconf RPC logging callback.
 */
public class NetconfRpcFutureCallback implements FutureCallback<DOMRpcResult> {
    private static final Logger LOG  = LoggerFactory.getLogger(NetconfRpcFutureCallback.class);

    private final String type;
    private final RemoteDeviceId id;

    public NetconfRpcFutureCallback(final String prefix, final RemoteDeviceId id) {
        this.type = prefix;
        this.id = id;
    }

    @Override
    public void onSuccess(@Nonnull final DOMRpcResult result) {
        if (result.getErrors().isEmpty()) {
            LOG.trace("{}: {} invoked successfully", id, type);
        } else {
            onUnsuccess(result);
        }
    }

    protected void onUnsuccess(final DOMRpcResult result) {
        LOG.warn("{}: {} invoked unsuccessfully: {}", id, type, result.getErrors());
    }

    @Override
    public void onFailure(final Throwable throwable) {
        LOG.warn("{}: {} failed.", id, type, throwable);
    }
}
