package com.ctc.wstx.sr;

import com.ctc.wstx.dtd.DTDId;
import com.ctc.wstx.dtd.DTDSubset;
import com.ctc.wstx.util.SymbolTable;

/**
 * Interface that defines callbacks readers can use to access settings
 * of the input factory that created them, as well as update cached
 * data factory may store (shared symbol tables, cached DTDs etc).
 *<p>
 * Note that readers in general should only access the configuration info
 * when they are created (from constructor).
 */
public interface ReaderCreator
{
    /*
    ///////////////////////////////////////////////////////
    // Methods for accessing configuration info
    ///////////////////////////////////////////////////////
     */

    public DTDSubset findCachedDTD(DTDId id);

    /*
    ///////////////////////////////////////////////////////
    // Methods for updating information factory has
    ///////////////////////////////////////////////////////
     */
    
    public void updateSymbolTable(SymbolTable t);

    public void addCachedDTD(DTDId id, DTDSubset extSubset);
}
