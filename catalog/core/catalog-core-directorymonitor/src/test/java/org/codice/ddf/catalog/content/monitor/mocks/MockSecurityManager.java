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
package org.codice.ddf.catalog.content.monitor.mocks;

import java.util.Dictionary;
import java.util.Properties;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import ddf.security.Subject;
import ddf.security.service.SecurityManager;
import ddf.security.service.SecurityServiceException;

public class MockSecurityManager implements SecurityManager, BundleActivator {
    private ServiceRegistration<SecurityManager> registration;

    @Override
    public Subject getSubject(Object token) throws SecurityServiceException {
        return null;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        Dictionary properties = new Properties();
        registration = context.registerService(SecurityManager.class, this, properties);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        registration.unregister();
    }
}
