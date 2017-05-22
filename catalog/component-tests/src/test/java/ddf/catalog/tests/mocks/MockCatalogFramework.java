/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.catalog.tests.mocks;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.catalog.CatalogFramework;
import ddf.catalog.content.operation.CreateStorageRequest;
import ddf.catalog.content.operation.UpdateStorageRequest;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.data.Metacard;
import ddf.catalog.federation.FederationException;
import ddf.catalog.federation.FederationStrategy;
import ddf.catalog.operation.CreateRequest;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteRequest;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.QueryRequest;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.Request;
import ddf.catalog.operation.ResourceRequest;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.operation.SourceInfoRequest;
import ddf.catalog.operation.SourceInfoResponse;
import ddf.catalog.operation.SourceResponse;
import ddf.catalog.operation.UpdateRequest;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.resource.ResourceNotFoundException;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.transform.CatalogTransformerException;

public class MockCatalogFramework implements CatalogFramework, BundleActivator {
    private static final Logger LOG = LoggerFactory.getLogger(MockCatalogFramework.class);

    public List<Request> createRequests = new ArrayList<>();

    private ServiceRegistration registration;

    @Override
    public CreateResponse create(CreateStorageRequest createRequest)
            throws IngestException, SourceUnavailableException {
        LOG.debug("CREATE REQUEST");
        createRequests.add(createRequest);
        return null;
    }

    @Override
    public CreateResponse create(CreateRequest createRequest)
            throws IngestException, SourceUnavailableException {
        LOG.debug("CREATE REQUEST");
        createRequests.add(createRequest);
        return null;
    }

    @Override
    public DeleteResponse delete(DeleteRequest deleteRequest)
            throws IngestException, SourceUnavailableException {
        LOG.debug("DELETE REQUEST");
        return null;
    }

    @Override
    public ResourceResponse getEnterpriseResource(ResourceRequest request)
            throws IOException, ResourceNotFoundException, ResourceNotSupportedException {
        LOG.debug("DELETE REQUEST");
        return null;
    }

    @Override
    public Map<String, Set<String>> getEnterpriseResourceOptions(String metacardId)
            throws ResourceNotFoundException {
        LOG.debug("GET ENTERPRISE RESOURCE OPTIONS");
        return null;
    }

    @Override
    public ResourceResponse getLocalResource(ResourceRequest request)
            throws IOException, ResourceNotFoundException, ResourceNotSupportedException {
        LOG.debug("GET LOCAL RESOURCE");
        return null;
    }

    @Override
    public Map<String, Set<String>> getLocalResourceOptions(String metacardId)
            throws ResourceNotFoundException {
        LOG.debug("GET LOCAL RESOURCE OPTIONS");
        return null;
    }

    @Override
    public ResourceResponse getResource(ResourceRequest request, String resourceSiteName)
            throws IOException, ResourceNotFoundException, ResourceNotSupportedException {
        LOG.debug("GET RESOURCE");
        return null;
    }

    @Override
    public Map<String, Set<String>> getResourceOptions(String metacardId, String sourceId)
            throws ResourceNotFoundException {
        LOG.debug("GET RESOURCE OPTIONS");
        return null;
    }

    @Override
    public Set<String> getSourceIds() {
        LOG.debug("GET SOURCE IDS");
        return null;
    }

    @Override
    public SourceInfoResponse getSourceInfo(SourceInfoRequest sourceInfoRequest)
            throws SourceUnavailableException {
        LOG.debug("GET SOURCE INFO");
        return null;
    }

    @Override
    public QueryResponse query(QueryRequest query)
            throws UnsupportedQueryException, SourceUnavailableException, FederationException {
        LOG.debug("QUERY");
        return null;
    }

    @Override
    public QueryResponse query(QueryRequest queryRequest, FederationStrategy strategy)
            throws SourceUnavailableException, UnsupportedQueryException, FederationException {
        LOG.debug("QUERY");
        return null;
    }

    @Override
    public BinaryContent transform(Metacard metacard, String transformerId,
            Map<String, Serializable> requestProperties) throws CatalogTransformerException {
        LOG.debug("TRANSFORM");
        return null;
    }

    @Override
    public BinaryContent transform(SourceResponse response, String transformerId,
            Map<String, Serializable> requestProperties) throws CatalogTransformerException {
        LOG.debug("TRANSFORM");
        return null;
    }

    @Override
    public UpdateResponse update(UpdateStorageRequest updateRequest)
            throws IngestException, SourceUnavailableException {
        LOG.debug("UPDATE");
        return null;
    }

    @Override
    public UpdateResponse update(UpdateRequest updateRequest)
            throws IngestException, SourceUnavailableException {
        LOG.debug("UPDATE");
        return null;
    }

    @Override
    public String getVersion() {
        LOG.debug("GET VERSION");
        return null;
    }

    @Override
    public String getId() {
        LOG.debug("GET ID");
        return null;
    }

    @Override
    public String getTitle() {
        LOG.debug("GET TITLE");
        return null;
    }

    @Override
    public String getDescription() {
        LOG.debug("GET DESCRIPTION");
        return null;
    }

    @Override
    public String getOrganization() {
        LOG.debug("GET ORGANIZATION");
        return null;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        Dictionary properties = new Properties();
        properties.put("name", "Mock Catalog Framework");
        registration = context.registerService(CatalogFramework.class, this, properties);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        registration.unregister();
    }
}
