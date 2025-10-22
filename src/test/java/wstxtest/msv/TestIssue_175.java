package wstxtest.msv;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.validation.ValidationProblemHandler;
import org.codehaus.stax2.validation.XMLValidationException;
import org.codehaus.stax2.validation.XMLValidationProblem;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.junit.Assert;
import org.junit.Test;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.ctc.wstx.msv.W3CMultiSchemaFactory;
import com.ctc.wstx.stax.WstxInputFactory;

/**
 * Test Issue_175 - Added to test resolving XSD source files using the Entity Resolver
 *     
 *  When using various versions of XML that is build using imported XSD, in order to validate the XML correctly, the schema
 *  validation object must be built using the correct XSD.  Typically, the main XSD uses an generic import to the XSD built
 *  with.  In this scenario, the correct XSD must be resolved in order for the XML Validation to work correctly.
 *  
 *  In this test, two separate folders containing the versioned XSDs is used to demonstrate.  The developer could have placed 
 *  all the versioned XSDs in the same 'schema' folder.
 *  
 * @author Tim Martin
 */
public class TestIssue_175 
{	
	@Test
	/**
	 *  This tests in the case where the XSDs import statements files names/path do not match the file/path and naming 
	 *  used in the implementation.
	 */		
	public void testReadWithNoEntityResolver() 
	{
		try 
		{
			// Build Schema Validation object for Version 1
    		File fMainXsd1 = new File(getClass().getResource("schema/main/issue_175_main_v1.xsd").getFile());
    		final File fImportXsd1 = new File(getClass().getResource("schema/import/issue_175_import_v1.xsd").getFile());
    		
    		// Test to make sure we picked up the XSD
    		Assert.assertTrue(fMainXsd1.exists());
    		Assert.assertTrue(fImportXsd1.exists());
    		
        	final Map<String, Source> schemas_Ver1 = new HashMap<>();
        	schemas_Ver1.put("http://www.example.org/issue_175_main", new StreamSource(fMainXsd1));
        	schemas_Ver1.put("http://www.example.org/issue_175_import", new StreamSource(fImportXsd1));
        	
    		final W3CMultiSchemaFactory sf = new W3CMultiSchemaFactory();
        	sf.createSchema(null, schemas_Ver1);
        	
        	Assert.fail("Exception should have been thrown");
		}
		catch (XMLStreamException xse)
		{
			Assert.assertEquals("Failed to load schemas", xse.getMessage());
		}
		
		try
		{
   	  		// Build Schema Validation object for Version 2    		
   			final File fMainXsd2 = new File(getClass().getResource("schema/main/issue_175_main_v2.xsd").getFile());
   			final File fImportXsd2 = new File(getClass().getResource("schema/import/issue_175_import_v1.xsd").getFile());
   			    		
   			final Map<String, Source> schemas_Ver2 = new HashMap<>();
   	    	schemas_Ver2.put("http://www.example.org/issue_175_main", new StreamSource(fMainXsd2));
   	    	schemas_Ver2.put("http://www.example.org/issue_175_import", new StreamSource(fImportXsd2));
   			        	
   	    	final W3CMultiSchemaFactory sf = new W3CMultiSchemaFactory();
   			sf.createSchema(null, schemas_Ver2);
		}
		catch (XMLStreamException xse)
		{
			Assert.assertEquals("Failed to load schemas", xse.getMessage());
		}   			
   			
        try
        {
   			// Build Schema Validation object for Version 3    		
   			final File fMainXsd3 = new File(getClass().getResource("schema/main/issue_175_main_v3.xsd").getFile());
   			final File fImportXsd3 = new File(getClass().getResource("schema/import/issue_175_import_v2.xsd").getFile());
   			    		
   			final Map<String, Source> schemas_Ver3 = new HashMap<>();
   	    	schemas_Ver3.put("http://www.example.org/issue_175_main", new StreamSource(fMainXsd3));
   	    	schemas_Ver3.put("http://www.example.org/issue_175_import", new StreamSource(fImportXsd3));
   			        	
   	    	final W3CMultiSchemaFactory sf = new W3CMultiSchemaFactory();
   			sf.createSchema(null, schemas_Ver3);   			
		}
		catch (XMLStreamException xse)
		{
			Assert.assertEquals("Failed to load schemas", xse.getMessage());
		}	
	}		
	
	@Test
	/**
	 *  This tests in the case where the XML version matches the schema version, this validates the correct XSDs
	 *  are being picked up.
	 */		
	public void testValidationWithEntityResolver() 
	{
		try 
		{
			// Build Schema Validation object for Version 1
    		File fMainXsd1 = new File(getClass().getResource("schema/main/issue_175_main_v1.xsd").getFile());
    		final File fImportXsd1 = new File(getClass().getResource("schema/import/issue_175_import_v1.xsd").getFile());
    		
    		// Test to make sure we picked up the XSD
    		Assert.assertTrue(fMainXsd1.exists());
    		Assert.assertTrue(fImportXsd1.exists());
    		
        	final Map<String, Source> schemas_Ver1 = new HashMap<>();
        	schemas_Ver1.put("http://www.example.org/issue_175_main", new StreamSource(fMainXsd1));
        	schemas_Ver1.put("http://www.example.org/issue_175_import", new StreamSource(fImportXsd1));
        	
    		final W3CMultiSchemaFactory sf = new W3CMultiSchemaFactory();
        	sf.setEntityResolver(new EntityResolver() {
    			@Override
    			public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException 
    			{							
    	        	System.out.println("ResolveEntity() - Public id: [" + publicId + "], System id: [" + systemId + "]");
    	        	
    	        	InputSource is = null;
    	        	
    	        	File tmp = new File(URI.create(systemId).getPath());
    	        	
    	        	String fileName = tmp.getName();
    	        	if ("issue_175_import.xsd".equalsIgnoreCase(fileName))
    	        	{
    	        		tmp = fImportXsd1;
    	        	}
    	        	
    	        	try
    	        	{
    	        		is = new InputSource(new FileReader(tmp));
    	        		is.setSystemId(tmp.toURI().toString());
    	        	}
    	        	catch (IOException ex)
        			{
        				ex.printStackTrace();
        			}
    	        	
    	        	System.out.println("ResolveEntity() - System Id: [" + is.getSystemId() + "]");
    	        	
    				return is;
    			}				
    		});
        	
        	final XMLValidationSchema valSchemaVal_Ver1 = sf.createSchema(null, schemas_Ver1);
        	    		
   	  		// Build Schema Validation object for Version 2    		
   			final File fMainXsd2 = new File(getClass().getResource("schema/main/issue_175_main_v2.xsd").getFile());
   			final File fImportXsd2 = new File(getClass().getResource("schema/import/issue_175_import_v1.xsd").getFile());
   			    		
   			final Map<String, Source> schemas_Ver2 = new HashMap<>();
   	    	schemas_Ver2.put("http://www.example.org/issue_175_main", new StreamSource(fMainXsd2));
   	    	schemas_Ver2.put("http://www.example.org/issue_175_import", new StreamSource(fImportXsd2));
   			        	
   			final XMLValidationSchema valSchemaVal_Ver2 = sf.createSchema(null, schemas_Ver2, new EntityResolver() {
   				@Override
   				public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException 
   				{							
   		        	System.out.println("ResolveEntity() - Public id: [" + publicId + "], System id: [" + systemId + "]");
   		        	
   		        	InputSource is = null;
   		        	
   		        	File tmp = new File(URI.create(systemId).getPath());
   		        	
   		        	String fileName = tmp.getName();
   		        	if ("issue_175_import.xsd".equalsIgnoreCase(fileName))
   		        	{
   		        		tmp = fImportXsd2;
   		        	}
   		        	
   		        	try
   		        	{
   		        		is = new InputSource(new FileReader(tmp));
   		        		is.setSystemId(tmp.toURI().toString());
   		        	}
   		        	catch (IOException ex)
   	    			{
   	    				ex.printStackTrace();
   	    			}
   		        	
   		        	System.out.println("ResolveEntity() - System Id: [" + is.getSystemId() + "]");
   		        	
   					return is;
   				}				
   			});
        	
   			// Build Schema Validation object for Version 3    		
   			final File fMainXsd3 = new File(getClass().getResource("schema/main/issue_175_main_v3.xsd").getFile());
   			final File fImportXsd3 = new File(getClass().getResource("schema/import/issue_175_import_v2.xsd").getFile());
   			    		
   			final Map<String, Source> schemas_Ver3 = new HashMap<>();
   	    	schemas_Ver3.put("http://www.example.org/issue_175_main", new StreamSource(fMainXsd3));
   	    	schemas_Ver3.put("http://www.example.org/issue_175_import", new StreamSource(fImportXsd3));
   			        	
   			final XMLValidationSchema valSchemaVal_Ver3 = sf.createSchema(null, schemas_Ver3, new EntityResolver() {
   				@Override
   				public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException 
   				{							
   		        	System.out.println("ResolveEntity() - Public id: [" + publicId + "], System id: [" + systemId + "]");
   		        	
   		        	InputSource is = null;
   		        	
   		        	File tmp = new File(URI.create(systemId).getPath());
   		        	
   		        	String fileName = tmp.getName();
   		        	if ("issue_175_import.xsd".equalsIgnoreCase(fileName))
   		        	{
   		        		tmp = fImportXsd3;
   		        	}
   		        	
   		        	try
   		        	{
   		        		is = new InputSource(new FileReader(tmp));
   		        		is.setSystemId(tmp.toURI().toString());
   		        	}
   		        	catch (IOException ex)
   	    			{
   	    				ex.printStackTrace();
   	    			}
   		        	
   		        	System.out.println("ResolveEntity() - System Id: [" + is.getSystemId() + "]");
   		        	
   					return is;
   				}				
   			});   			
   			
        	try
        	{
        		// Xsd Validation - Version 1 XML data	        	
	    		final WstxInputFactory xf = new WstxInputFactory();
	    		xf.configureForXmlConformance();
					    		
	    		final XMLStreamReader2 xmlRdr = (XMLStreamReader2) xf.createXMLStreamReader(getClass().getResourceAsStream("Issue_175_ver1.xml"), "utf-8");
				xmlRdr.setValidationProblemHandler(new ValidationProblemHandler() 
				{
					@Override
					public void reportProblem(XMLValidationProblem arg0) throws XMLValidationException 
					{
						if (arg0.getSeverity() == XMLValidationProblem.SEVERITY_WARNING )
						{
							System.out.println("Validation Warning: " + arg0.getMessage());
						}
						else
						{
							System.out.println("Validation Error: " + arg0.getMessage());	
							throw arg0.toException();
						}
					}			
				});		
			
				// Validate
				xmlRdr.validateAgainst(valSchemaVal_Ver1);
	
				// Run thru each element, etc.
				while(xmlRdr.hasNext())	{ xmlRdr.next(); }
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
				Assert.fail("Unexpected error: " + ex.toString());
			}
				
	   		try 
			{
	   			// Xsd Validation - Version 2 XML data   					
	    		final WstxInputFactory xf = new WstxInputFactory();
	    		xf.configureForXmlConformance();
	   			
	    		final XMLStreamReader2 xmlRdr = (XMLStreamReader2) xf.createXMLStreamReader(getClass().getResourceAsStream("Issue_175_ver2.xml"), "utf-8");
				xmlRdr.setValidationProblemHandler(new ValidationProblemHandler() 
				{
					@Override
					public void reportProblem(XMLValidationProblem arg0) throws XMLValidationException 
					{
						if (arg0.getSeverity() == XMLValidationProblem.SEVERITY_WARNING )
						{
							System.out.println("Validation Warning: " + arg0.getMessage());
						}
						else
						{
							System.out.println("Validation Error: " + arg0.getMessage());	
							throw arg0.toException();
						}
					}			
				});		
			
				// Validate
				xmlRdr.validateAgainst(valSchemaVal_Ver2);
	
				// Run thru each element, etc.
				while(xmlRdr.hasNext())	{ xmlRdr.next(); }
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
				Assert.fail("Unexpected error: " + ex.toString());
			}				
		
	 		try 
			{
	   			// Xsd Validation - Version 3 XML data
	 			final WstxInputFactory xf = new WstxInputFactory();
	    		xf.configureForXmlConformance();
	    		
	    		XMLStreamReader2 xmlRdr = (XMLStreamReader2) xf.createXMLStreamReader(getClass().getResourceAsStream("Issue_175_ver3.xml"), "utf-8");
				xmlRdr.setValidationProblemHandler(new ValidationProblemHandler() 
				{
					@Override
					public void reportProblem(XMLValidationProblem arg0) throws XMLValidationException 
					{
						if (arg0.getSeverity() == XMLValidationProblem.SEVERITY_WARNING )
						{
							System.out.println("Validation Warning: " + arg0.getMessage());
						}
						else
						{
							System.out.println("Validation Error: " + arg0.getMessage());	
							throw arg0.toException();
						}
					}			
				});		
			
				// Validate
				xmlRdr.validateAgainst(valSchemaVal_Ver3);
	
				// Run thru each element, etc.
				while(xmlRdr.hasNext())	{ xmlRdr.next(); }
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
				Assert.fail("Unexpected error: " + ex.toString());
			}       	
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			Assert.fail("Unexpected error: " + ex.toString());
		}   	
	}	
	
	@Test
	/**
	 *  This tests in the case where the XML version does not match the schema version, this validates the correct XSDs 
	 *  are being picked up.
	 */	
	public void testValidationOfWrongXmlVersionWithEntityResolver() 
	{
		try 
		{
			// Build Schema Validation object for Version 1
    		File fMainXsd1 = new File(getClass().getResource("schema/main/issue_175_main_v1.xsd").getFile());
    		final File fImportXsd1 = new File(getClass().getResource("schema/import/issue_175_import_v1.xsd").getFile());
    		
    		// Test to make sure we picked up the XSD
    		Assert.assertTrue(fMainXsd1.exists());
    		Assert.assertTrue(fImportXsd1.exists());
    		
        	final Map<String, Source> schemas_Ver1 = new HashMap<>();
        	schemas_Ver1.put("http://www.example.org/issue_175_main", new StreamSource(fMainXsd1));
        	schemas_Ver1.put("http://www.example.org/issue_175_import", new StreamSource(fImportXsd1));
        	
    		final W3CMultiSchemaFactory sf = new W3CMultiSchemaFactory();
        	sf.setEntityResolver(new EntityResolver() {
    			@Override
    			public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException 
    			{							
    	        	System.out.println("ResolveEntity() - Public id: [" + publicId + "], System id: [" + systemId + "]");
    	        	
    	        	InputSource is = null;
    	        	
    	        	File tmp = new File(URI.create(systemId).getPath());
    	        	
    	        	String fileName = tmp.getName();
    	        	if ("issue_175_import.xsd".equalsIgnoreCase(fileName))
    	        	{
    	        		tmp = fImportXsd1;
    	        	}
    	        	
    	        	try
    	        	{
    	        		is = new InputSource(new FileReader(tmp));
    	        		is.setSystemId(tmp.toURI().toString());
    	        	}
    	        	catch (IOException ex)
        			{
        				ex.printStackTrace();
        			}
    	        	
    	        	System.out.println("ResolveEntity() - System Id: [" + is.getSystemId() + "]");
    	        	
    				return is;
    			}				
    		});
        	
        	final XMLValidationSchema valSchemaVal_Ver1 = sf.createSchema(null, schemas_Ver1);
        	    		
   	  		// Build Schema Validation object for Version 2    		
   			final File fMainXsd2 = new File(getClass().getResource("schema/main/issue_175_main_v2.xsd").getFile());
   			final File fImportXsd2 = new File(getClass().getResource("schema/import/issue_175_import_v1.xsd").getFile());
   			    		
   			final Map<String, Source> schemas_Ver2 = new HashMap<>();
   	    	schemas_Ver2.put("http://www.example.org/issue_175_main", new StreamSource(fMainXsd2));
   	    	schemas_Ver2.put("http://www.example.org/issue_175_import", new StreamSource(fImportXsd2));
   			        	
   			final XMLValidationSchema valSchemaVal_Ver2 = sf.createSchema(null, schemas_Ver2, new EntityResolver() {
   				@Override
   				public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException 
   				{							
   		        	System.out.println("ResolveEntity() - Public id: [" + publicId + "], System id: [" + systemId + "]");
   		        	
   		        	InputSource is = null;
   		        	
   		        	File tmp = new File(URI.create(systemId).getPath());
   		        	
   		        	String fileName = tmp.getName();
   		        	if ("issue_175_import.xsd".equalsIgnoreCase(fileName))
   		        	{
   		        		tmp = fImportXsd2;
   		        	}
   		        	
   		        	try
   		        	{
   		        		is = new InputSource(new FileReader(tmp));
   		        		is.setSystemId(tmp.toURI().toString());
   		        	}
   		        	catch (IOException ex)
   	    			{
   	    				ex.printStackTrace();
   	    			}
   		        	
   		        	System.out.println("ResolveEntity() - System Id: [" + is.getSystemId() + "]");
   		        	
   					return is;
   				}				
   			});
        	
   			// Build Schema Validation object for Version 3    		
   			final File fMainXsd3 = new File(getClass().getResource("schema/main/issue_175_main_v3.xsd").getFile());
   			final File fImportXsd3 = new File(getClass().getResource("schema/import/issue_175_import_v2.xsd").getFile());
   			    		
   			final Map<String, Source> schemas_Ver3 = new HashMap<>();
   	    	schemas_Ver3.put("http://www.example.org/issue_175_main", new StreamSource(fMainXsd3));
   	    	schemas_Ver3.put("http://www.example.org/issue_175_import", new StreamSource(fImportXsd3));
   			        	
   			final XMLValidationSchema valSchemaVal_Ver3 = sf.createSchema(null, schemas_Ver3, new EntityResolver() {
   				@Override
   				public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException 
   				{							
   		        	System.out.println("ResolveEntity() - Public id: [" + publicId + "], System id: [" + systemId + "]");
   		        	
   		        	InputSource is = null;
   		        	
   		        	File tmp = new File(URI.create(systemId).getPath());
   		        	
   		        	String fileName = tmp.getName();
   		        	if ("issue_175_import.xsd".equalsIgnoreCase(fileName))
   		        	{
   		        		tmp = fImportXsd3;
   		        	}
   		        	
   		        	try
   		        	{
   		        		is = new InputSource(new FileReader(tmp));
   		        		is.setSystemId(tmp.toURI().toString());
   		        	}
   		        	catch (IOException ex)
   	    			{
   	    				ex.printStackTrace();
   	    			}
   		        	
   		        	System.out.println("ResolveEntity() - System Id: [" + is.getSystemId() + "]");
   		        	
   					return is;
   				}				
   			});   			
   			
        	try
        	{
        		// Xsd Validation - Version 1 Schema, Version 2 XML data	        	
	    		final WstxInputFactory xf = new WstxInputFactory();
	    		xf.configureForXmlConformance();
					    		
	    		final XMLStreamReader2 xmlRdr = (XMLStreamReader2) xf.createXMLStreamReader(getClass().getResourceAsStream("Issue_175_ver2.xml"), "utf-8");
				xmlRdr.setValidationProblemHandler(new ValidationProblemHandler() 
				{
					@Override
					public void reportProblem(XMLValidationProblem arg0) throws XMLValidationException 
					{
						if (arg0.getSeverity() == XMLValidationProblem.SEVERITY_WARNING )
						{
							System.out.println("Validation Warning: " + arg0.getMessage());
						}
						else
						{
							System.out.println("Validation Error: " + arg0.getMessage());	
							throw arg0.toException();
						}
					}			
				});		
			
				// Validate
				xmlRdr.validateAgainst(valSchemaVal_Ver1);
	
				// Run thru each element, etc.
				while(xmlRdr.hasNext())	{ xmlRdr.next(); }
				
				Assert.fail("Error expected");
			}
			catch (Exception ex)
			{
				//ex.printStackTrace();
				Assert.assertEquals("ParseError at [row,col]:[12,3]\n"
						+ "Message: element \"Element5\" was found where no element may occur", ex.getMessage());
			}
				
	   		try 
			{
	   			// Xsd Validation - Version 2 Schema, Version 3 XML data   					
	    		final WstxInputFactory xf = new WstxInputFactory();
	    		xf.configureForXmlConformance();
	   			
	    		final XMLStreamReader2 xmlRdr = (XMLStreamReader2) xf.createXMLStreamReader(getClass().getResourceAsStream("Issue_175_ver3.xml"), "utf-8");
				xmlRdr.setValidationProblemHandler(new ValidationProblemHandler() 
				{
					@Override
					public void reportProblem(XMLValidationProblem arg0) throws XMLValidationException 
					{
						if (arg0.getSeverity() == XMLValidationProblem.SEVERITY_WARNING )
						{
							System.out.println("Validation Warning: " + arg0.getMessage());
						}
						else
						{
							System.out.println("Validation Error: " + arg0.getMessage());	
							throw arg0.toException();
						}
					}			
				});		
			
				// Validate
				xmlRdr.validateAgainst(valSchemaVal_Ver2);
	
				// Run thru each element, etc.
				while(xmlRdr.hasNext())	{ xmlRdr.next(); }
				
				Assert.fail("Error expected");
			}
			catch (Exception ex)
			{
				//ex.printStackTrace();
				Assert.assertEquals("ParseError at [row,col]:[6,2]\n"
						+ "Message: unexpected attribute \"Attrib4\"", ex.getMessage());
			}				
		
	 		try 
			{
	   			// Xsd Validation - Version 3 Schema, Version 1 XML data
	 			final WstxInputFactory xf = new WstxInputFactory();
	    		xf.configureForXmlConformance();
	    		
	    		XMLStreamReader2 xmlRdr = (XMLStreamReader2) xf.createXMLStreamReader(getClass().getResourceAsStream("Issue_175_ver1.xml"), "utf-8");
				xmlRdr.setValidationProblemHandler(new ValidationProblemHandler() 
				{
					@Override
					public void reportProblem(XMLValidationProblem arg0) throws XMLValidationException 
					{
						if (arg0.getSeverity() == XMLValidationProblem.SEVERITY_WARNING )
						{
							System.out.println("Validation Warning: " + arg0.getMessage());
						}
						else
						{
							System.out.println("Validation Error: " + arg0.getMessage());	
							throw arg0.toException();
						}
					}			
				});		
			
				// Validate
				xmlRdr.validateAgainst(valSchemaVal_Ver3);
	
				// Run thru each element, etc.
				while(xmlRdr.hasNext())	{ xmlRdr.next(); }
				
				Assert.fail("Error expected");
			}
			catch (Exception ex)
			{
				//ex.printStackTrace();
				Assert.assertEquals("ParseError at [row,col]:[6,2]\n"
						+ "Message: element \"Data\" is missing \"Attrib4\" attribute", ex.getMessage());
			}       	
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			Assert.fail("Unexpected error: " + ex.toString());
		}   	
	}			
}
