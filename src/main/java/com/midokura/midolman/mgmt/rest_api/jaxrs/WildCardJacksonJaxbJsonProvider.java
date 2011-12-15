/*
 * @(#)WildCardJacksonJaxbJsonProvider        1.6 11/11/11
 *
 * Copyright 2011 Midokura KK
 */
package com.midokura.midolman.mgmt.rest_api.jaxrs;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;

import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;

/**
 * A ConfiguredJacksonJaxbJsonProvider that consumes and produces wildcard media types.
 *
 * @version 1.6 11 Nov 2011
 * @author Ryu Ishimoto
 */
@Provider
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.WILDCARD)
public class WildCardJacksonJaxbJsonProvider extends ConfiguredJacksonJaxbJsonProvider {
}
