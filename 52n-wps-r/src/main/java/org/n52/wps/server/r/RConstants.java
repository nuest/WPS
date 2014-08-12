package org.n52.wps.server.r;

import org.springframework.http.MediaType;

/**
 * 
 * @author Daniel Nüst
 *
 */
public class RConstants {

    public static final String R_SCRIPT_TYPE_VALUE = "text/x-r";

    public static final MediaType R_SCRIPT_TYPE = MediaType.valueOf(R_SCRIPT_TYPE_VALUE);

    public static final String MIME_TYPE_SOURCE = "text/x-r-source";

}
