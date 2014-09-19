package com.ctc.wstx.io;

import java.io.IOException;
import java.net.URL;

import com.ctc.wstx.util.URLUtil;

/**
 * Helper class that is used to defer construction of {@link URL}
 * to help with cases where real URL is not actually needed, and
 * incoming System Id may not even resolve properly.
 *<p>
 * Note that class is meant to be accessed from a single thread, and
 * is not designed as multi-thread safe. Specifically it is not to be
 * used for caching or as a key, but strictly as System Id for processing
 * of a single XML document.
 * 
 * @since 4.4
 */
public class SystemId
{
	protected URL mURL;
	
	protected String mSystemId;

	protected SystemId(String systemId, URL url) {
		if (systemId == null && url == null) {
			throw new IllegalArgumentException("Can not pass null for both systemId and url");
		}
		mSystemId = systemId;
		mURL = url;
	}
	
	public static SystemId construct(String systemId) {
		return (systemId == null) ? null : new SystemId(systemId, null);
	}

	public static SystemId construct(URL url) {
		return (url == null) ? null : new SystemId(null, url);
	}
	
	public static SystemId construct(String systemId, URL url) {
		if (systemId == null && url == null) {
			return null;
		}
		return new SystemId(systemId, url);
	}

	public URL asURL() throws IOException {
		if (mURL == null) {
			mURL = URLUtil.urlFromSystemId(mSystemId);
		}
		return mURL;
	}

	public boolean hasResolvedURL() {
		return (mURL != null);
	}
	
//	@Override
	public String toString() {
		if (mSystemId == null) {
			mSystemId = mURL.toExternalForm();
		}
		return mSystemId;
	}
}
