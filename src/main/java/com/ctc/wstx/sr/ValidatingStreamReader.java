/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in the file LICENSE which is
 * included with the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.sr;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.NotationDeclaration;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.validation.*;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.io.*;
import com.ctc.wstx.dtd.DTDId;
import com.ctc.wstx.dtd.DTDSubset;
import com.ctc.wstx.dtd.DTDValidatorBase;
import com.ctc.wstx.dtd.FullDTDReader;
import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.util.URLUtil;

/**
 * Implementation of {@link org.codehaus.stax2.XMLStreamReader2}
 * that builds on {@link TypedStreamReader} and adds full DTD-handling
 * including DTD validation
 *
 * @author Tatu Saloranta
 * @author Benson Margulies
 */
public class ValidatingStreamReader
    extends TypedStreamReader
{
    /*
    ///////////////////////////////////////////////////////////////////////
    // Constants for standard StAX properties:
    ///////////////////////////////////////////////////////////////////////
    */

    final static String STAX_PROP_ENTITIES = "javax.xml.stream.entities";

    final static String STAX_PROP_NOTATIONS = "javax.xml.stream.notations";

    /*
    ///////////////////////////////////////////////////////////////////////
    // Validation (DTD) information (entities, ...)
    ///////////////////////////////////////////////////////////////////////
     */

    // // // Note: some members that logically belong here, are actually
    // // // part of superclass

    /**
     * Combined DTD set, constructed from parsed internal and external
     * entities (which may have been set via override DTD functionality).
     */
    DTDValidationSchema mDTD = null;

    /**
     * Validating reader keeps of automatically created DTD-based
     * validator, since its handling may differ from that of application
     * managed validators.
     */
    XMLValidator mAutoDtdValidator = null;

    /**
     * Flag that indicates whether a DTD validator has been automatically
     * set (as per DOCTYPE declaration or override)
     */
    boolean mDtdValidatorSet = false;

    /**
     * Custom validation problem handler, if any.
     */
    protected ValidationProblemHandler mVldProbHandler = null;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Life-cycle (ctors)
    ///////////////////////////////////////////////////////////////////////
     */

    private ValidatingStreamReader(InputBootstrapper bs,
                                   BranchingReaderSource input, ReaderCreator owner,
                                   ReaderConfig cfg, InputElementStack elemStack,
                                   boolean forER)
        throws XMLStreamException
    {
        super(bs, input, owner, cfg, elemStack, forER);
    }

    /**
     * Factory method for constructing readers.
     *
     * @param owner "Owner" of this reader, factory that created the reader;
     *   needed for returning updated symbol table information after parsing.
     * @param input Input source used to read the XML document.
     * @param cfg Object that contains reader configuration info.
     * @param bs Bootstrapper to use, for reading xml declaration etc.
     * @param forER True if this reader is to be (configured to be) used by
     *   an event reader. Will cause some changes to default settings, as
     *   required by contracts Woodstox XMLEventReader implementation has
     *   (with respect to lazy parsing, short text segments etc)
     */
    public static ValidatingStreamReader createValidatingStreamReader
        (BranchingReaderSource input, ReaderCreator owner,
         ReaderConfig cfg, InputBootstrapper bs, boolean forER)
        throws XMLStreamException
    {
        ValidatingStreamReader sr = new ValidatingStreamReader
            (bs, input, owner, cfg, createElementStack(cfg), forER);
        return sr;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API, configuration
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public Object getProperty(String name)
    {
        // DTD-specific properties...
        if (name.equals(STAX_PROP_ENTITIES)) {
            safeEnsureFinishToken();
            if (mDTD == null || !(mDTD instanceof DTDSubset)) {
                return null;
            }
            List<EntityDecl> l = ((DTDSubset) mDTD).getGeneralEntityList();
            /* Let's make a copy, so that caller can not modify
             * DTD's internal list instance
             */
            return new ArrayList<EntityDecl>(l);
        }
        if (name.equals(STAX_PROP_NOTATIONS)) {
            safeEnsureFinishToken();
            if (mDTD == null || !(mDTD instanceof DTDSubset)) {
                return null;
            }
            /* Let's make a copy, so that caller can not modify
             * DTD's internal list instance
             */
            List<NotationDeclaration> l = ((DTDSubset) mDTD).getNotationList();
            return new ArrayList<NotationDeclaration>(l);
        }
        return super.getProperty(name);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // XMLStreamReader2 (StAX2) implementation
    ///////////////////////////////////////////////////////////////////////
     */

    // // // StAX2, per-reader configuration

    /*
    ///////////////////////////////////////////////////////////////////////
    // DTDInfo implementation (StAX 2)
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public Object getProcessedDTD() {
        return getProcessedDTDSchema();
    }

    @Override
    public DTDValidationSchema getProcessedDTDSchema() {
        DTDValidationSchema dtd = mConfig.getDTDOverride();
        if (dtd == null) {
            dtd = mDTD;
        }
        return mDTD;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Stax2 validation
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public XMLValidator validateAgainst(XMLValidationSchema schema)
        throws XMLStreamException
    {
        return mElementStack.validateAgainst(schema);
    }

    @Override
    public XMLValidator stopValidatingAgainst(XMLValidationSchema schema)
        throws XMLStreamException
    {
        return mElementStack.stopValidatingAgainst(schema);
    }

    @Override
    public XMLValidator stopValidatingAgainst(XMLValidator validator)
        throws XMLStreamException
    {
        return mElementStack.stopValidatingAgainst(validator);
    }

    @Override
    public ValidationProblemHandler setValidationProblemHandler(ValidationProblemHandler h)
    {
        ValidationProblemHandler oldH = mVldProbHandler;
        mVldProbHandler = h;
        return oldH;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Private methods, DOCTYPE handling
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * This method gets called to handle remainder of DOCTYPE declaration,
     * essentially the optional internal subset. Internal subset, if such
     * exists, is always read, but whether its contents are added to the
     * read buffer depend on passed-in argument.
     *<p>
     * NOTE: Since this method overrides the default implementation, make
     * sure you do NOT change the method signature.
     *
     * @param copyContents If true, will copy contents of the internal
     *   subset of DOCTYPE declaration
     *   in the text buffer (in addition to parsing it for actual use); if
     *   false, will only do parsing.
     */
    @Override
    protected void finishDTD(boolean copyContents)
        throws XMLStreamException
    {
        if (!hasConfigFlags(CFG_SUPPORT_DTD)) {
            super.finishDTD(copyContents);
            return;
        }

        /* We know there are no spaces, as this char was read and pushed
         * back earlier...
         */
        char c = getNextChar(SUFFIX_IN_DTD);
        DTDSubset intSubset = null;

        /* Do we have an internal subset? Note that we have earlier checked
         * that it has to be either '[' or closing '>'.
         */
        if (c == '[') {
            // Do we need to copy the contents of int. subset in the buffer?
            if (copyContents) {
                ((BranchingReaderSource) mInput).startBranch(mTextBuffer, mInputPtr, mNormalizeLFs);
            }

            try {
                intSubset = FullDTDReader.readInternalSubset(this, mInput, mConfig,
                                                             hasConfigFlags(CFG_VALIDATE_AGAINST_DTD),
                                                             mDocXmlVersion);
            } finally {
                /* Let's close branching in any and every case (may allow
                 * graceful recovery in error cases in future
                 */
                if (copyContents) {
                    /* Need to "push back" ']' got in the succesful case
                     * (that's -1 part below);
                     * in error case it'll just be whatever last char was.
                     */
                    ((BranchingReaderSource) mInput).endBranch(mInputPtr-1);
                }
            }

            // And then we need closing '>'
            c = getNextCharAfterWS(SUFFIX_IN_DTD_INTERNAL);
        }

        if (c != '>') {
            throwUnexpectedChar(c, "; expected '>' to finish DOCTYPE declaration.");
        }

        /* But, then, we also may need to read the external subset, if
         * one was defined:
         */
        /* 19-Sep-2004, TSa: That does not need to be done, however, if
         *    there's a DTD override set.
         */

        mDTD = mConfig.getDTDOverride();
        if (mDTD != null) {
            // We have earlier override that's already parsed
        } else { // Nope, no override
            DTDSubset extSubset = null;

            /* 05-Mar-2006, TSa: If standalone was specified as "yes", we
             *   should not rely on any external declarations, so shouldn't
             *   we really just skip the external subset?
             */
            /* Alas: SAX (Xerces) still tries to read it... should we
             * do the Right Thing, or follow the leader? For now, let's
             * just follow the wrong example.
             */

            //if (mDocStandalone != DOC_STANDALONE_YES) {
            if (true) {
                if (mDtdPublicId != null || mDtdSystemId != null) {
                    extSubset = findDtdExtSubset(mDtdPublicId, mDtdSystemId, intSubset);
                }
            }
            if (intSubset == null) {
                mDTD = extSubset;
            } else if (extSubset == null) {
                mDTD = intSubset;
            } else {
                mDTD = intSubset.combineWithExternalSubset(this, extSubset);
            }
        }

        
        if (mDTD == null) { // only if specifically overridden not to have any
            mGeneralEntities = null;
        } else {
            if (mDTD instanceof DTDSubset) {
                mGeneralEntities = ((DTDSubset) mDTD).getGeneralEntityMap();
            } else {
                /* Also, let's warn if using non-native DTD implementation,
                 * since entities and notations can not be accessed
                 */
                _reportProblem(mConfig.getXMLReporter(), ErrorConsts.WT_DT_DECL,
                               "Value to set for property '"+XMLInputFactory2.P_DTD_OVERRIDE
                               +"' not a native Woodstox DTD implementation (but "+mDTD.getClass()+"): can not access full entity or notation information", null);
            }
            /* 16-Jan-2006, TSa: Actually, we have both fully-validating mode,
             *   and non-validating-but-DTD-aware mode. In latter case, we'll
             *   still need to add a validator, but just to get type info
             *   and to add attribute default values if necessary.
             */
            mAutoDtdValidator = mDTD.createValidator(/*(ValidationContext)*/ mElementStack);
            mDtdValidatorSet = true; // so we won't get nags
            NsDefaultProvider nsDefs = null;
            if (mAutoDtdValidator instanceof DTDValidatorBase) {
                DTDValidatorBase dtdv = (DTDValidatorBase) mAutoDtdValidator;
                dtdv.setAttrValueNormalization(true);
                // Do we have any attribute defaults for 'xmlns' or 'xmlns:*'?
                if (dtdv.hasNsDefaults()) {
                    nsDefs = dtdv;
                }
            }
            mElementStack.setAutomaticDTDValidator(mAutoDtdValidator, nsDefs);
        }
    }

    /**
     * If there is an error handler established, call it.
     */
    @Override
    public void reportValidationProblem(XMLValidationProblem prob)
        throws XMLStreamException
    {
    	if (mVldProbHandler != null) {
            // Fix for [WSTX-209]
            mVldProbHandler.reportProblem(prob);
    	} else {
            super.reportValidationProblem(prob);
    	}
    }

    /**
     * Method called right before handling the root element, by the base
     * class. This allows for some initialization and checks to be done
     * (not including ones that need access to actual element name)
     */
    @Override
    protected void initValidation() throws XMLStreamException
    {
        if (hasConfigFlags(CFG_VALIDATE_AGAINST_DTD)
            && !mDtdValidatorSet) {
            /* It's ok to miss it, but it may not be what caller wants. Either
             * way, let's pass the info and continue
             */
            reportProblem(null, ErrorConsts.WT_DT_DECL, ErrorConsts.W_MISSING_DTD, null, null);
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Private methods, external subset access
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method called by <code>finishDTD</code>, to locate the specified
     * external DTD subset. Subset may be obtained from a cache, if cached
     * copy exists and is compatible; if not, it will be read from the
     * source identified by the public and/or system identifier passed.
     */
    private DTDSubset findDtdExtSubset(String pubId, String sysId,
                                       DTDSubset intSubset)
        throws XMLStreamException
    {
        boolean cache = hasConfigFlags(CFG_CACHE_DTDS);
        DTDId dtdId;
        try {
            dtdId = constructDtdId(pubId, sysId);
        } catch (IOException ioe) {
            throw constructFromIOE(ioe);
        }

        if (cache) {
            DTDSubset extSubset = findCachedSubset(dtdId, intSubset);
            if (extSubset != null) {
                return extSubset;
            }
        }

        // No useful cached copy? Need to read it then.
        /* For now, we do require system identifier; otherwise we don't
         * know how to resolve DTDs by public id. In future should
         * probably also have some simple catalog resolving facility?
         */
        if (sysId == null) {
            throwParseError("Can not resolve DTD with public id \"{0}\"; missing system identifier", mDtdPublicId, null);
        }
        WstxInputSource src = null;

        try {
            int xmlVersion = mDocXmlVersion;
            // 05-Feb-2006, TSa: If xmlVersion not explicitly known, defaults to 1.0
            if (xmlVersion == XmlConsts.XML_V_UNKNOWN) {
                xmlVersion = XmlConsts.XML_V_10;
            }
            /* null -> no explicit path context, use parent's
             * null -> not an entity expansion, no name.
             * Note, too, that we can NOT just pass mEntityResolver, since
             * that's the one used for general entities, whereas ext subset
             * should be resolved by the param entity resolver.
             */
            src = DefaultInputResolver.resolveEntity
                (mInput, null, null, pubId, sysId, mConfig.getDtdResolver(),
                 mConfig, xmlVersion);
        } catch (FileNotFoundException fex) {
            /* Let's catch and rethrow this just so we get more meaningful
             * description (with input source position etc)
             */
            throwParseError("(was {0}) {1}", fex.getClass().getName(), fex.getMessage());
        } catch (IOException ioe) {
            throwFromIOE(ioe);
        }

        DTDSubset extSubset = FullDTDReader.readExternalSubset(src, mConfig, intSubset,
                                                               hasConfigFlags(CFG_VALIDATE_AGAINST_DTD),
                                                               mDocXmlVersion);
        
        if (cache) {
            /* Ok; can be cached, but only if it does NOT refer to
             * parameter entities defined in the internal subset (if
             * it does, there's no easy/efficient to check if it could
             * be used later on, plus it's unlikely it could be)
             */
            if (extSubset.isCachable()) {
                mOwner.addCachedDTD(dtdId, extSubset);
            }
        }

        return extSubset;
    }

    private DTDSubset findCachedSubset(DTDId id, DTDSubset intSubset)
        throws XMLStreamException
    {
        DTDSubset extSubset = mOwner.findCachedDTD(id);
        /* Ok, now; can use the cached copy iff it does not refer to
         * any parameter entities internal subset (if one exists)
         * defines:
         */
        if (extSubset != null) {
            if (intSubset == null || extSubset.isReusableWith(intSubset)) {
                return extSubset;
            }
        }
        return null;
    }

    /**
     * Method called to resolve path to external DTD subset, given
     * system identifier.
     */
    private URI resolveExtSubsetPath(String systemId) throws IOException
    {
        // Do we have a context to use for resolving?
        URL ctxt = (mInput == null) ? null : mInput.getSource();

        /* Ok, either got a context or not; let's create the URL based on
         * the id, and optional context:
         */
        if (ctxt == null) {
            /* Call will try to figure out if system id has the protocol
             * in it; if not, create a relative file, if it does, try to
             * resolve it.
             */
            return URLUtil.uriFromSystemId(systemId);
        }
        URL url = URLUtil.urlFromSystemId(systemId, ctxt);
        try {
            return new URI(url.toExternalForm());
        } catch (URISyntaxException e) { // should never occur...
            throw new IOException("Failed to construct URI for external subset, URL = "+url.toExternalForm()+": "+e.getMessage());
        }
    }

    protected DTDId constructDtdId(String pubId, String sysId) throws IOException
    {
        /* Following settings will change what gets stored as DTD, so
         * they need to separate cached instances too:
         */
        int significantFlags = mConfigFlags &
            (CFG_NAMESPACE_AWARE
             /* Let's optimize non-validating case; DTD info we need
              * is less if so (no need to store content specs for one)...
              * plus, eventual functionality may be different too.
              */
             | CFG_VALIDATE_AGAINST_DTD
             /* Also, whether we support dtd++ or not may change construction
              * of settings... (currently does not, but could)
              */
             | CFG_SUPPORT_DTDPP
             /* Also, basic xml:id support does matter -- xml:id attribute
              * type is verified only if it's enabled
              */
             | CFG_XMLID_TYPING
             );
        URI sysRef = (sysId == null || sysId.length() == 0) ? null :
            resolveExtSubsetPath(sysId);

        /* 29-Mar-2006, TSa: Apparently public ids are not always very
         *   unique and/or can be mismatched with system ids, resulting
         *   in false matches if using public ids. As a result, by default
         *   Woodstox does NOT rely on public ids, when matching.
         */
        boolean usePublicId = (mConfigFlags & CFG_CACHE_DTDS_BY_PUBLIC_ID) != 0;
        if (usePublicId && pubId != null && pubId.length() > 0) {
            return DTDId.construct(pubId, sysRef, significantFlags, mXml11);
        }
        if (sysRef == null) {
            return null;
        }
        return DTDId.constructFromSystemId(sysRef, significantFlags, mXml11);
    }

    protected DTDId constructDtdId(URI sysId) throws IOException
    {
        int significantFlags = mConfigFlags &
            (CFG_NAMESPACE_AWARE
             /* Let's optimize non-validating case; DTD info we need
              * is less if so (no need to store content specs for one)
              */
             | CFG_VALIDATE_AGAINST_DTD
             /* Also, whether we support dtd++ or not may change construction
              * of settings... (currently does not, but could)
              */
             | CFG_SUPPORT_DTDPP
             );
        return DTDId.constructFromSystemId(sysId, significantFlags, mXml11);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Private methods, DTD validation support
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method called by lower-level parsing code when invalid content
     * (anything inside element with 'empty' content spec; text inside
     * non-mixed element etc) is found during basic scanning. Note
     * that actual DTD element structure problems are not reported
     * through this method.
     */
    @Override
    protected void reportInvalidContent(int evtType)
        throws XMLStreamException
    {
        switch (mVldContent) {
        case XMLValidator.CONTENT_ALLOW_NONE:
            reportValidationProblem(ErrorConsts.ERR_VLD_EMPTY,
                                    mElementStack.getTopElementDesc(),
                                    ErrorConsts.tokenTypeDesc(evtType));
            break;
        case XMLValidator.CONTENT_ALLOW_WS:
        case XMLValidator.CONTENT_ALLOW_WS_NONSTRICT: // should this ever occur?
            reportValidationProblem(ErrorConsts.ERR_VLD_NON_MIXED,
                                    mElementStack.getTopElementDesc(), null);
            break;
        case XMLValidator.CONTENT_ALLOW_VALIDATABLE_TEXT:
        case XMLValidator.CONTENT_ALLOW_ANY_TEXT:
            /* Not 100% sure if this should ever happen... depends on
             * interpretation of 'any' content model?
             */
            reportValidationProblem(ErrorConsts.ERR_VLD_ANY,
                                    mElementStack.getTopElementDesc(),
                                    ErrorConsts.tokenTypeDesc(evtType));
            break;
        default: // should never occur:
            throwParseError("Internal error: trying to report invalid content for "+evtType);
        }
    }
}
