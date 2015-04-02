package com.ctc.wstx.dom;

import javax.xml.namespace.NamespaceContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.ctc.wstx.sw.OutputElementBase;
import com.ctc.wstx.util.BijectiveNsMap;

/**
 * Context object that holds information about an open element
 * (one for which START_ELEMENT has been sent, but no END_ELEMENT)
 */
public final class DOMOutputElement
    extends OutputElementBase
{
    /**
     * Reference to the parent element, element enclosing this element.
     * Null for root element.
     * Non-final to allow temporary pooling
     * (on per-writer basis, to keep these short-lived).
     */
    private DOMOutputElement mParent;

    /**
     * DOM node that is the root under which content is written, in case
     * where there is no parent (mParent == null). If mParent is not null,
     * this will be null.
     * Value is of type
     * {@link Document}, {@link DocumentFragment} or {@link Element}
     */
    private final Node mRootNode;
    
    /**
     * Actual DOM element for which this element object acts as a proxy.
     */
    private Element mElement;

    private boolean mDefaultNsSet;

    /**
     * Constructor for the virtual root element
     */
    private DOMOutputElement(Node rootNode)
    {
        super();
        mRootNode = rootNode;
        mParent = null;
        mElement = null;
        mNsMapping = null;
        mNsMapShared = false;
        mDefaultNsURI = "";
        mRootNsContext = null;
        mDefaultNsSet = false;
    }

    private DOMOutputElement(DOMOutputElement parent, Element element, BijectiveNsMap ns)
    {
        super(parent, ns);
        mRootNode = null;
        mParent = parent;
        mElement = element;
        mNsMapping = ns;
        mNsMapShared = (ns != null);
        mDefaultNsURI = parent.mDefaultNsURI;
        mRootNsContext = parent.mRootNsContext;
        mDefaultNsSet = false;
    }

    /**
     * Method called to reuse a pooled instance.
     *
     * @returns Chained pooled instance that should now be head of the
     *   reuse chain
     */
    private void relink(DOMOutputElement parent, Element element)
    {
        super.relink(parent);
        mParent = parent;
        mElement = element;
        parent.appendNode(element);
        mDefaultNsSet = false;
    }

    public static DOMOutputElement createRoot(Node rootNode)
    {
        return new DOMOutputElement(rootNode);
    }
    
    /**
     * Simplest factory method, which gets called when a 1-argument
     * element output method is called. It is, then, assumed to
     * use the default namespace.
     * Will both create the child element and attach it to parent element,
     * or lacking own owner document.
     */
    protected DOMOutputElement createAndAttachChild(Element element)
    {
        if (mRootNode != null) {
            mRootNode.appendChild(element);
        } else {
            mElement.appendChild(element);
        }
        return createChild(element);
    }

    protected DOMOutputElement createChild(Element element)
    {
        return new DOMOutputElement(this, element, mNsMapping);
    }

    /**
     * @return New head of the recycle pool
     */
    protected DOMOutputElement reuseAsChild(DOMOutputElement parent, Element element)
    {
        DOMOutputElement poolHead = mParent;
        relink(parent, element);
        return poolHead;
    }

    /**
     * Method called to temporarily link this instance to a pool, to
     * allow reusing of instances with the same reader.
     */
    protected void addToPool(DOMOutputElement poolHead)
    {
        mParent = poolHead;
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API, accessors
    ///////////////////////////////////////////////////////////////////////
     */

    public DOMOutputElement getParent() {
        return mParent;
    }

    @Override
    public boolean isRoot() {
        // (Virtual) Root element has no parent...
        return (mParent == null);
    }
    
    /**
     * @return String presentation of the fully-qualified name, in
     *   "prefix:localName" format (no URI). Useful for error and
     *   debugging messages.
     */
    @Override
    public String getNameDesc() {
        if(mElement != null) {
            return mElement.getLocalName();
        }
        return "#error"; // unexpected case
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API, mutators
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public void setDefaultNsUri(String uri) {
        mDefaultNsURI = uri;
        mDefaultNsSet = true;
    }

    @Override
    protected void setRootNsContext(NamespaceContext ctxt)
    {
        mRootNsContext = ctxt;
        /* Let's also see if we have an active default ns mapping:
         * (provided it hasn't yet explicitly been set for this element)
         */
        if (!mDefaultNsSet) {
            String defURI = ctxt.getNamespaceURI("");
            if (defURI != null && defURI.length() > 0) {
                mDefaultNsURI = defURI;
            }
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API, DOM manipulation
    ///////////////////////////////////////////////////////////////////////
     */
    
    protected void appendNode(Node n)
    {
        if (mRootNode != null) {
            mRootNode.appendChild(n);
        } else {
            mElement.appendChild(n);
        }
    }

    protected void addAttribute(String pname, String value)
    {
        mElement.setAttribute(pname, value);
    }

    protected void addAttribute(String uri, String qname, String value)
    {
        mElement.setAttributeNS(uri, qname, value);
    }

    public void appendChild(Node n) {
        mElement.appendChild(n);
    }
}
