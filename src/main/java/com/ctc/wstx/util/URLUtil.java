package com.ctc.wstx.util;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.net.MalformedURLException;
import java.util.regex.Pattern;

public final class URLUtil
{
    /**
     * While URIs that contain pipe are wrong, we'll work around that
     * for [WSTX-275].
     */
    private final static Pattern URI_WINDOWS_FILE_PATTERN = Pattern.compile("^file:///\\p{Alpha}|.*$");

    private URLUtil() { }

    /**
     * Method that tries to figure out how to create valid URL from a system
     * id, without additional contextual information.
     * If we could use URIs this might be easier to do, but they are part
     * of JDK 1.4, and preferably code should only require 1.2 (or maybe 1.3)
     */
    public static URL urlFromSystemId(String sysId)
        throws IOException
    {
        try {
	    sysId = cleanSystemId(sysId);
            /* Ok, does it look like a full URL? For one, you need a colon. Also,
             * to reduce likelihood of collision with Windows paths, let's only
             * accept it if there are 3 preceding other chars...
             * Not sure if Mac might be a problem? (it uses ':' as file path
             * separator, alas, at least prior to MacOS X)
             */
            int ix = sysId.indexOf(':', 0);
            /* Also, protocols are generally fairly short, usually 3 or 4
             * chars (http, ftp, urn); so let's put upper limit of 8 chars too
             */
            if (ix >= 3 && ix <= 8) {
                return new URL(sysId);
            }
            // Ok, let's just assume it's local file reference...
            /* 24-May-2006, TSa: Amazingly, this single call does show in
             *   profiling, for small docs. The problem is that deep down it
             *   tries to check physical file system, to check if the File
             *   pointed to is a directory: and that is (relatively speaking)
             *   a very expensive call. Since in this particular case it
             *   should never be a dir (and/or doesn't matter), let's just
             *   implement conversion locally
             */
            String absPath = new java.io.File(sysId).getAbsolutePath();
            // Need to convert colons/backslashes to regular slashes?
            {
                char sep = File.separatorChar;
                if (sep != '/') {
                    absPath = absPath.replace(sep, '/');
                }
            }
            if (absPath.length() > 0 && absPath.charAt(0) != '/') {
                absPath = "/" + absPath;
            }
            return new URL("file", "", absPath);
        } catch (MalformedURLException e) {
            throwIOException(e, sysId);
            return null; // never gets here
        }
    }

    public static URL urlFromSystemId(String sysId, URL ctxt)
        throws IOException
    {
        if (ctxt == null) {
            return urlFromSystemId(sysId);
        }
        try {
	    sysId = cleanSystemId(sysId);
            return new URL(ctxt, sysId);
        } catch (MalformedURLException e) {
            throwIOException(e, sysId);
            return null; // never gets here
        }
    }

    /**
     * Method that tries to create and return URL that denotes current
     * working directory. Usually used to create a context, when one is
     * not explicitly passed.
     */
    public static URL urlFromCurrentDir()
        throws IOException /* MalformedURLException or such */
    {
        /* This seems to work; independent of whether there happens to
         * be such/file dir or not.
         */
        File parent = new File("a").getAbsoluteFile().getParentFile();
        return toURL(parent);
    }

    /**
     * Method that tries to get a stream (ideally, optimal one) to read from
     * the specified URL.
     * Currently it just means creating a simple file input stream if the
     * URL points to a (local) file, and otherwise relying on URL classes
     * input stream creation method.
     */
    public static InputStream inputStreamFromURL(URL url)
        throws IOException
    {
        if ("file".equals(url.getProtocol())) {
            /* As per [WSTX-82], can not do this if the path refers
             * to a network drive on windows. This fixes the problem;
             * might not be needed on all platforms (NFS?), but should not
             * matter a lot: performance penalty of extra wrapping is more
             * relevant when accessing local file system.
             */
            String host = url.getHost();
            if (host == null || host.length() == 0) {
                /* One more test: if there are quoted characters, need
                 * to decoded [WSTX-207]:
                 */
                String path = url.getPath();
                if (path.indexOf('%') >= 0) {
                    path = URLDecoder.decode(path, "UTF-8");
                }
                return new FileInputStream(path);
            }
        }
        return url.openStream();
    }

    /**
     * Method that tries to get a stream (ideally, optimal one) to write to
     * the resource specified by given URL.
     * Currently it just means creating a simple file output stream if the
     * URL points to a (local) file, and otherwise relying on URL classes
     * input stream creation method.
     */
    public static OutputStream outputStreamFromURL(URL url)
        throws IOException
    {
        if ("file".equals(url.getProtocol())) {
            /* As per [WSTX-82], can not do this if the path refers
             * to a network drive on windows.
             */
            String host = url.getHost();
            if (host == null || host.length() == 0) {
                return new FileOutputStream(url.getPath());
            }
        }
        return url.openConnection().getOutputStream();
    }

    /**
     * Helper method that will convert given file into equivalent URL.
     * Encapsulated as a separate method to allow for working around
     * problems with deprecation of {@link File#toURL} method.
     */
    public static URL toURL(File f)
        throws IOException
    {
        return f.toURI().toURL();
    }

    /*
    ///////////////////////////////////////////
    // Private helper methods
    ///////////////////////////////////////////
    */

    private static String cleanSystemId(final String sysId)
    {
	int ix = sysId.indexOf('|');
	if (ix > 0 && URI_WINDOWS_FILE_PATTERN.matcher(sysId).matches()) {
	    StringBuilder sb = new StringBuilder(sysId);
	    sb.setCharAt(ix, ':');
	    return sb.toString();
	}
	return sysId;
    }

    /**
     * Helper method that tries to fully convert strange URL-specific exception
     * to more general IO exception. Also, to try to use JDK 1.4 feature without
     * creating requirement, uses reflection to try to set the root cause, if
     * we are running on JDK1.4
     */
    private static void throwIOException(MalformedURLException mex, String sysId)
        throws IOException
    {
        String msg = "[resolving systemId '"+sysId+"']: "+mex.toString();
        throw ExceptionUtil.constructIOException(msg, mex);
    }
}
