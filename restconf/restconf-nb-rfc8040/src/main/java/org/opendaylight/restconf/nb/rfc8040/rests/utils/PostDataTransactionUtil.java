/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.FluentFuture;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedMapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Util class to post data to DS.
 *
 */
public final class PostDataTransactionUtil {
    private PostDataTransactionUtil() {
        // Hidden on purpose
    }

    /**
     * Check mount point and prepare variables for post data. Close {@link DOMTransactionChain} if any inside of object
     * {@link RestconfStrategy} provided as a parameter.
     *
     * @param uriInfo       uri info
     * @param payload       data
     * @param strategy      Object that perform the actual DS operations
     * @param schemaContext reference to actual {@link EffectiveModelContext}
     * @param point         point
     * @param insert        insert
     * @return {@link Response}
     */
    public static Response postData(final UriInfo uriInfo, final NormalizedNodeContext payload,
                                    final RestconfStrategy strategy,
                                    final EffectiveModelContext schemaContext, final String insert,
                                    final String point) {
        final FluentFuture<? extends CommitInfo> future = submitData(
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(),
                strategy, schemaContext, insert, point);
        final URI location = resolveLocation(uriInfo, strategy.getInstanceIdentifier(),
                schemaContext, payload.getData());
        final ResponseFactory dataFactory = new ResponseFactory(Status.CREATED).location(location);
        //This method will close transactionChain if any
        FutureCallbackTx.addCallback(future, RestconfDataServiceConstant.PostData.POST_TX_TYPE, dataFactory,
                strategy.getTransactionChain());
        return dataFactory.build();
    }

    /**
     * Post data by type.
     *
     * @param path          path
     * @param data          data
     * @param strategy      object that perform the actual DS operations
     * @param schemaContext schema context of data
     * @param point         query parameter
     * @param insert        query parameter
     * @return {@link FluentFuture}
     */
    private static FluentFuture<? extends CommitInfo> submitData(final YangInstanceIdentifier path,
                                                                 final NormalizedNode<?, ?> data,
                                                                 final RestconfStrategy strategy,
                                                                 final EffectiveModelContext schemaContext,
                                                                 final String insert, final String point) {
        strategy.prepareReadWriteExecution();
        if (insert == null) {
            makePost(path, data, schemaContext, strategy);
            return strategy.commit();
        }

        final DataSchemaNode schemaNode = PutDataTransactionUtil.checkListAndOrderedType(schemaContext, path);
        switch (insert) {
            case "first":
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, strategy, schemaNode);
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy);
                        return strategy.commit();
                    }

                    strategy.delete(LogicalDatastoreType.CONFIGURATION, path.getParent().getParent());
                    simplePost(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext, strategy);
                    makePost(path, readData, schemaContext, strategy);
                    return strategy.commit();
                } else {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, strategy, schemaNode);

                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy);
                        return strategy.commit();
                    }

                    strategy.delete(LogicalDatastoreType.CONFIGURATION, path.getParent().getParent());
                    simplePost(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext, strategy);
                    makePost(path, readData, schemaContext, strategy);
                    return strategy.commit();
                }
            case "last":
                makePost(path, data, schemaContext, strategy);
                return strategy.commit();
            case "before":
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, strategy, schemaNode);
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy);
                        return strategy.commit();
                    }

                    insertWithPointListPost(LogicalDatastoreType.CONFIGURATION, path,
                            data, schemaContext, point, readList, true, strategy);
                    return strategy.commit();
                } else {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, strategy, schemaNode);

                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy);
                        return strategy.commit();
                    }

                    insertWithPointLeafListPost(LogicalDatastoreType.CONFIGURATION,
                            path, data, schemaContext, point, readLeafList, true, strategy);
                    return strategy.commit();
                }
            case "after":
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, strategy, schemaNode);
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy);
                        return strategy.commit();
                    }

                    insertWithPointListPost(LogicalDatastoreType.CONFIGURATION, path,
                            data, schemaContext, point, readList, false, strategy);
                    return strategy.commit();
                } else {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, strategy, schemaNode);

                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy);
                        return strategy.commit();
                    }

                    insertWithPointLeafListPost(LogicalDatastoreType.CONFIGURATION,
                            path, data, schemaContext, point, readLeafList, true, strategy);
                    return strategy.commit();
                }
            default:
                throw new RestconfDocumentedException(
                    "Used bad value of insert parameter. Possible values are first, last, before or after, but was: "
                            + insert, RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.BAD_ATTRIBUTE);
        }
    }

    private static void insertWithPointLeafListPost(final LogicalDatastoreType datastore,
                                                    final YangInstanceIdentifier path,
                                                    final NormalizedNode<?, ?> payload,
                                                    final EffectiveModelContext schemaContext, final String point,
                                                    final OrderedLeafSetNode<?> readLeafList,
                                                    final boolean before, final RestconfStrategy strategy) {
        strategy.delete(datastore, path.getParent().getParent());
        final InstanceIdentifierContext<?> instanceIdentifier =
                ParserIdentifier.toInstanceIdentifier(point, schemaContext, Optional.empty());
        int lastItemPosition = 0;
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (nodeChild.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent().getParent());
        strategy.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                TransactionUtil.checkItemDoesNotExists(strategy, datastore, path,
                        RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                strategy.create(datastore, path, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(nodeChild.getIdentifier());
            TransactionUtil.checkItemDoesNotExists(strategy, datastore, childPath,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);
            strategy.create(datastore, childPath, nodeChild);
            lastInsertedPosition++;
        }
    }

    private static void insertWithPointListPost(final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
                                                final NormalizedNode<?, ?> payload,
                                                final EffectiveModelContext schemaContext, final String point,
                                                final MapNode readList, final boolean before,
                                                final RestconfStrategy strategy) {
        strategy.delete(datastore, path.getParent().getParent());
        final InstanceIdentifierContext<?> instanceIdentifier =
                ParserIdentifier.toInstanceIdentifier(point, schemaContext, Optional.empty());
        int lastItemPosition = 0;
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (mapEntryNode.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent().getParent());
        strategy.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                TransactionUtil.checkItemDoesNotExists(strategy, datastore, path,
                        RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                strategy.create(datastore, path, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(mapEntryNode.getIdentifier());
            TransactionUtil.checkItemDoesNotExists(strategy, datastore, childPath,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);
            strategy.create(datastore, childPath, mapEntryNode);
            lastInsertedPosition++;
        }
    }

    private static void makePost(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data,
                                 final SchemaContext schemaContext, final RestconfStrategy strategy) {
        if (data instanceof MapNode) {
            boolean merge = false;
            for (final MapEntryNode child : ((MapNode) data).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                TransactionUtil.checkItemDoesNotExists(strategy, LogicalDatastoreType.CONFIGURATION, childPath,
                        RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                if (!merge) {
                    merge = true;
                    TransactionUtil.ensureParentsByMerge(path, schemaContext, strategy);
                    final NormalizedNode<?, ?> emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
                    strategy.merge(LogicalDatastoreType.CONFIGURATION,
                            YangInstanceIdentifier.create(emptySubTree.getIdentifier()), emptySubTree);
                }
                strategy.create(LogicalDatastoreType.CONFIGURATION, childPath, child);
            }
        } else {
            TransactionUtil.checkItemDoesNotExists(strategy, LogicalDatastoreType.CONFIGURATION, path,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);

            TransactionUtil.ensureParentsByMerge(path, schemaContext, strategy);
            strategy.create(LogicalDatastoreType.CONFIGURATION, path, data);
        }
    }

    /**
     * Get location from {@link YangInstanceIdentifier} and {@link UriInfo}.
     *
     * @param uriInfo                uri info
     * @param yangInstanceIdentifier reference to {@link InstanceIdentifierContext}
     * @param schemaContext          reference to {@link SchemaContext}
     * @return {@link URI}
     */
    private static URI resolveLocation(final UriInfo uriInfo, final InstanceIdentifierContext<?> yangInstanceIdentifier,
                                       final EffectiveModelContext schemaContext, final NormalizedNode<?, ?> data) {
        if (uriInfo == null) {
            return null;
        }

        YangInstanceIdentifier path = yangInstanceIdentifier.getInstanceIdentifier();

        if (data instanceof MapNode) {
            final Collection<MapEntryNode> children = ((MapNode) data).getValue();
            if (!children.isEmpty()) {
                path = path.node(children.iterator().next().getIdentifier());
            }
        }

        return uriInfo.getBaseUriBuilder()
                .path("data")
                .path(ParserIdentifier.stringFromYangInstanceIdentifier(path, schemaContext))
                .build();
    }

    private static void simplePost(final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
                                   final NormalizedNode<?, ?> payload,
                                   final SchemaContext schemaContext, final RestconfStrategy strategy) {
        TransactionUtil.checkItemDoesNotExists(strategy, datastore, path,
                RestconfDataServiceConstant.PostData.POST_TX_TYPE);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, strategy);
        strategy.create(datastore, path, payload);
    }
}
