package com.tibco.bw.maven.plugin.test.helpers;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.tibco.bw.maven.plugin.test.dto.AssertionDTO;
import com.tibco.bw.maven.plugin.test.dto.ConditionLanguageDTO;
import com.tibco.bw.maven.plugin.test.dto.MockActivityDTO;
import com.tibco.bw.maven.plugin.test.dto.TestCaseDTO;
import com.tibco.bw.maven.plugin.test.dto.TestSetDTO;
import com.tibco.bw.maven.plugin.test.dto.TestSuiteDTO;
import com.tibco.bw.maven.plugin.utils.BWFileUtils;


public class TestFileParser {

	public static TestFileParser INSTANCE = new TestFileParser();
	
	boolean disableMocking = false;
	
	boolean disableAssertions = false;
	
	boolean showFailureDetails = false;
	

	private TestFileParser() {

	}

	
	@SuppressWarnings({ "unchecked" })
	public void collectAssertions(String contents , TestSuiteDTO suite , String baseDirectoryPath ) throws Exception,FileNotFoundException
	{
		String assertionMode = "Primitive";
		
		InputStream is = null;
		try {
			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			is = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
			Document document = builder.parse(is);
			NodeList nodeList = document.getDocumentElement().getChildNodes();

			String testCaseFile = document.getDocumentElement().getAttribute("location");
			for (int i = 0; i < nodeList.getLength(); i++) 
			{
				Node node = nodeList.item(i);
				if (node instanceof Element) 
				{
					Element el = (Element) node;
					if ("ProcessNode".equals(el.getNodeName())) 
					{
						
						suite.setShowFailureDetails(showFailureDetails);
						String processId = el.getAttributes().getNamedItem("Id").getNodeValue();

						String packageName = BWFileUtils.getFileNameWithoutExtn(processId);
						
						TestSetDTO testset = getProcessTestSet(processId, suite);
						testset.setPackageName(packageName);
						TestCaseDTO testcase = new TestCaseDTO();
						testcase.setTestCaseFile(testCaseFile);
						
						
						
						NodeList childNodes = el.getChildNodes();
						for (int j = 0; j < childNodes.getLength(); j++) 
						{
							Node cNode = childNodes.item(j);
							if (cNode instanceof Element) {
								Element cEl = (Element) cNode;
								if ("Assertion".equals(cEl.getNodeName()))
								{
									
									if(!disableAssertions){
											if(null != cEl.getAttributes().getNamedItem("goldOutputFromFile") && "true".equals(cEl.getAttributes().getNamedItem("goldOutputFromFile").getNodeValue())){
												assertionMode = "ActivityWithGoldFile";
											}
											else if(null != cEl.getAttributes().getNamedItem("assertionType") && "Activity".equals(cEl.getAttributes().getNamedItem("assertionType").getNodeValue())){
												assertionMode = "Activity";
											}
											else{
												assertionMode = "Primitive";
											}
									AssertionDTO ast = new AssertionDTO();
									ast.setProcessId(processId);
									ast.setAssertionMode(assertionMode);

									String location = cEl.getAttributes().getNamedItem("Id").getNodeValue();
									ast.setLocation(location);

									NodeList gChildNodes = cEl.getChildNodes();
									for (int k = 0; k < gChildNodes.getLength(); k++) {
										Node gcNode = gChildNodes.item(k);
										if (gcNode instanceof Element) {
											Element gcEl = (Element) gcNode;
											switch (gcEl.getNodeName()) {
											case "Lang":
												String conditionLanguage = gcEl.getLastChild().getTextContent();
												if ("urn:oasis:names:tc:wsbpel:2.0:sublang:xslt1.0".equals(conditionLanguage)) {
													ast.setConditionLanguage(ConditionLanguageDTO.XSLT10);
												} else {
													ast.setConditionLanguage(ConditionLanguageDTO.XSLT20);
												}
												break;
											case "Expression":
												String expression = gcEl.getLastChild().getTextContent();
												String replaceInputFile = null;
												String inputFile = null;
												if("ActivityWithGoldFile".equals(assertionMode)){
													inputFile = StringUtils.substringBetween(expression,"file:///", "')");
													File goldInputFile = new File(inputFile);
													if(!goldInputFile.isAbsolute()){
														BWTestConfig.INSTANCE.getLogger().debug("Provided Gold File path is relative "+inputFile);
														baseDirectoryPath =	baseDirectoryPath.replace("\\" , "/");
														replaceInputFile = baseDirectoryPath.concat("/"+inputFile);
														BWTestConfig.INSTANCE.getLogger().debug("Absolute File path "+replaceInputFile);
														expression = StringUtils.replace(expression, inputFile, replaceInputFile);
													}
													if(null != replaceInputFile){
														inputFile = replaceInputFile;
													}
												}
												if(showFailureDetails){
													setGoldData(assertionMode,expression,ast,inputFile);
												}
												ast.setExpression(expression);
												break;
											}
										}
									}
									testcase.getAssertionList().add(ast);
								 }
								}
								else if( "Operation".equals(cEl.getNodeName()))
								{
									NodeList gChildNodes = cEl.getChildNodes();
									for (int k = 0; k < gChildNodes.getLength(); k++) {
										Node gcNode = gChildNodes.item(k);
										if (gcNode instanceof Element) {
											Element e1 = (Element) gcNode;
											if ("resolvedInput".equals(e1.getNodeName()))
											{
												String inputValue = e1.getAttribute("inputValue");
												testcase.setXmlInput(inputValue);
												break;
											}
										}
									}
								}else if("MockActivity".equals(cEl.getNodeName()) || "MockFault".equals(cEl.getNodeName())){
									
									MockActivityDTO mockActivity = new MockActivityDTO();
									
									String location = cEl.getAttributes().getNamedItem("Id").getNodeValue();
									mockActivity.setLocation(location);
									
									NodeList gChildNodes = cEl.getChildNodes();
									for (int k = 0; k < gChildNodes.getLength(); k++) {
										Node gcNode = gChildNodes.item(k);
										if (gcNode instanceof Element) {
											Element e1 = (Element) gcNode;
											if ("MockOutputFilePath".equals(e1.getNodeName()))
											{
												String mockOutputFilePath = e1.getTextContent();
												File file = new File(mockOutputFilePath);
												if(!file.isAbsolute()){
													BWTestConfig.INSTANCE.getLogger().info("Provided Mock File path is relative "+file.getPath());
													mockOutputFilePath = baseDirectoryPath.concat("/"+mockOutputFilePath);
												}
												if(!disableMocking){
													boolean isValidFile = validateMockXMLFile(mockOutputFilePath);
													if(isValidFile){
														mockActivity.setmockOutputFilePath(mockOutputFilePath);
														testcase.setmockOutputFilePath(mockOutputFilePath);
														testcase.getMockActivityList().add(mockActivity);
														break;
													}
												}
											}
								      }
						       	}
						     }
						   }
						}
						if(disableMocking){
							BWTestConfig.INSTANCE.getLogger().info("-----------------------------------------------------------------------------------------------");
							BWTestConfig.INSTANCE.getLogger().info("## Mocking will be disabled for all Mocked Activities. DisableMocking :" + disableMocking +" ##");
							BWTestConfig.INSTANCE.getLogger().info("-----------------------------------------------------------------------------------------------");
						}
						
						if(disableAssertions){
							BWTestConfig.INSTANCE.getLogger().info("-----------------------------------------------------------------------------------------------");
							BWTestConfig.INSTANCE.getLogger().info("## All Assertions will be disabled. DisableAssertions :" + disableAssertions +" ##");
							BWTestConfig.INSTANCE.getLogger().info("-----------------------------------------------------------------------------------------------");
						}
						
						if( testcase.getAssertionList().isEmpty() && testcase.getMockActivityList().isEmpty() && !disableMocking && !disableAssertions)
						{
							BWTestConfig.INSTANCE.getLogger().info( "No assertions and Mock Activities found in the Test File : " + testcase.getTestCaseFile() + " . Skipping the running of file." );
							
						}
						else
						{
							testset.getTestCaseList().add(testcase);
						}
					}
				}
			}
			
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
			throw e;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (is != null) {
					is.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
	
	
		private void setGoldData(String assertionMode, String expression, AssertionDTO ast, String inputFile) {
		switch(assertionMode){
		case "Primitive":
				String goldValueWithElement = StringUtils.substringBetween(expression, "test=\"", "\">");
				String goldValue = StringUtils.substringAfter(goldValueWithElement, "=");
				if(goldValue.contains("'")){
					goldValue = StringUtils.substringBetween(goldValue, "'");
				}
				String elementNameString = StringUtils.substringBefore(goldValueWithElement, "=");
				String[] elementNameArray = StringUtils.split(elementNameString, "/");
				String elementName = elementNameArray[elementNameArray.length-1];
				String startElementTag = "<".concat(elementName).concat(">");
				String endElementTag = "</".concat(elementName).concat(">");
	
				ast.setGoldInput(startElementTag.concat(goldValue).concat(endElementTag));
				ast.setStartElementNameTag(startElementTag);
				ast.setEndElementNameTag(endElementTag);
				break;
				
		case "Activity":
			   String activityGoldValue = StringUtils.substringBetween(expression,"<xsl:variable name=\"AssertType\" as=\"item()*\"><Activity-Assertion>","</Activity-Assertion>");
			   activityGoldValue = StringUtils.replace(activityGoldValue, "<xsl:value-of select=\"&quot;" , "");
			   activityGoldValue = StringUtils.replace(activityGoldValue, "&quot;\"  />", "");
			   ast.setGoldInput(activityGoldValue);
			   break;
		
		case "ActivityWithGoldFile":
			  ast.setGoldInput(readXMLFile(inputFile));
			  break;
			   
		}
		
	}


		public HashSet<String> collectSkipInitActivities(String contents){
		InputStream is = null;
		HashSet<String> skipInitActivitiesSet = new HashSet<String>();
		
		try {
			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			is = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
			Document document = builder.parse(is);
			NodeList nodeList = document.getDocumentElement().getChildNodes();

			for (int i = 0; i < nodeList.getLength(); i++) 
			{
				Node node = nodeList.item(i);
				if (node instanceof Element) 
				{
					Element el = (Element) node;
					if ("ProcessNode".equals(el.getNodeName())) 
					{
						String processId = el.getAttributes().getNamedItem("Id").getNodeValue();
						String key = "-D"+processId+"=true";
						skipInitActivitiesSet.add(key);
						NodeList childNodes = el.getChildNodes();
						for (int j = 0; j < childNodes.getLength(); j++) 
						{
							Node cNode = childNodes.item(j);
							if (cNode instanceof Element) {
								Element cEl = (Element) cNode;
								if("MockActivity".equals(cEl.getNodeName())){
									MockActivityDTO mockActivity = new MockActivityDTO();
									String location = cEl.getAttributes().getNamedItem("Id").getNodeValue();
									mockActivity.setLocation(location);
									if(!disableMocking){
										String activityName = cEl.getAttributes().getNamedItem("Name").getNodeValue();
										if(null!=activityName){
											skipInitActivitiesSet.add("-D"+processId+activityName+"=true");
										}
									}
								}
						    }
					    }
				     }
			       }
			   }
				
		} catch (ParserConfigurationException |SAXException | IOException e) {
			e.printStackTrace();
		}   
		finally {
			try {
				if (is != null) {
					is.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return skipInitActivitiesSet;

	}
	
	private boolean validateMockXMLFile(String mockOutputFilePath) throws Exception {
		File mockOutputFile = new File(mockOutputFilePath);
		if(mockOutputFile.exists()){
			try {
				 String mockOutputString = readXMLFile(mockOutputFilePath);// TODO Set mockOutputString to TestCase variable mockOutput 
				 DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(mockOutputString)));
				 return true;
		    } catch (Exception e) {
		    	String errorMessage = "Provided XML file "+ mockOutputFilePath +" is not valid";
		    	BWTestConfig.INSTANCE.getLogger().error(errorMessage, e);
		    	throw e;
		    }
		}
		else{
			String errorMessage = "Provided XML file "+ mockOutputFilePath +" is not Present";
	    	BWTestConfig.INSTANCE.getLogger().error(errorMessage, new FileNotFoundException());
			throw new Exception();
		}
		
	}


	private String readXMLFile(String mockOutputFilePath) {
		String sCurrentLine;
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new FileReader(mockOutputFilePath))) {
			while ((sCurrentLine = br.readLine()) != null) {
				sb.append(sCurrentLine);
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return sb.toString();
	}

	public void setdisbleMocking(boolean disableMocking){
		this.disableMocking = disableMocking;
	}
	
	public void setdisbleAssertions(boolean disableAssertions){
		this.disableAssertions = disableAssertions;
	}
	
	public void setshowFailureDetails(boolean showFailureDetails){
		this.showFailureDetails = showFailureDetails;
	}
	
	@SuppressWarnings("unchecked")
	private TestSetDTO getProcessTestSet( String processName , TestSuiteDTO suite )
	{
		
		for( int i = 0 ; i < suite.getTestSetList().size() ; i++ )
		{
			if( suite.getTestSetList().get(i) != null && ((TestSetDTO)suite.getTestSetList().get(i)).getProcessName().equals( processName ) )
			{
				return (TestSetDTO)suite.getTestSetList().get(i);
			}
		}
		
		TestSetDTO set = new TestSetDTO();
		set.setProcessName(processName);
		suite.getTestSetList().add( set );
		
		return set;
	}

}
