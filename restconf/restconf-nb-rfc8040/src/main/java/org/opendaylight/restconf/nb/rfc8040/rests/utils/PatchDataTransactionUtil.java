/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FluentFuture;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant.PatchData;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PatchDataTransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PatchDataTransactionUtil.class);

    private PatchDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Process edit operations of one {@link PatchContext}. Close {@link DOMTransactionChain} if any inside of object
     * {@link RestconfStrategy} provided as a parameter.
     *
     * @param context       Patch context to be processed
     * @param strategy      object that perform the actual DS operations
     * @param schemaContext Global schema context
     * @return {@link PatchStatusContext}
     */
    public static PatchStatusContext patchData(final PatchContext context, final RestconfStrategy strategy,
                                               final EffectiveModelContext schemaContext) {
        final List<PatchStatusEntity> editCollection = new ArrayList<>();
        boolean noError = true;
        strategy.prepareReadWriteExecution();

        for (final PatchEntity patchEntity : context.getData()) {
            if (noError) {
                switch (patchEntity.getOperation()) {
                    case CREATE:
                        try {
                            createDataWithinTransaction(LogicalDatastoreType.CONFIGURATION,
                                    patchEntity.getTargetNode(), patchEntity.getNode(), strategy, schemaContext);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case DELETE:
                        try {
                            deleteDataWithinTransaction(LogicalDatastoreType.CONFIGURATION, patchEntity.getTargetNode(),
                                    strategy);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case MERGE:
                        try {
                            mergeDataWithinTransaction(LogicalDatastoreType.CONFIGURATION,
                                    patchEntity.getTargetNode(), patchEntity.getNode(), strategy, schemaContext);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case REPLACE:
                        try {
                            replaceDataWithinTransaction(LogicalDatastoreType.CONFIGURATION,
                                    patchEntity.getTargetNode(), patchEntity.getNode(), schemaContext, strategy);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case REMOVE:
                        try {
                            removeDataWithinTransaction(LogicalDatastoreType.CONFIGURATION, patchEntity.getTargetNode(),
                                    strategy);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    default:
                        editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                false, Lists.newArrayList(new RestconfError(ErrorType.PROTOCOL,
                                ErrorTag.OPERATION_NOT_SUPPORTED, "Not supported Yang Patch operation"))));
                        noError = false;
                        break;
                }
            } else {
                break;
            }
        }

        // if no errors then submit transaction, otherwise cancel
        if (noError) {
            final ResponseFactory response = new ResponseFactory(Status.OK);
            final FluentFuture<? extends CommitInfo> future = strategy.commit();

            try {
                //This method will close transactionChain if any
                FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response, strategy.getTransactionChain());
            } catch (final RestconfDocumentedException e) {
                // if errors occurred during transaction commit then patch failed and global errors are reported
                return new PatchStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection), false,
                        Lists.newArrayList(e.getErrors()));
            }

            return new PatchStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection),
                    true, null);
        } else {
            strategy.cancel();
            return new PatchStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection),
                    false, null);
        }
    }

    /**
     * Create data within one transaction, return error if already exists.
     *
     * @param dataStore     Datastore to write data to
     * @param path          Path for data to be created
     * @param payload       Data to be created
     * @param strategy      Object that perform the actual DS operations
     * @param schemaContext Global schema context
     */
    private static void createDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                    final YangInstanceIdentifier path,
                                                    final NormalizedNode<?, ?> payload,
                                                    final RestconfStrategy strategy,
                                                    final EffectiveModelContext schemaContext) {
        LOG.trace("POST {} within Restconf Patch: {} with payload {}", dataStore.name(), path, payload);
        createData(payload, schemaContext, path, strategy, dataStore, true);
    }

    /**
     * Check if data exists and remove it within one transaction.
     *
     * @param dataStore            Datastore to delete data from
     * @param path                 Path for data to be deleted
     * @param strategy             Object that perform the actual DS operations
     */
    private static void deleteDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                    final YangInstanceIdentifier path,
                                                    final RestconfStrategy strategy) {
        LOG.trace("Delete {} within Restconf Patch: {}", dataStore.name(), path);
        checkItemExistsWithinTransaction(strategy, dataStore, path);
        strategy.delete(dataStore, path);
    }

    /**
     * Merge data within one transaction.
     *
     * @param dataStore     Datastore to merge data to
     * @param path          Path for data to be merged
     * @param payload       Data to be merged
     * @param strategy      Object that perform the actual DS operations
     * @param schemaContext Global schema context
     */
    private static void mergeDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                   final YangInstanceIdentifier path,
                                                   final NormalizedNode<?, ?> payload,
                                                   final RestconfStrategy strategy,
                                                   final EffectiveModelContext schemaContext) {
        LOG.trace("Merge {} within Restconf Patch: {} with payload {}", dataStore.name(), path, payload);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, strategy);
        strategy.merge(dataStore, path, payload);
    }

    /**
     * Do NOT check if data exists and remove it within one transaction.
     *
     * @param dataStore        Datastore to delete data from
     * @param path             Path for data to be deleted
     * @param strategy         Object that perform the actual DS operations
     */
    private static void removeDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                    final YangInstanceIdentifier path,
                                                    final RestconfStrategy strategy) {
        LOG.trace("Remove {} within Restconf Patch: {}", dataStore.name(), path);
        strategy.delete(dataStore, path);
    }

    /**
     * Create data within one transaction, replace if already exists.
     *
     * @param dataStore     Datastore to write data to
     * @param path          Path for data to be created
     * @param payload       Data to be created
     * @param schemaContext Global schema context
     * @param strategy      Object that perform the actual DS operations
     */
    private static void replaceDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                     final YangInstanceIdentifier path,
                                                     final NormalizedNode<?, ?> payload,
                                                     final EffectiveModelContext schemaContext,
                                                     final RestconfStrategy strategy) {
        LOG.trace("PUT {} within Restconf Patch: {} with payload {}", dataStore.name(), path, payload);
        createData(payload, schemaContext, path, strategy, dataStore, false);
    }

    /**
     * Create data within one transaction. If {@code errorIfExists} is set to {@code true} then data will be checked
     * for existence before created, otherwise they will be overwritten.
     *
     * @param payload       Data to be created
     * @param schemaContext Global schema context
     * @param path          Path for data to be created
     * @param strategy      Object that perform the actual DS operations
     * @param dataStore     Datastore to write data to
     * @param errorIfExists Enable checking for existence of data (throws error if already exists)
     */
    private static void createData(final NormalizedNode<?, ?> payload, final EffectiveModelContext schemaContext,
                                   final YangInstanceIdentifier path,
                                   final RestconfStrategy strategy,
                                   final LogicalDatastoreType dataStore, final boolean errorIfExists) {
        if (payload instanceof MapNode) {
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            strategy.merge(dataStore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, strategy);
            for (final MapEntryNode child : ((MapNode) payload).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());

                if (errorIfExists) {
                    checkItemDoesNotExistsWithinTransaction(strategy, dataStore, childPath);
                }

                if (errorIfExists) {
                    strategy.create(dataStore, childPath, child);
                } else {
                    strategy.replace(dataStore, childPath, child);
                }
            }
        } else {
            if (errorIfExists) {
                checkItemDoesNotExistsWithinTransaction(strategy, dataStore, path);
            }

            TransactionUtil.ensureParentsByMerge(path, schemaContext, strategy);
            if (errorIfExists) {
                strategy.create(dataStore, path, payload);
            } else {
                strategy.replace(dataStore, path, payload);
            }
        }
    }

    /**
     * Check if items already exists at specified {@code path}. Throws {@link RestconfDocumentedException} if
     * data does NOT already exists.
     *
     * @param strategy      Object that perform the actual DS operations
     * @param store         Datastore
     * @param path          Path to be checked
     */
    public static void checkItemExistsWithinTransaction(final RestconfStrategy strategy,
                                                        final LogicalDatastoreType store,
                                                        final YangInstanceIdentifier path) {
        final FluentFuture<Boolean> future = strategy.exists(store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response);

        if (!response.result) {
            LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
            throw new RestconfDocumentedException("Data does not exist", ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                    path);
        }
    }

    /**
     * Check if items do NOT already exists at specified {@code path}. Throws {@link RestconfDocumentedException} if
     * data already exists.
     *
     * @param strategy      Object that perform the actual DS operations
     * @param store         Datastore
     * @param path          Path to be checked
     */
    public static void checkItemDoesNotExistsWithinTransaction(final RestconfStrategy strategy,
                                                               final LogicalDatastoreType store,
                                                               final YangInstanceIdentifier path) {
        final FluentFuture<Boolean> future = strategy.exists(store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response);

        if (response.result) {
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RestconfDocumentedException("Data already exists", ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS,
                    path);
        }
    }
}
