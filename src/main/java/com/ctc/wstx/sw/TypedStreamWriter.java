/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in file LICENSE, included with
 * the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.sw;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.typed.Base64Variant;
import org.codehaus.stax2.typed.Base64Variants;
import org.codehaus.stax2.ri.typed.AsciiValueEncoder;
import org.codehaus.stax2.ri.typed.ValueEncoderFactory;
import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.exc.WstxIOException;

/**
 * Intermediate base class that implements Typed Access API (Stax2 v3)
 * for all (repairing, non-repairing, non-namespace) native stream
 * writer implementations.
 */
public abstract class TypedStreamWriter
    extends BaseStreamWriter
{
    /**
     * When outputting using Typed Access API, we will need
     * encoders. If so, they will created by lazily-constructed
     * factory
     */
    protected ValueEncoderFactory mValueEncoderFactory;

    /*
    ////////////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////////////
     */

    protected TypedStreamWriter(XmlWriter xw, String enc, WriterConfig cfg)
    {
        super(xw, enc, cfg);
    }

    protected final ValueEncoderFactory valueEncoderFactory()
    {
        if (mValueEncoderFactory == null) {
            mValueEncoderFactory = new ValueEncoderFactory();
        }
        return mValueEncoderFactory;
    }

    /*
    /////////////////////////////////////////////////
    // TypedXMLStreamWriter2 implementation
    // (Typed Access API, Stax v3.0)
    /////////////////////////////////////////////////
     */

    // // // Typed element content write methods

    @Override
    public void writeBoolean(boolean value)
        throws XMLStreamException
    {
        writeTypedElement(valueEncoderFactory().getEncoder(value));
    }

    @Override
    public void writeInt(int value)
        throws XMLStreamException
    {
        writeTypedElement(valueEncoderFactory().getEncoder(value));
    }

    @Override
    public void writeLong(long value)
        throws XMLStreamException
    {
        writeTypedElement(valueEncoderFactory().getEncoder(value));
    }

    @Override
    public void writeFloat(float value)
        throws XMLStreamException
    {
        writeTypedElement(valueEncoderFactory().getEncoder(value));
    }

    @Override
    public void writeDouble(double value)
        throws XMLStreamException
    {
        writeTypedElement(valueEncoderFactory().getEncoder(value));

    }

    @Override
    public void writeInteger(BigInteger value)
        throws XMLStreamException
    {
        /* No really efficient method exposed by JDK, keep it simple
         * (esp. considering that length is actually not bound)
         */
        writeTypedElement(valueEncoderFactory().getScalarEncoder(value.toString()));
    }

    @Override
    public void writeDecimal(BigDecimal value)
        throws XMLStreamException
    {
        /* No really efficient method exposed by JDK, keep it simple
         * (esp. considering that length is actually not bound)
         */
        writeTypedElement(valueEncoderFactory().getScalarEncoder(value.toString()));
    }

    @Override
    public void writeQName(QName name)
        throws XMLStreamException
    {
        /* Can't use AsciiValueEncoder, since QNames can contain
         * non-ascii characters
         */
        writeCharacters(serializeQName(name));
    }

    @Override
    public final void writeIntArray(int[] value, int from, int length)
        throws XMLStreamException
    {
        writeTypedElement(valueEncoderFactory().getEncoder(value, from, length));
    }

    @Override
    public void writeLongArray(long[] value, int from, int length)
        throws XMLStreamException
    {
        writeTypedElement(valueEncoderFactory().getEncoder(value, from, length));
    }

    @Override
    public void writeFloatArray(float[] value, int from, int length)
        throws XMLStreamException
    {
        writeTypedElement(valueEncoderFactory().getEncoder(value, from, length));
    }

    @Override
    public void writeDoubleArray(double[] value, int from, int length)
        throws XMLStreamException
    {
        writeTypedElement(valueEncoderFactory().getEncoder(value, from, length));
    }

    @Override
    public void writeBinary(byte[] value, int from, int length)
        throws XMLStreamException
    {
        Base64Variant v = Base64Variants.getDefaultVariant();
        writeTypedElement(valueEncoderFactory().getEncoder(v, value, from, length));
    }

    @Override
    public void writeBinary(Base64Variant v, byte[] value, int from, int length)
        throws XMLStreamException
    {
        writeTypedElement(valueEncoderFactory().getEncoder(v, value, from, length));
    }

    protected final void writeTypedElement(AsciiValueEncoder enc)
        throws XMLStreamException
    {
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        }
        // How about well-formedness?
        if (mCheckStructure) {
            if (inPrologOrEpilog()) {
                reportNwfStructure(ErrorConsts.WERR_PROLOG_NONWS_TEXT);
            }
        }
        // Or validity?
        if (mVldContent <= XMLValidator.CONTENT_ALLOW_WS) {
            reportInvalidContent(CHARACTERS);
        }

        // So far so good: let's serialize
        try {
            XMLValidator vld = (mVldContent == XMLValidator.CONTENT_ALLOW_VALIDATABLE_TEXT) ?
                mValidator : null;
            if (vld == null) {
                mWriter.writeTypedElement(enc);
            } else {
                mWriter.writeTypedElement(enc, vld, getCopyBuffer());
            }
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    // // // Typed attribute value write methods

    @Override
    public void writeBooleanAttribute(String prefix, String nsURI, String localName, boolean value)
        throws XMLStreamException
    {
        writeTypedAttribute(prefix, nsURI, localName,
                            valueEncoderFactory().getEncoder(value));
    }

    @Override
    public void writeIntAttribute(String prefix, String nsURI, String localName, int value)
        throws XMLStreamException
    {
        writeTypedAttribute(prefix, nsURI, localName,
                              valueEncoderFactory().getEncoder(value));
    }

    @Override
    public void writeLongAttribute(String prefix, String nsURI, String localName, long value)
        throws XMLStreamException
    {
        writeTypedAttribute(prefix, nsURI, localName,
                              valueEncoderFactory().getEncoder(value));
    }

    @Override
    public void writeFloatAttribute(String prefix, String nsURI, String localName, float value)
        throws XMLStreamException
    {
        writeTypedAttribute(prefix, nsURI, localName,
                              valueEncoderFactory().getEncoder(value));
    }

    @Override
    public void writeDoubleAttribute(String prefix, String nsURI, String localName, double value)
        throws XMLStreamException
    {
        writeTypedAttribute(prefix, nsURI, localName,
                              valueEncoderFactory().getEncoder(value));
    }

    @Override
    public void writeIntegerAttribute(String prefix, String nsURI, String localName, BigInteger value)
        throws XMLStreamException
    {
        // not optimal, but has to do:
        writeTypedAttribute(prefix, nsURI, localName,
                              valueEncoderFactory().getScalarEncoder(value.toString()));
    }

    @Override
    public void writeDecimalAttribute(String prefix, String nsURI, String localName, BigDecimal value)
        throws XMLStreamException
    {
        // not optimal, but has to do:
        writeTypedAttribute(prefix, nsURI, localName,
                              valueEncoderFactory().getScalarEncoder(value.toString()));
    }

    @Override
    public void writeQNameAttribute(String prefix, String nsURI, String localName, QName name)
        throws XMLStreamException
    {
        /* Can't use AsciiValueEncoder, since QNames can contain
         * non-ascii characters
         */
        writeAttribute(prefix, nsURI, localName, serializeQName(name));
    }

    @Override
    public void writeIntArrayAttribute(String prefix, String nsURI, String localName, int[] value)
        throws XMLStreamException
    {
        writeTypedAttribute(prefix, nsURI, localName,
                              valueEncoderFactory().getEncoder(value, 0, value.length));
    }

    @Override
    public void writeLongArrayAttribute(String prefix, String nsURI, String localName, long[] value)
        throws XMLStreamException
    {
        writeTypedAttribute(prefix, nsURI, localName,
                            valueEncoderFactory().getEncoder(value, 0, value.length));
    }

    @Override
    public void writeFloatArrayAttribute(String prefix, String nsURI, String localName, float[] value)
        throws XMLStreamException
    {
        writeTypedAttribute(prefix, nsURI, localName,
                            valueEncoderFactory().getEncoder(value, 0, value.length));
    }

    @Override
    public void writeDoubleArrayAttribute(String prefix, String nsURI, String localName, double[] value)
        throws XMLStreamException
    {
        writeTypedAttribute(prefix, nsURI, localName,
                            valueEncoderFactory().getEncoder(value, 0, value.length));
    }

    @Override
    public void writeBinaryAttribute(String prefix, String nsURI, String localName, byte[] value)
        throws XMLStreamException
    {
        Base64Variant v = Base64Variants.getDefaultVariant();
        writeTypedAttribute(prefix, nsURI, localName,
                valueEncoderFactory().getEncoder(v, value, 0, value.length));
    }

    @Override
    public void writeBinaryAttribute(Base64Variant v, String prefix, String nsURI, String localName, byte[] value)
        throws XMLStreamException
    {
        writeTypedAttribute(prefix, nsURI, localName,
                valueEncoderFactory().getEncoder(v, value, 0, value.length));
    }

    /**
     * Method that will write attribute with value that is known not to
     * require additional escaping.
     */
    protected abstract void writeTypedAttribute(String prefix, String nsURI,
            String localName,
            AsciiValueEncoder enc)
        throws XMLStreamException;

    private String serializeQName(QName name)
        throws XMLStreamException
    {
        String vp = validateQNamePrefix(name);
        String local = name.getLocalPart();
        if (vp == null || vp.length() == 0) {
            return local;
        }

        // Not efficient... but should be ok
        return vp + ":" + local;
    }
}
