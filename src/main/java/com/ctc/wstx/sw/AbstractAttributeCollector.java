package com.ctc.wstx.sw;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidator;

/**
 * Base class for attribute-collecting stubs that masquerade as an
 * {@link XMLValidator} so they can be passed to
 * {@link XmlWriter#writeTypedAttribute} and
 * {@link com.ctc.wstx.sr.AttributeCollector#writeAttribute}.
 * <p>
 * Only the two {@code validateAttribute} overloads are meaningful;
 * every other {@link XMLValidator} method throws
 * {@link UnsupportedOperationException} because it should never be
 * called on a collector.
 * <p>
 * Concrete subclasses are inner classes of {@link SimpleOutputElement}
 * (namespace-aware) and {@link NonNsStreamWriter} (non-namespace-aware).
 */
abstract class AbstractAttributeCollector extends XMLValidator {

    protected AbstractAttributeCollector() {
        super();
    }

    // -- validateAttribute left abstract (inherited from XMLValidator) --

    @Override
    public XMLValidationSchema getSchema() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void validateElementStart(String localName, String uri, String prefix) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int validateElementAndAttributes() throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int validateElementEnd(String localName, String uri, String prefix) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void validateText(String text, boolean lastTextSegment) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void validateText(char[] cbuf, int textStart, int textEnd, boolean lastTextSegment) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void validationCompleted(boolean eod) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAttributeType(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getIdAttrIndex() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getNotationAttrIndex() {
        throw new UnsupportedOperationException();
    }
}
