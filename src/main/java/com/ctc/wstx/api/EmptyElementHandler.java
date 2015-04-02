package com.ctc.wstx.api;

import java.util.Set;
import java.util.TreeSet;

/**
 * Optional handler used to determine if a specific empty element (by name) should
 * be allowed to use the self-closing syntax instead of having a separate end tag.
 * 
 * @since 4.1
 */
public interface EmptyElementHandler
{
    /**
     * @param prefix The element's namespace prefix, null if not set
     * @param localName The element's local name
     * @param nsURI The elements's namespace URI, null if not set
     * @param allowEmpty The allow empty setting specified by the caller.
     * @return True if the empty element can be self-closing. False if a separate end tag should be written.
     */
    public boolean allowEmptyElement(String prefix, String localName, String nsURI, boolean allowEmpty);
    
    /**
     * Handler that uses a Set of Strings. If the local part of the element's QName is contained
     * in the Set the element is allowed to be empty.
     *<p>
     * Users of this class are encouraged to use a {@link TreeSet} with the {@link String#CASE_INSENSITIVE_ORDER}
     * comparator if case-insensitive comparison is needed (like when dealing with HTML tags).
     */
    public static class SetEmptyElementHandler
        implements EmptyElementHandler
    {
        final protected Set<String> mEmptyElements;

        public SetEmptyElementHandler(Set<String> emptyElements)
        {
            mEmptyElements = emptyElements;
        }

        @Override
        public boolean allowEmptyElement(String prefix, String localName, String nsURI, boolean allowEmpty)
        {
            return mEmptyElements.contains(localName);
        }
    }
    
    /**
     * HTML specific empty element handler.
     * Extends the {@link SetEmptyElementHandler} and configures
     * the HTML elements that must be self-closing according to the W3C:
     * http://www.w3.org/TR/html4/index/elements.html
     *<p>
     * Note that element name comparison is case-insensitive as required
     * by HTML specification.
     */
    public static class HtmlEmptyElementHandler
        extends SetEmptyElementHandler
    {
        private final static HtmlEmptyElementHandler sInstance = new HtmlEmptyElementHandler();

        public static HtmlEmptyElementHandler getInstance() { return sInstance; }
        
        protected HtmlEmptyElementHandler()
        {
            super(new TreeSet<String>(String.CASE_INSENSITIVE_ORDER));
            mEmptyElements.add("area");
            mEmptyElements.add("base");
            mEmptyElements.add("basefont");
            mEmptyElements.add("br");
            mEmptyElements.add("col");
            mEmptyElements.add("frame");
            mEmptyElements.add("hr");
            mEmptyElements.add("input");
            mEmptyElements.add("img");
            mEmptyElements.add("isindex");
            mEmptyElements.add("link");
            mEmptyElements.add("meta");
            mEmptyElements.add("param");
        }
    }
}

