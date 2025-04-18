/* jcifs smb client library in Java
 * Copyright (C) 2002  "Michael B. Allen" <jcifs at samba dot org>
 *                   "Eric Glass" <jcifs at samba dot org>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package jcifs.smb1.http;

import java.io.IOException;

import java.net.UnknownHostException;

import java.util.Enumeration;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;

import jakarta.servlet.http.HttpSession;

import jcifs.smb1.Config;
import jcifs.smb1.UniAddress;
import jcifs.smb1.netbios.NbtAddress;
import jcifs.smb1.smb1.NtlmPasswordAuthentication;
import jcifs.smb1.smb1.SmbAuthException;
import jcifs.smb1.smb1.SmbSession;
import jcifs.smb1.util.Base64;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This servlet may be used with pre-2.3 servlet containers
 * to protect content with NTLM HTTP Authentication. Servlets that
 * extend this abstract base class may be authenticatied against an SMB
 * server or domain controller depending on how the
 * <tt>jcifs.smb1.smb1.client.domain</tt> or <tt>jcifs.smb1.http.domainController</tt>
 * properties are be specified. <b>With later containers the
 * <tt>NtlmHttpFilter</tt> should be used/b>. For custom NTLM HTTP Authentication schemes the <tt>NtlmSsp</tt> may be used.
 * <p>
 * Read <a href="../../../ntlmhttpauth.html">jCIFS NTLM HTTP Authentication and the Network Explorer Servlet</a> related information.
 */

public abstract class NtlmServlet extends HttpServlet {

    private String defaultDomain;

    private String domainController;

    private boolean loadBalance;

    private boolean enableBasic;

    private boolean insecureBasic;

    private String realm;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        /* Set jcifs.smb1 properties we know we want; soTimeout and cachePolicy to 10min.
         */
        Config.setProperty( "jcifs.smb1.smb.client.soTimeout", "300000" );
        Config.setProperty( "jcifs.smb1.netbios.cachePolicy", "600" );

        Enumeration e = config.getInitParameterNames();
        String name;
        while (e.hasMoreElements()) {
            name = (String) e.nextElement();
            if (name.startsWith("jcifs.smb1.")) {
                Config.setProperty(name, config.getInitParameter(name));
            }
        }
        defaultDomain = Config.getProperty("jcifs.smb1.smb.client.domain");
        domainController = Config.getProperty("jcifs.smb1.http.domainController");
        if( domainController == null ) {
            domainController = defaultDomain;
            loadBalance = Config.getBoolean( "jcifs.smb1.http.loadBalance", true );
        }
        enableBasic = Boolean.valueOf(
                Config.getProperty("jcifs.smb1.http.enableBasic")).booleanValue();
        insecureBasic = Boolean.valueOf(
                Config.getProperty("jcifs.smb1.http.insecureBasic")).booleanValue();
        realm = Config.getProperty("jcifs.smb1.http.basicRealm");
        if (realm == null) realm = "jCIFS";
    }

    protected void service(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        UniAddress dc;
        boolean offerBasic = enableBasic &&
                (insecureBasic || request.isSecure());
        String msg = request.getHeader("Authorization");
        if (msg != null && (msg.startsWith("NTLM ") ||
                    (offerBasic && msg.startsWith("Basic ")))) {
            if( loadBalance ) {
                dc = new UniAddress( NbtAddress.getByName( domainController, 0x1C, null ));
            } else {
                dc = UniAddress.getByName( domainController, true );
            }
            NtlmPasswordAuthentication ntlm;
            if (msg.startsWith("NTLM ")) {
                byte[] challenge = SmbSession.getChallenge(dc);
                ntlm = NtlmSsp.authenticate(request, response, challenge);
                if (ntlm == null) return;
            } else {
                String auth = new String(Base64.decode(msg.substring(6)),
                        "US-ASCII");
                int index = auth.indexOf(':');
                String user = (index != -1) ? auth.substring(0, index) : auth;
                String password = (index != -1) ? auth.substring(index + 1) :
                        "";
                index = user.indexOf('\\');
                if (index == -1) index = user.indexOf('/');
                String domain = (index != -1) ? user.substring(0, index) :
                        defaultDomain;
                user = (index != -1) ? user.substring(index + 1) : user;
                ntlm = new NtlmPasswordAuthentication(domain, user, password);
            }
            try {
                SmbSession.logon(dc, ntlm);
            } catch (SmbAuthException sae) {
                response.setHeader("WWW-Authenticate", "NTLM");
                if (offerBasic) {
                    response.addHeader("WWW-Authenticate", "Basic realm=\"" +
                            realm + "\"");
                }
                response.setHeader("Connection", "close");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.flushBuffer();
                return;
            }
            HttpSession ssn = request.getSession();
            ssn.setAttribute("NtlmHttpAuth", ntlm);
            ssn.setAttribute( "ntlmdomain", ntlm.getDomain() );
            ssn.setAttribute( "ntlmuser", ntlm.getUsername() );
        } else {
            HttpSession ssn = request.getSession(false);
            if (ssn == null || ssn.getAttribute("NtlmHttpAuth") == null) {
                response.setHeader("WWW-Authenticate", "NTLM");
                if (offerBasic) {
                    response.addHeader("WWW-Authenticate", "Basic realm=\"" +
                            realm + "\"");
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.flushBuffer();
                return;
            }
        }
        super.service(request, response);
    }
}

