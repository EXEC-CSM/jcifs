/* jcifs smb client library in Java
 * Copyright (C) 2002  "Michael B. Allen" <jcifs at samba dot org>
 *                   "Eric Glass" <jcifs at samba dot org>
 *                   "Jason Pugsley" <jcifs at samba dot org>
 *                   "skeetz" <jcifs at samba dot org>
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

package jcifs.http;


import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.bouncycastle.util.encoders.Base64;

import jcifs.CIFSContext;
import jcifs.ntlmssp.NtlmFlags;
import jcifs.ntlmssp.Type1Message;
import jcifs.ntlmssp.Type2Message;
import jcifs.ntlmssp.Type3Message;
import jcifs.smb.NtlmPasswordAuthentication;


/**
 * This class is used internally by <tt>NtlmHttpFilter</tt>,
 * <tt>NtlmServlet</tt>, and <tt>NetworkExplorer</tt> to negotiate password
 * hashes via NTLM SSP with MSIE. It might also be used directly by servlet
 * containers to incorporate similar functionality.
 * <p>
 * How NTLMSSP is used in conjunction with HTTP and MSIE clients is
 * described in an <A HREF="http://www.innovation.ch/java/ntlm.html">NTLM
 * Authentication Scheme for HTTP</A>.
 * <p>
 * Also, read <a
 * href="../../../ntlmhttpauth.html">jCIFS NTLM HTTP Authentication and
 * the Network Explorer Servlet</a> related information.
 */
@Deprecated
public class NtlmSsp implements NtlmFlags {

    /**
     * Calls the static {@link #authenticate(CIFSContext, HttpServletRequest,
     * HttpServletResponse, byte[])} method to perform NTLM authentication
     * for the specified servlet request.
     * 
     * @param tc
     *
     * @param req
     *            The request being serviced.
     * @param resp
     *            The response.
     * @param challenge
     *            The domain controller challenge.
     * @return credentials passed in the servlet request
     * @throws IOException
     *             If an IO error occurs.
     */
    public NtlmPasswordAuthentication doAuthentication ( CIFSContext tc, HttpServletRequest req, HttpServletResponse resp, byte[] challenge )
            throws IOException {
        return authenticate(tc, req, resp, challenge);
    }


    /**
     * Performs NTLM authentication for the servlet request.
     * 
     * @param tc
     *            context to use
     *
     * @param req
     *            The request being serviced.
     * @param resp
     *            The response.
     * @param challenge
     *            The domain controller challenge.
     * @return credentials passed in the servlet request
     * @throws IOException
     *             If an IO error occurs.
     */
    public static NtlmPasswordAuthentication authenticate ( CIFSContext tc, HttpServletRequest req, HttpServletResponse resp, byte[] challenge )
            throws IOException {
        String msg = req.getHeader("Authorization");
        if ( msg != null && msg.startsWith("NTLM ") ) {
            byte[] src = Base64.decode(msg.substring(5));
            if ( src[ 8 ] == 1 ) {
                Type1Message type1 = new Type1Message(src);
                Type2Message type2 = new Type2Message(tc, type1, challenge, null);
                msg = new String(Base64.encode(type2.toByteArray()), "US-ASCII");
                resp.setHeader("WWW-Authenticate", "NTLM " + msg);
            }
            else if ( src[ 8 ] == 3 ) {
                Type3Message type3 = new Type3Message(src);
                byte[] lmResponse = type3.getLMResponse();
                if ( lmResponse == null )
                    lmResponse = new byte[0];
                byte[] ntResponse = type3.getNTResponse();
                if ( ntResponse == null )
                    ntResponse = new byte[0];
                return new NtlmPasswordAuthentication(type3.getDomain(), type3.getUser(), challenge, lmResponse, ntResponse);
            }
        }
        else {
            resp.setHeader("WWW-Authenticate", "NTLM");
        }
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentLength(0);
        resp.flushBuffer();
        return null;
    }

}
