/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.admin.indices.mapping.put;

import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.master.TransportMasterNodeOperationAction;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.MetaDataMappingService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

/**
 * Put mapping action.
 */
public class TransportPutMappingAction extends TransportMasterNodeOperationAction<PutMappingRequest, PutMappingResponse> {

    private final MetaDataMappingService metaDataMappingService;

    @Inject
    public TransportPutMappingAction(Settings settings, TransportService transportService, ClusterService clusterService,
                                     ThreadPool threadPool, MetaDataMappingService metaDataMappingService) {
        super(settings, transportService, clusterService, threadPool);
        this.metaDataMappingService = metaDataMappingService;
    }

    @Override
    protected String executor() {
        // we go async right away
        return ThreadPool.Names.SAME;
    }

    @Override
    protected String transportAction() {
        return PutMappingAction.NAME;
    }

    @Override
    protected PutMappingRequest newRequest() {
        return new PutMappingRequest();
    }

    @Override
    protected PutMappingResponse newResponse() {
        return new PutMappingResponse();
    }

    @Override
    protected void doExecute(PutMappingRequest request, ActionListener<PutMappingResponse> listener) {
        request.indices(clusterService.state().metaData().concreteIndices(request.indices()));
        super.doExecute(request, listener);
    }

    @Override
    protected ClusterBlockException checkBlock(PutMappingRequest request, ClusterState state) {
        return state.blocks().indicesBlockedException(ClusterBlockLevel.METADATA, request.indices());
    }

    @Override
    protected void masterOperation(final PutMappingRequest request, final ClusterState state, final ActionListener<PutMappingResponse> listener) throws ElasticSearchException {
        ClusterState clusterState = clusterService.state();

        // update to concrete indices
        request.indices(clusterState.metaData().concreteIndices(request.indices()));

        metaDataMappingService.putMapping(new MetaDataMappingService.PutRequest(request.indices(), request.type(), request.source()).ignoreConflicts(request.ignoreConflicts()).timeout(request.timeout()).masterTimeout(request.masterNodeTimeout()), new MetaDataMappingService.Listener() {
            @Override
            public void onResponse(MetaDataMappingService.Response response) {
                listener.onResponse(new PutMappingResponse(response.acknowledged()));
            }

            @Override
            public void onFailure(Throwable t) {
                logger.debug("failed to put mappings on indices [{}], type [{}]", t, request.indices(), request.type());
                listener.onFailure(t);
            }
        });
    }
}
