
package org.n52.wps.server;

import org.n52.security.service.config.support.SecurityConfigDelegatingServletFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

public class WpsSecurityModule extends ServletModule {

    protected static Logger log = LoggerFactory.getLogger(WpsSecurityModule.class);

    @Override
    protected void configureServlets() {
        log.info("Configure {}", this);
        
        // 52n Security 
        // disables validation of the security-config.xml this is necessary because the Maven Project: org.n52.wps:52n-wps-webapp:3.3.0-SNAPSHOT @ D:\dev\GitHub4w\WPS\52n-wps-webapp\pom.xml mechanism works only if thevalidation is disabled.
        getServletContext().setAttribute("security.config.validation", "false");

        // bind(SecurityConfigContextListener.class).in(Singleton.class);

        // Delegates calls to AuthenticationChainFilter that is defined in the security-config.
        bind(SecurityConfigDelegatingServletFilter.class).in(Singleton.class);
        filter("/webAdmin/*").through(SecurityConfigDelegatingServletFilter.class);
    }

}
