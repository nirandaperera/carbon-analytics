/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
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
package org.wso2.carbon.analytics.eventsink;

import org.wso2.carbon.analytics.datasource.commons.AnalyticsSchema;
import org.wso2.carbon.analytics.eventsink.exception.AnalyticsEventStoreException;
import org.wso2.carbon.analytics.eventsink.internal.AnalyticsEventStoreManager;
import org.wso2.carbon.analytics.eventsink.internal.util.AnalyticsEventSinkUtil;
import org.wso2.carbon.databridge.commons.utils.DataBridgeCommonsUtils;

/**
 * This is the implementation of AnalyticsEventSink OSGI service which is exposed to other components.
 */
public class AnalyticsEventSinkServiceImpl implements AnalyticsEventSinkService {

    @Override
    public void putEventSink(int tenantId, String streamName, String version, AnalyticsSchema analyticsSchema,
                             String recordStoreName) throws AnalyticsEventStoreException {
        String streamId = DataBridgeCommonsUtils.generateStreamId(streamName, version);
        AnalyticsEventStore analyticsEventStore = AnalyticsEventSinkUtil.
                getAnalyticsEventStore(streamName, version, analyticsSchema, recordStoreName);
        AnalyticsEventStore existingEventStore = AnalyticsEventStoreManager.
                getInstance().getAnalyticsEventStore(tenantId,
                analyticsEventStore.getName());
        if (existingEventStore != null) {
            if (existingEventStore.getRecordStore() == null) {
                if (analyticsEventStore.getRecordStore() != null) {
                    throw new AnalyticsEventStoreException("Already event store is configured with primary record store," +
                            " therefore unable to proceed with new event sink for stream :" + streamName + " , version : "
                            + version + " , record store name : " + recordStoreName + ".");
                }
            } else {
                if (analyticsEventStore.getRecordStore() == null) {
                    throw new AnalyticsEventStoreException("Already event store is configured with record store name : "
                            + analyticsEventStore.getRecordStore() + "," +
                            " therefore unable to proceed with new event sink for stream :" + streamName + " , version : "
                            + version + " , with primary record store.");
                }else if (!analyticsEventStore.getRecordStore().equals(existingEventStore.getRecordStore())){
                    throw new AnalyticsEventStoreException("Already event store is configured with record store name : "
                            + analyticsEventStore.getRecordStore() + "," +
                            " therefore unable to proceed with new event sink for stream :" + streamName + " , version : "
                            + version + " , with record store :" + analyticsEventStore.getRecordStore()+".");
                }
            }
            for (String aStream : existingEventStore.getEventSource().getStreamIds()) {
                if (!aStream.equals(streamId)) {
                    analyticsEventStore.getEventSource().getStreamIds().add(aStream);
                }
            }
        }
        putEventStore(tenantId, analyticsEventStore);
    }

    @Override
    public void putEventStore(int tenantId, AnalyticsEventStore eventStore)
            throws AnalyticsEventStoreException {
        AnalyticsEventStoreManager.getInstance().saveEventStoreConfiguration(tenantId, eventStore);
    }

    @Override
    public void removeEventSink(int tenantId, String streamName, String version)
            throws AnalyticsEventStoreException {
        String streamId = DataBridgeCommonsUtils.generateStreamId(streamName, version);
        String eventStoreName = AnalyticsEventSinkUtil.generateAnalyticsTableName(streamName);
        AnalyticsEventStore existingEventStore = AnalyticsEventStoreManager.getInstance().
                getAnalyticsEventStore(tenantId, eventStoreName);
        if (existingEventStore.getEventSource().getStreamIds().contains(streamId)) {
            if (existingEventStore.getEventSource().getStreamIds().size() == 1) {
                AnalyticsEventStoreManager.getInstance().deleteEventStoreConfiguration(tenantId, eventStoreName);
            } else {
                AnalyticsEventStore newAnalyticsEventSink = AnalyticsEventSinkUtil.copyAnalyticsEventStore(existingEventStore);
                newAnalyticsEventSink.getEventSource().getStreamIds().remove(streamId);
                AnalyticsEventStoreManager.getInstance().
                        saveEventStoreConfiguration(tenantId, newAnalyticsEventSink);
            }
        }
    }

    @Override
    public AnalyticsEventStore getEventStore(int tenantId, String streamName) {
        String eventStoreName = AnalyticsEventSinkUtil.generateAnalyticsTableName(streamName);
        return AnalyticsEventStoreManager.getInstance().getAnalyticsEventStore(tenantId, eventStoreName);
    }
}
